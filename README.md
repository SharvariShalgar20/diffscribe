# pr-agent

Automatically generates PR titles and descriptions from a git diff, using a
local LLM via Ollama and Spring AI. No manual copy-pasting diffs into a
chatbot.

## Architecture

```
cli/       Java CLI, runs on your dev machine. Extracts + cleans the diff.
                |
                | HTTP POST { "diff": "..." }
                v
backend/   Spring Boot + Spring AI service. Builds the prompt, calls the LLM
           (local Ollama), returns structured { title, description, type }.
                |
                | (Phase 4, not built yet) GitHub API call
                v
           Opens or updates a PR with the generated title/description.
```

`cli/` and `backend/` are two independent Maven projects (not a multi-module
build) — they're separate deployables with separate lifecycles, mirroring
the local-agent/backend split used in the apiVault project.

### Package structure

**Backend** (`com.pragent.backend`), layered by responsibility:
```
config/      ChatClientConfig      - shared ChatClient bean
controller/  HealthController,     - thin HTTP layer only
             PrGenerationController
dto/         GenerateRequest,      - request/response shapes
             PrDescription
service/     PrDescriptionService  - prompt building + LLM call
```

**CLI** (`com.pragent.cli`), split by concern:
```
git/         GitClient             - shells out to the real git binary
diff/        DiffFilter,           - noise filtering, numstat parsing,
             NumstatParser,          size-bounded chunking
             DiffChunker
```

## Prerequisites

- **Java 21** (both projects target this — check with `java -version`)
- **Maven** — if `mvn` isn't on your PATH, use IntelliJ's Maven tool window
  (right-hand sidebar) instead: expand `Lifecycle` and double-click `package`
- **[Ollama](https://ollama.com)** installed and running locally
- **Git**, obviously

## Setting up the local model

The backend calls a locally-running Ollama model — nothing goes to an
external API, no API key needed.

1. Install Ollama from [ollama.com](https://ollama.com).
2. Pull a model. Which one depends on your available disk space:

   | Model | Approx. size | Notes |
   |---|---|---|
   | `qwen2.5-coder:7b` | ~4.7GB | Best quality, default if space allows |
   | `qwen2.5-coder:1.5b` | ~1GB | Current default in this project — fits tight disk space |
   | `qwen2.5-coder:0.5b` | ~400MB | Last resort, noticeably weaker |

   ```bash
   ollama pull qwen2.5-coder:1.5b
   ```

   Code-tuned models (the `qwen2.5-coder` family) read diffs and follow
   structured-output instructions more reliably than general-purpose chat
   models — avoid swapping in something like `llama3.2` for this use case.

3. Confirm it's there: `ollama list`
4. Ollama runs as a background service after install and normally listens
   on `http://localhost:11434` automatically. If a call fails with
   `Connection refused`, run `ollama serve` manually and check
   `curl http://localhost:11434/api/tags` responds.

**Known tradeoff**: this project currently defaults to the 1.5b model due to
local disk constraints. It works, but produces rougher output than 7b would
— e.g. it has already misclassified a new-feature diff as `type: "fix"`.
If you free up disk space, switch to 7b (see "Configuration" below). Longer
term, swapping to a cloud provider (OpenAI/Gemini/Claude) removes the local
storage constraint entirely — Spring AI's abstraction makes that a small
change (new starter dependency + config), not a rewrite.

## Configuration

`backend/src/main/resources/application.yml`:
```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:qwen2.5-coder:1.5b}
```

Both values are overridable via environment variables (`OLLAMA_BASE_URL`,
`OLLAMA_MODEL`) without touching this file — set up ahead of eventually
containerizing this, where Ollama won't be at `localhost`.

## Running the backend

```bash
cd backend
mvn spring-boot:run
```

Verify it's up:
```bash
curl http://localhost:8080/api/health
# -> OK

curl http://localhost:8080/api/test-llm
# -> confirms Spring AI <-> Ollama wiring is working end-to-end
```

## Running the CLI

Build:
```bash
cd cli
mvn clean package
```
This produces `target/pr-agent-cli.jar` (an all-in-one "shaded" jar — no
separate classpath setup needed to run it).

Extract a diff (run from anywhere inside a git repo):
```bash
java -jar target/pr-agent-cli.jar --base main
```

Useful flags:
| Flag | Purpose | Default |
|---|---|---|
| `--base` | Branch/ref to diff against | required, no default |
| `--repo-path` | Path to the git repo | `.` (current directory) |
| `--max-chunk-chars` | Max size per chunk before splitting | `8000` |

The CLI automatically:
- Resolves the actual git repo root first (so it works correctly even if
  invoked from a subdirectory)
- Filters out noise: binary files, `target/`, `*.class`, `*.jar`,
  `dependency-reduced-pom.xml`
- Splits large diffs into chunks on file boundaries (never mid-hunk)
- Prints progress/stats to **stderr** and the actual diff to **stdout** —
  so `> diff.txt` gives you a clean file with no extra noise mixed in

## Testing the backend manually (no CLI wiring yet — that's Phase 4/5)

The endpoint expects JSON: `POST /api/generate-pr-description` with body
`{ "diff": "<diff text>" }`.

**Via Postman**: pasting a multi-line diff directly into Postman's raw/JSON
body breaks JSON (real newlines aren't valid inside a JSON string, and
Postman won't auto-escape them). Instead, use a **Pre-request Script** to
build the body with `JSON.stringify`, which handles escaping automatically:

```javascript
const diff = `PASTE YOUR DIFF HERE — backticks support real multi-line text`;
pm.request.body.update({
  mode: 'raw',
  raw: JSON.stringify({ diff: diff }),
  options: { raw: { language: 'json' } }
});
```
Watch for a literal `\ No newline at end of file` line in some diffs — that
backslash-space isn't a valid JS escape inside a template literal and will
throw a syntax error. Escape it as `\\ No newline...` if you hit this.

**Via PowerShell**, avoiding manual JSON escaping entirely:
```powershell
cd cli
java -jar target\pr-agent-cli.jar --base main --max-chunk-chars 999999 > diff.txt

cd ..\backend
$diff = Get-Content -Raw ..\cli\diff.txt
$body = @{ diff = $diff } | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8080/api/generate-pr-description -Method Post -Body $body -ContentType "application/json"
```

## Troubleshooting

| Symptom | Cause |
|---|---|
| `mvn: command not found` | Maven isn't on PATH — use IntelliJ's Maven tool window instead |
| `Unable to access jarfile` | Build hasn't succeeded yet — check for `BUILD FAILURE` output |
| `Connection refused: ...11434` | Ollama isn't running, or the model isn't pulled |
| CLI prints file counts but no actual diff content | You're diffing against `HEAD` — files must be **committed**, not just staged, to show up |
| Diff comes back empty despite files changing | (Fixed) previously caused by running from a subdirectory — numstat paths are root-relative but pathspecs are cwd-relative; the CLI now always resolves to repo root first |

