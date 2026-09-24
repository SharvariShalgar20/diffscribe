# pr-agent

Automatically generates PR titles and descriptions from a git diff, using a
local LLM via Ollama and Spring AI, and writes them directly onto a real
GitHub PR — fully automatically on `git push`. No manual copy-pasting diffs
into a chatbot, no manual Postman calls.

**Status: Phases 0–5 complete. This is a working, end-to-end tool.**

## What it actually does today

1. You `git push` a branch with an open PR.
2. A `pre-push` git hook fires automatically.
3. The CLI extracts and cleans the diff (noise-filtered, chunked if large).
4. It calls the local backend, which generates a title/description via a
   local Ollama model.
5. The backend finds the matching open PR on GitHub and updates its
   title/description via the GitHub API.
6. Your push completes normally either way — if any of the above fails
   (no PR yet, backend down, etc.), it's logged and ignored, never blocks
   the actual push.

## Architecture

```
cli/       Java CLI. Extracts + cleans the diff, calls the backend.
                |
                | HTTP POST { repoOwner, repoName, branch, diffChunks }
                v
backend/   Spring Boot + Spring AI. Generates via local Ollama, finds the
           open PR for that branch, PATCHes its title/body via GitHub API.
                |
                v
           The real PR on GitHub gets updated.

.githooks/pre-push   Fires the CLI automatically on every git push.
```

`cli/` and `backend/` are two independent Maven projects (not a
multi-module build) — separate deployables, mirroring the local-agent/
backend split used in the apiVault project.

### Package structure

**Backend** (`com.pragent.backend`):
```
config/      ChatClientConfig, GitHubProperties
controller/  HealthController, PrGenerationController, PrUpdateController
dto/         GenerateRequest, PrDescription, ChangeType,
             UpdatePrRequest, UpdatePrResponse
service/     PrDescriptionService (prompts + multi-chunk map-reduce),
             PrUpdateService (orchestrates generate -> GitHub update)
github/      GitHubClient (find open PR by branch, update it)
```

**CLI** (`com.pragent.cli`):
```
git/         GitClient (shells out to git; resolves repo root, current
             branch, and owner/repo from the 'origin' remote)
diff/        DiffFilter, NumstatParser, DiffChunker
http/        BackendClient, UpdatePrRequest, UpdatePrResponse
             (talks to the backend using Jackson, not hand-built JSON)
```

## Prerequisites

- **Java 21**, **Maven** (or use IntelliJ's Maven tool window if `mvn`
  isn't on PATH)
- **[Ollama](https://ollama.com)**, running locally with a model pulled
- **Git**, with a GitHub remote
- **A GitHub Personal Access Token** (`repo` scope)

## One-time setup

```bash
ollama pull qwen2.5-coder:1.5b
```
```powershell
$env:GITHUB_TOKEN="ghp_..."   # or set permanently via Windows Environment Variables
```
```bash
cd backend && mvn spring-boot:run &   # keep running in the background
cd cli && mvn clean package
```
```bash
git config core.hooksPath .githooks
chmod +x .githooks/pre-push          # via Git Bash
```

After this, automation "just works" on every push, as long as the backend
is running locally.

## Manual usage (without the hook)

```bash
java -jar target/pr-agent-cli.jar --base main
```
Auto-detects owner/repo (from `git remote`) and branch (from `git
rev-parse`), extracts the diff, calls the backend, updates the matching
open PR, and prints the result.

`--dry-run` skips the backend call entirely and just prints the filtered
diff to stdout — useful for debugging diff extraction without touching
the network or GitHub.

| Flag | Purpose | Default |
|---|---|---|
| `--base` | Branch/ref to diff against | required |
| `--repo-path` | Path to the git repo | `.` |
| `--max-chunk-chars` | Max size per chunk | `8000` |
| `--backend-url` | Backend base URL | `http://localhost:8080` |
| `--dry-run` | Print diff only, no backend call | off |

## API endpoints (for manual testing / debugging)

**`POST /api/generate-pr-description`** — generate only, no side effects.
`{ "diffChunks": [...] }` → `{ title, description, type }`

**`POST /api/pr/update`** — generate and write to a real PR.
`{ repoOwner, repoName, branch, diffChunks }` → `{ generated, prNumber, prUrl }`.
`404` if no open PR matches the branch — this endpoint updates existing
PRs, it does not create them.

## Configuration

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
Real secrets live only in environment variables, never in this file.

## Known limitations (accepted baseline)

- **`type` classification and the "Why" section are unreliable on nuanced
  diffs** (refactors, validation additions) on `qwen2.5-coder:1.5b`, despite
  several rounds of prompt refinement. Straightforward feature additions
  classify correctly; ambiguous ones don't always.
- **The three-heading description template isn't 100% reliable**, even with
  a shared worked example — confirmed on a real multi-chunk PR update.
  Probabilistic model behavior, not a fixable prompt bug at this model size.
- **The pre-push hook assumes `cli/` lives in the same repo being pushed**
  (true for this dogfooding setup). Installing the hook into a different
  repo would need the jar path made configurable — planned for Phase 6.
- **First push of a new branch always "fails" gracefully** (no PR exists
  yet to update) — expected, not a bug; push a second time after creating
  the PR on GitHub.

If quality becomes a blocker: bump to `qwen2.5-coder:7b` if disk space
allows, or switch to a cloud provider (OpenAI/Gemini/Claude) — Spring AI's
abstraction makes that a config change, not a rewrite.
