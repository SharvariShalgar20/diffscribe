# pr-agent

Automatically generates PR titles and descriptions from a git diff, using a
local LLM via Ollama and Spring AI. No manual copy-pasting diffs into a
chatbot.

## Architecture

```
cli/       Java CLI, runs on your dev machine. Extracts + cleans the diff.
                |
                | HTTP POST { "diffChunks": ["...", "..."] }
                v
backend/   Spring Boot + Spring AI service. Summarizes/synthesizes via LLM
           (local Ollama), returns structured { title, description, type }.
                |
                | (Phase 4, next) GitHub API call
                v
           Opens or updates a PR with the generated title/description.
```

`cli/` and `backend/` are two independent Maven projects (not a multi-module
build) — separate deployables, mirroring the local-agent/backend split used
in the apiVault project.

### Package structure

**Backend** (`com.pragent.backend`), layered by responsibility:
```
config/      ChatClientConfig       - shared ChatClient bean
controller/  HealthController,      - thin HTTP layer only
             PrGenerationController
dto/         GenerateRequest,       - request/response shapes
             PrDescription,
             ChangeType
service/     PrDescriptionService   - prompt building + LLM calls,
                                       including multi-chunk map-reduce
```

**CLI** (`com.pragent.cli`), split by concern:
```
git/         GitClient              - shells out to the real git binary
diff/        DiffFilter,            - noise filtering, numstat parsing,
             NumstatParser,           size-bounded chunking
             DiffChunker
```

## Prerequisites

- **Java 21** (`java -version`)
- **Maven** — if `mvn` isn't on PATH, use IntelliJ's Maven tool window
  (right sidebar → Lifecycle → double-click `package`)
- **[Ollama](https://ollama.com)** installed and running locally
- **Git**

## Setting up the local model

The backend calls a locally-running Ollama model — nothing goes to an
external API, no API key needed.

1. Install Ollama from [ollama.com](https://ollama.com).
2. Pull a model based on available disk space:

   | Model | Approx. size | Notes |
   |---|---|---|
   | `qwen2.5-coder:7b` | ~4.7GB | Best quality if space allows |
   | `qwen2.5-coder:1.5b` | ~1GB | **Current default** — fits tight disk space |
   | `qwen2.5-coder:0.5b` | ~400MB | Last resort, noticeably weaker |

   ```bash
   ollama pull qwen2.5-coder:1.5b
   ```

3. Confirm: `ollama list`
4. If a call fails with `Connection refused`, run `ollama serve` manually
   and check `curl http://localhost:11434/api/tags` responds.

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

Both overridable via env vars without touching this file — set up ahead of
eventually containerizing this.

## Running the backend

```bash
cd backend
mvn spring-boot:run
```

```bash
curl http://localhost:8080/api/health     # -> OK
curl http://localhost:8080/api/test-llm   # -> confirms Spring AI <-> Ollama wiring
```

## Running the CLI

```bash
cd cli
mvn clean package
java -jar target/pr-agent-cli.jar --base main
```

| Flag | Purpose | Default |
|---|---|---|
| `--base` | Branch/ref to diff against | required, no default |
| `--repo-path` | Path to the git repo | `.` |
| `--max-chunk-chars` | Max size per chunk before splitting | `8000` |

The CLI resolves the actual repo top-level first (works correctly even from
a subdirectory), filters noise (binary files, `target/`, `*.class`, `*.jar`,
`dependency-reduced-pom.xml`), and splits large diffs into chunks on file
boundaries. Progress/stats print to **stderr**, the diff itself to
**stdout** — `> diff.txt` gives a clean file.

**Remember**: `git diff --base <branch>` only sees **committed** changes on
your current branch relative to that base — staged-but-uncommitted changes
won't show up, and running it while sitting on the base branch itself
naturally shows nothing.

## API contract

`POST /api/generate-pr-description`
```json
{ "diffChunks": ["<diff or chunk 1>", "<diff or chunk 2>", "..."] }
```
A single-element array is a normal single diff. Multiple elements trigger
map-reduce: each chunk is summarized independently, then synthesized into
one coherent title/description — this avoids concatenating chunks back into
one oversized prompt, which would defeat the point of chunking in the first
place.

Response:
```json
{
  "title": "feat: short conventional-commit-style summary",
  "description": "## What changed\n...\n\n## Why\n...\n\n## How to test\n...",
  "type": "feat"
}
```

## Testing the backend manually (no CLI wiring yet — Phase 4/5)

**Via Postman** — pasting a multi-line diff directly into raw/JSON body
breaks JSON (real newlines aren't valid inside a JSON string). Use a
**Pre-request Script** instead:

```javascript
const rawOutput = `PASTE FULL CLI OUTPUT HERE, INCLUDING ===== CHUNK n/n ===== MARKERS IF PRESENT`;

const parts = rawOutput
  .split(/===== CHUNK \d+\/\d+ =====\n?/)
  .filter(p => p.trim().length > 0);

pm.request.body.update({
  mode: 'raw',
  raw: JSON.stringify({ diffChunks: parts.length > 0 ? parts : [rawOutput] }),
  options: { raw: { language: 'json' } }
});
```
This works whether the CLI output had chunk markers or not — if there were
none, the whole output becomes a single-element array.

Watch for a literal `\ No newline at end of file` line in some diffs — a
backslash-space isn't a valid JS escape inside a template literal. Escape
it as `\\ No newline...` if you hit a script syntax error.

**Via PowerShell** (single-chunk testing, avoids manual JSON escaping):
```powershell
cd cli
java -jar target\pr-agent-cli.jar --base main --max-chunk-chars 999999 > diff.txt

cd ..\backend
$diff = Get-Content -Raw ..\cli\diff.txt
$body = @{ diffChunks = @($diff) } | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8080/api/generate-pr-description -Method Post -Body $body -ContentType "application/json"
```

## Known limitations (accepted baseline, Phase 3)

The `qwen2.5-coder:1.5b` model — chosen for disk-space reasons, see above —
has a real, tested quality ceiling for this task:

- **`type` classification is unreliable for nuanced cases.** It correctly
  distinguishes "adds new files" as `feat`, but has repeatedly mislabeled
  refactors and validation-adding changes as `fix` even with explicit rules
  and contrastive examples in the prompt. Straightforward new-feature diffs
  classify correctly; ambiguous ones (refactors, validation logic) often don't.
- **The "Why" section tends to invent plausible-sounding but ungrounded
  motivation** (e.g. "improves integration with other systems") instead of
  admitting the reason isn't evident from the diff, despite explicit
  instructions and negative examples against this. This persisted across
  multiple rounds of prompt refinement.
- **Structural compliance (the three-heading template) is reliable** once
  a concrete worked example is included in the prompt for whichever code
  path is generating output — this was learned the hard way when the
  synthesis (multi-chunk) path lost the template because the example
  constant wasn't shared with it during a refactor.

Decision: accepted as Phase 3's baseline rather than continuing to chase
prompt-engineering fixes on a 1.5b model. If quality becomes a blocker,
options are (a) `qwen2.5-coder:7b` if disk space frees up, or (b) a cloud
provider (OpenAI/Gemini/Claude) — Spring AI's abstraction makes that a
config change, not a rewrite.
