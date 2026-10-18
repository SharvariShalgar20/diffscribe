# diffscribe (pr-agent)

Writes your pull request title and description for you. On `git push`, a local CLI extracts the diff, a Spring AI backend generates a title and description with a **local** Ollama model, and the result is written onto your open GitHub PR. No diffs are pasted into a chatbot, and no code leaves your machine except the PR text sent to GitHub.

## How it works

```
git push
   |
   v
pre-push hook (global, opt-in per repo)
   |
   v
cli/   Extracts and filters the diff, detects owner/repo/branch from git,
       POSTs to the backend.
   |    HTTP POST /api/pr/update { repoOwner, repoName, branch, diffChunks }
   v
pr-agent-backend/   Spring Boot + Spring AI. Checks Ollama is reachable,
                    generates title/description (map-reduce for large diffs),
                    finds the open PR for the branch, updates it via the
                    GitHub REST API.
   |
   v
The PR on GitHub is updated. The push itself always completes, even if
diffscribe fails for any reason.
```

The two projects are independent Maven projects (separate deployables), mirroring a local-agent / backend split.

## Repository layout

```
cli/                 Java CLI (picocli, Jackson). Builds target/pr-agent-cli.jar
  install.sh         One-time global installer (jar + hook)
pr-agent-backend/    Spring Boot backend
.githooks/pre-push   Repo-local hook used when developing diffscribe itself
.pragent.properties  Per-repo CLI settings (opt-in marker for the global hook)
```

Backend packages (`com.pragent.backend`): `config`, `controller`, `dto`, `service`, `github`, `ollama`.
CLI packages (`com.pragent.cli`): `git`, `diff`, `http`, `config`.

## Requirements

- Java 17 or newer (both projects target 17)
- Maven (the backend also ships `mvnw`)
- Git, and Git Bash on Windows (the installer and hook are POSIX shell scripts)
- [Ollama](https://ollama.com) with a model pulled
- A GitHub personal access token (see below)

## Quick start (new machine)

1. **Ollama:** install it, then `ollama pull qwen2.5-coder:1.5b`. Larger models give better output: set `OLLAMA_MODEL` (for example `qwen2.5-coder:7b`).
2. **GitHub token:** follow "GitHub token setup" below.
3. **Backend:** `cd pr-agent-backend && ./mvnw spring-boot:run` (Windows: `.\mvnw spring-boot:run`). Check it with `curl http://localhost:8080/api/health` and `curl http://localhost:8080/api/test-llm` (the second one exercises Ollama).
4. **CLI:** `cd cli && mvn clean package`, then in Git Bash `./install.sh`.
5. **Opt a repo in:** create `.pragent.properties` at the repo root (see Configuration).
6. **Use it:** open a PR for your branch on GitHub, then `git push`. The PR title and description update automatically.

Each user runs their own backend and uses their **own** token. Nobody needs anyone else's credentials.

## GitHub token setup

The backend needs a GitHub Personal Access Token (PAT) to update PRs on your behalf. Provide it as the `GITHUB_TOKEN` environment variable. Never write it into `application.yaml` or any file that gets committed.

### 1. Create a token

GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token. Select the **`repo`** scope. Copy the token immediately, because GitHub shows it only once.

### 2. Set it as an environment variable

**Windows (PowerShell), persistent:**
```powershell
[Environment]::SetEnvironmentVariable("GITHUB_TOKEN", "ghp_your_token_here", "User")
```
GUI alternative: Start → "Edit environment variables for your account" → New → name `GITHUB_TOKEN`.

**macOS / Linux:** add to `~/.zshrc` or `~/.bashrc`, then restart the terminal:
```bash
export GITHUB_TOKEN="ghp_your_token_here"
```

### 3. Restart whatever launches the backend

Environment variables are read when a process starts, and each terminal has its own copy. Open a **new** terminal, and fully restart IntelliJ if you run the backend from it (or set the variable in its Run Configuration instead).

### 4. Verify

```powershell
$env:GITHUB_TOKEN.Substring(0,4)   # should print: ghp_
```

### 5. Confirm `application.yaml` holds only the placeholder

```yaml
github:
  token: ${GITHUB_TOKEN:}
```

The backend refuses to start if the token is missing or blank, and says so.

## Configuration

**Backend** (`pr-agent-backend/src/main/resources/application.yaml`). Every value is overridable by environment variable:

| Setting | Env var | Default |
|---|---|---|
| Ollama URL | `OLLAMA_BASE_URL` | `http://localhost:11434` |
| Model | `OLLAMA_MODEL` | `qwen2.5-coder:1.5b` |
| GitHub token | `GITHUB_TOKEN` | none (required) |

**CLI** (`.pragent.properties` at the repo root, optional keys):

```properties
backend.url=http://localhost:8080
default.base=main
max.chunk.chars=8000
```

Precedence: command-line flag, then `.pragent.properties`, then the built-in default. `--base` has **no** built-in default on purpose: pass it, or set `default.base`.

## Using the CLI directly

```bash
java -jar cli/target/pr-agent-cli.jar --base main
```

| Flag | Purpose |
|---|---|
| `--base` | Branch/ref to diff against (required unless `default.base` is set) |
| `--repo-path` | Repo location (default `.`) |
| `--max-chunk-chars` | Chunk size before splitting (default 8000) |
| `--backend-url` | Backend URL (default `http://localhost:8080`) |
| `--dry-run` | Print the filtered diff only. No network calls, no side effects |

Only **committed** changes on your branch versus the base are seen. Uncommitted work and running while on the base branch itself produce "no changes".

## Automation

**Recommended: global install.** From `cli/`, in Git Bash, after `mvn clean package`:
```bash
./install.sh
```
This copies the jar to `~/.pragent/`, writes a global `pre-push` hook, and sets `git config --global core.hooksPath`. The hook only runs in repos that contain a `.pragent.properties` file, and it never blocks a push. If diffscribe fails (backend down, no PR yet, and so on), it prints a message and the push continues. Re-run `install.sh` after rebuilding the CLI.

Notes:
- The installer records the full path of the `java` it finds, because git hooks can see a different `PATH` than your terminal. If that Java is older than 17 the jar won't run.
- A repo-local `core.hooksPath` overrides the global one. This repo has one (`.githooks`), so diffscribe's own hook is the repo-local `.githooks/pre-push`.
- A brand-new branch has no PR yet, so the first push just logs "no open PR". Create the PR on GitHub, and later pushes update it.

## API

- `POST /api/generate-pr-description`: `{ "diffChunks": [...] }`. Generate only, no side effects. One chunk is a single call. Several chunks are summarized one at a time and then synthesized into one result.
- `POST /api/pr/update`: `{ "repoOwner", "repoName", "branch", "diffChunks" }`. Generates, then updates the open PR for that branch. It does not create PRs.
- `GET /api/health`, `GET /api/test-llm`: liveness and an end-to-end Ollama check.

## Error reference

| Where | Message / status | Meaning |
|---|---|---|
| Backend `503` | Ollama is not reachable | Ollama isn't running or the URL is wrong. Fails in about 3 seconds |
| Backend `404` | No open PR found / GitHub returned 404 | Create the PR first; check owner/repo and token scope |
| Backend `429` | GitHub API rate limit reached | Wait as the message says |
| Backend `502` | GitHub rejected the token / denied access | Token expired, revoked, or missing `repo` scope |
| CLI | Could not connect to backend | Start the backend |
| CLI | Backend did not respond within 5 minutes | Large diff or a stuck Ollama |

The `429` branch has not yet been exercised against a real GitHub rate limit. The `401` path uses the same translation code and has been tested.

## Known limitations

- **Output quality depends on the model.** With `qwen2.5-coder:1.5b` (chosen for disk space):
  - `type` is sometimes wrong for refactors and added validation (labelled `fix` instead of `feat`).
  - The "Why" section can invent a plausible reason instead of saying it isn't evident.
  - The three-heading template (`## What changed`, `## Why`, `## How to test`) is not always followed on large multi-chunk diffs.
  Setting `OLLAMA_MODEL` to a bigger model needs no code changes.
- Only updates existing PRs. `pre-push` runs before the branch exists on GitHub, so it cannot create one on the first push.
- No automated tests yet.
- Local-first: each user runs their own backend and Ollama. There is no hosted version.
