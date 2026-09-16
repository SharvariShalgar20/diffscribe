# pr-agent

Automatically generates PR titles and descriptions from a git diff, using a
local LLM via Ollama and Spring AI, and can write them directly onto a real
GitHub PR. No manual copy-pasting diffs into a chatbot.

## Architecture

```
cli/       Java CLI, runs on your dev machine. Extracts + cleans the diff.
                |
                | HTTP POST { "diffChunks": [...] }              (generate only)
                | HTTP POST { repoOwner, repoName, branch,        (generate + update PR)
                |             diffChunks }
                v
backend/   Spring Boot + Spring AI service. Generates via local Ollama model,
           then (for /api/pr/update) finds the open PR for that branch and
           PATCHes its title/body via the GitHub REST API.
                |
                v
           The real PR on GitHub gets its title/description updated.
```

`cli/` and `backend/` are two independent Maven projects (not a multi-module
build) — separate deployables, mirroring the local-agent/backend split used
in the apiVault project. The CLI does not yet call the backend automatically
— that wiring is Phase 5. Today, testing is manual (Postman/PowerShell).

### Package structure

**Backend** (`com.pragent.backend`), layered by responsibility:
```
config/      ChatClientConfig,      - shared ChatClient bean, GitHub token
             GitHubProperties         binding (from GITHUB_TOKEN env var)
controller/  HealthController,      - thin HTTP layer only
             PrGenerationController,
             PrUpdateController
dto/         GenerateRequest,       - request/response shapes
             PrDescription,
             ChangeType,
             UpdatePrRequest,
             UpdatePrResponse
service/     PrDescriptionService,  - prompt building + LLM calls
             PrUpdateService          (incl. multi-chunk map-reduce);
                                       orchestrates generate -> GitHub update
github/      GitHubClient           - thin GitHub REST API wrapper
                                       (find open PR by branch, update it)
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
- **A GitHub Personal Access Token** (only needed for `/api/pr/update` —
  see "GitHub setup" below)

## Setting up the local model

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

## GitHub setup (for `/api/pr/update`)

1. GitHub → Settings → Developer settings → Personal access tokens →
   Tokens (classic) → Generate new token → scope: **`repo`**.
2. **Set it as an environment variable — never paste it into `application.yml`
   or any file.** `application.yml` only ever contains the placeholder:
   ```yaml
   github:
     token: ${GITHUB_TOKEN:}
   ```
   PowerShell (session-only): `$env:GITHUB_TOKEN="ghp_..."`
   Permanent: Windows → Environment Variables → User variables → New →
   `GITHUB_TOKEN`. Requires fully restarting IntelliJ (not just the run) to
   pick up a newly-set system env var.
3. **`/api/pr/update` only works on a PR that already exists.** It finds an
   *open* PR matching the given branch and updates it — it does not create
   one. If you're on GitHub's compare/create screen (URL contains
   `/compare/...`), the PR doesn't exist yet; click "Create pull request"
   first, then re-run.

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
github:
  token: ${GITHUB_TOKEN:}
```
All overridable via env vars without touching this file — set up ahead of
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

Resolves the actual repo top-level first (works from a subdirectory),
filters noise (binary files, `target/`, `*.class`, `*.jar`,
`dependency-reduced-pom.xml`), splits large diffs into chunks on file
boundaries. Stats print to **stderr**, the diff to **stdout**.

**Remember**: only sees **committed** changes on your current branch
relative to the base — staged-but-uncommitted changes won't show up, and
running it while sitting on the base branch itself shows nothing.

## API endpoints

### `POST /api/generate-pr-description` — generate only, no side effects
```json
{ "diffChunks": ["<diff or chunk 1>", "<diff or chunk 2>", "..."] }
```
A single-element array is a normal single diff. Multiple elements trigger
map-reduce: each chunk summarized independently, then synthesized into one
coherent result — avoids concatenating everything back into one oversized
prompt, which would defeat the point of chunking.

Response:
```json
{
  "title": "feat: short conventional-commit-style summary",
  "description": "## What changed\n...\n\n## Why\n...\n\n## How to test\n...",
  "type": "feat"
}
```

### `POST /api/pr/update` — generate AND write to a real GitHub PR
```json
{
  "repoOwner": "your-username",
  "repoName": "your-repo",
  "branch": "your-branch-name",
  "diffChunks": ["...", "..."]
}
```
Internally: generates via `PrDescriptionService`, finds the open PR for
`branch` via the GitHub API, PATCHes its title/body. Returns:
```json
{
  "generated": { "title": "...", "description": "...", "type": "feat" },
  "prNumber": 8,
  "prUrl": "https://github.com/owner/repo/pull/8"
}
```
`404` if no open PR matches the branch. Confirmed working end-to-end against
a real PR (see Known limitations for a caveat on multi-chunk diffs).

## Testing manually (no CLI wiring yet — Phase 5)

**Via Postman** — pasting a multi-line diff directly into raw/JSON body
breaks JSON (real newlines aren't valid inside a JSON string). Use a
**Pre-request Script**:

```javascript
const rawOutput = `PASTE FULL CLI OUTPUT HERE, INCLUDING ===== CHUNK n/n ===== MARKERS IF PRESENT`;

const parts = rawOutput
  .split(/===== CHUNK \d+\/\d+ =====\n?/)
  .filter(p => p.trim().length > 0);

pm.request.body.update({
  mode: 'raw',
  raw: JSON.stringify({
    repoOwner: "your-username",   // omit repoOwner/repoName/branch for /api/generate-pr-description
    repoName: "your-repo",
    branch: "your-branch-name",
    diffChunks: parts.length > 0 ? parts : [rawOutput]
  }),
  options: { raw: { language: 'json' } }
});
```

**Two escaping gotchas found via testing, both need manual fixing if hit:**
- A literal `\ No newline at end of file` line in a diff breaks the script
  (`\ ` isn't a valid JS escape) — fix by escaping to `\\ No newline...`.
- Any literal `${...}` sequence in the diff (Spring property placeholders,
  JS/TS template literals) gets misread as JS template interpolation and
  throws `ReferenceError: <name> is not defined` — fix by escaping to
  `\${...}`, or better, reword source comments to avoid writing `${...}`
  literally where possible.

**Via PowerShell** (single-chunk, avoids manual JSON escaping):
```powershell
cd cli
java -jar target\pr-agent-cli.jar --base main --max-chunk-chars 999999 > diff.txt

cd ..\backend
$diff = Get-Content -Raw ..\cli\diff.txt
$body = @{ diffChunks = @($diff) } | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8080/api/generate-pr-description -Method Post -Body $body -ContentType "application/json"
```

## Known limitations (accepted baseline)

`qwen2.5-coder:1.5b` — chosen for disk-space reasons — has a real, tested
quality ceiling for this task:

- **`type` classification is unreliable for nuanced cases.** Correctly
  distinguishes "adds new files" as `feat`, but has repeatedly mislabeled
  refactors and validation-adding changes as `fix` despite explicit rules
  and contrastive examples in the prompt.
- **The "Why" section sometimes invents plausible-sounding but ungrounded
  motivation** instead of admitting it's not evident from the diff, despite
  explicit instructions against this.
- **The three-heading template (`## What changed` / `## Why` / `## How to
  test`) is not 100% reliable, even with a worked example in the prompt.**
  Confirmed on the real Phase 4 test (PR #8, 5 commits / 9 files, routed
  through multi-chunk synthesis): the model dropped the template and wrote
  a single paragraph despite the fix that shares the worked example between
  the single-diff and synthesis prompts. This looks like inherent
  small-model unreliability on larger/more complex synthesized input, not a
  prompt-wording gap — logged as a known limitation rather than chased
  further.

Decision: accepted as this model's baseline rather than continuing
prompt-engineering iteration. If quality becomes a blocker: (a)
`qwen2.5-coder:7b` if disk space frees up, or (b) a cloud provider
(OpenAI/Gemini/Claude) — Spring AI's abstraction makes that a config
change, not a rewrite.
