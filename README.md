

# diffscribe (pr-agent)

![CI](https://github.com/SharvariShalgar20/diffscribe/actions/workflows/ci.yml/badge.svg)

Writes your pull request title and description for you. On `git push`, a local CLI extracts the diff, a Spring AI backend generates the PR text with a **local** Ollama model, and the result is written onto your open GitHub PR. No diffs are pasted into a chatbot, and no code leaves your machine except the PR text sent to GitHub.

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
pr-agent-backend/   Spring Boot + Spring AI.
                    1. checks Ollama is reachable (fails in about 3 seconds if not)
                    2. finds the open PR for the branch
                    3. asks the model for five labeled lines (map-reduce for big diffs)
                    4. builds the title and the three headings in code
                    5. applies file-path rules to the change type, adds reviewer notes
                    6. updates the PR and sets a "type: ..." label
   |
   v
The PR on GitHub is updated. The push itself always completes, even if
diffscribe fails for any reason.
```

The two projects are independent Maven projects (separate deployables), mirroring a local-agent / backend split.

## What you get on the PR

- **Title:** `type: short summary`, 72 characters at most, with the prefix always matching the type.
- **Description:** exactly three sections (`## What changed`, `## Why`, `## How to test`). The headings are written by code, not the model, so they are always there.
- **Reviewer notes** (rule-based, not model-written) appended when relevant: a possible secret on an added line (file name only, never the value), security-related file names, source changes with no test changes, build/dependency changes alongside code, and very large changes (over 500 lines).
- **A label** such as `type: feat`, created on first use and swapped when the type changes. Your own labels are never touched. Turn it off with `pragent.labels.enabled: false`.

## Repository layout

```
cli/                 Java CLI (picocli, Jackson). Builds target/pr-agent-cli.jar
  install.sh         One-time global installer (jar + hook)
pr-agent-backend/    Spring Boot backend
eval/                Scores a model against saved sample diffs (see "Measuring quality")
.github/workflows/   ci.yml (tests on every PR), release.yml (jars on a version tag)
.githooks/pre-push   Repo-local hook used when developing diffscribe itself
.pragent.properties  Per-repo CLI settings (also the opt-in marker for the global hook)
```

Backend packages (`com.pragent.backend`): `config`, `controller`, `dto`, `service`, `github`, `ollama`.
CLI packages (`com.pragent.cli`): `git`, `diff`, `http`, `config`.

## Requirements

- Java 17 or newer
- Git, and Git Bash on Windows (the installer and hook are POSIX shell scripts)
- [Ollama](https://ollama.com) with a model pulled
- A GitHub personal access token (see below)
- Maven only if you build from source (the backend ships `mvnw`)

## Quick start

### Option A: prebuilt jars (no Maven)

1. Download `pr-agent-cli.jar`, `pr-agent-backend.jar` and `install.sh` from the latest [Release](../../releases).
2. In Git Bash: `./install.sh pr-agent-cli.jar`
3. Set up the token (next section), start Ollama, then start the backend: `java -jar pr-agent-backend.jar`
4. Continue at step 5 below.

### Option B: build from source

1. **Ollama:** install it, then `ollama pull qwen2.5-coder:1.5b`. Bigger models give better type decisions: set `OLLAMA_MODEL` (for example `qwen2.5-coder:3b`).
2. **GitHub token:** follow "GitHub token setup" below.
3. **Backend:** `cd pr-agent-backend && ./mvnw spring-boot:run` (Windows: `.\mvnw spring-boot:run`). Check it with `curl http://localhost:8080/api/health` and `curl http://localhost:8080/api/test-llm` (the second one exercises Ollama).
4. **CLI:** `cd cli && mvn clean package`, then in Git Bash `./install.sh`.
5. **Opt a repo in:** create `.pragent.properties` at the repo root (see Configuration).
6. **Use it:** open a PR for your branch on GitHub, then `git push`. The PR title, description and label update automatically.

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

**Backend** (`pr-agent-backend/src/main/resources/application.yaml`):

| Setting | Env var | Default |
|---|---|---|
| Ollama URL | `OLLAMA_BASE_URL` | `http://localhost:11434` |
| Model | `OLLAMA_MODEL` | `qwen2.5-coder:1.5b` |
| GitHub token | `GITHUB_TOKEN` | none (required) |
| Type labels | (property `pragent.labels.enabled`) | `true` |

The model runs at temperature 0.2 so the same diff gives the same answer from run to run.

**CLI** (`.pragent.properties` at the repo root, all keys optional):

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
| `--create-pr` | Create the PR if none exists for this branch, or update it if one does. Run it **after** the branch's first push |

Only **committed** changes on your branch versus the base are seen. Uncommitted work, and running while on the base branch itself, give "no changes".

### Your first push of a new branch

The pre-push hook runs before the branch exists on GitHub, so it can only log "no open PR" that time. After that push, run:

```bash
java -jar cli/target/pr-agent-cli.jar --create-pr
```

If the branch isn't on GitHub yet, the CLI stops straight away and tells you the exact `git push` to run. The PR is created against `--base` / `default.base`. Running it again just updates the same PR.

## Automation

**Recommended: global install.** From `cli/`, in Git Bash:
```bash
./install.sh                      # after mvn clean package
./install.sh path/to/pr-agent-cli.jar   # or with a downloaded jar
```
This copies the jar to `~/.pragent/`, writes a global `pre-push` hook, and sets `git config --global core.hooksPath`. The hook only runs in repos that contain a `.pragent.properties` file, and it never blocks a push. If diffscribe fails (backend down, no PR yet, and so on), it prints a message and the push continues. Re-run `install.sh` after you get a new jar.

Notes:
- The installer records the full path of the `java` it finds, because git hooks can see a different `PATH` than your terminal. If that Java is older than 17 the jar won't run.
- A repo-local `core.hooksPath` overrides the global one. This repo has one (`.githooks`), so diffscribe's own hook is the repo-local `.githooks/pre-push`.

## API

- `POST /api/generate-pr-description`: `{ "diffChunks": [...] }`. Generate only, no side effects. One chunk is a single call. Several chunks are summarized one at a time and then combined into one result.
- `POST /api/pr/update`: `{ "repoOwner", "repoName", "branch", "diffChunks" }`. Updates the open PR for that branch, and sets its type label. Does not create PRs.
- `POST /api/pr/create`: `{ "repoOwner", "repoName", "branch", "base", "diffChunks" }`. Creates the PR, or updates it if one exists. Returns `created: true/false`.
- `GET /api/health`, `GET /api/test-llm`: liveness and an end-to-end Ollama check.

## Error reference

| Where | Message / status | Meaning |
|---|---|---|
| Backend `503` | Ollama is not reachable | Ollama isn't running or the URL is wrong. Fails in about 3 seconds |
| Backend `404` | No open PR found / GitHub returned 404 | Create the PR first; check owner/repo and token scope |
| Backend `422` | GitHub could not create the PR | (`/create` only) branch not pushed, nothing to compare, or a PR already exists |
| Backend `429` | GitHub API rate limit reached | Wait as the message says |
| Backend `502` | GitHub rejected the token / denied access | Token expired, revoked, or missing `repo` scope |
| Backend `502` | The model's answer could not be understood | Two bad answers in a row; retry, or use a larger model |
| CLI | Could not connect to backend | Start the backend |
| CLI | Backend did not respond within 5 minutes | Large diff or a stuck Ollama |

The `429` branch is covered by unit tests but has not been seen against a real GitHub rate limit.

## Measuring quality

`eval/` holds seven saved diffs whose expected change type is in the file name (`02-fix-off-by-one.diff`), and a script that sends each to a running backend and scores the answers. It calls only `/api/generate-pr-description`, so there are no GitHub calls or side effects.

```powershell
powershell -ExecutionPolicy Bypass -File .\eval\run-eval.ps1 -Label my-model -Runs 3
```

It prints a table, a summary (type correct, title format, three headings, errors) and saves a CSV in `eval/results/`. Use `-Runs 3` or more: a single run of a random model is misleading. To compare models, set `OLLAMA_MODEL`, restart the backend, and run it again with a different `-Label`.

Results so far for `qwen2.5-coder:1.5b`, 21 answers each:

| | Original design | Current design |
|---|---|---|
| Type correct | 24% | 62% |
| Title format | 81% | 100% |
| Three headings | 33% | 100% |
| Errors | 1 | 0 |

Most of the type gain comes from the file-path rules (docs-only, test-only and build-only changes), not the model. On the four samples that need real judgement (new feature vs fix vs refactor) the model is right about a third of the time, and it tends to answer `fix` for new code.

## Docker (optional)

Runs the backend in a container. Your CLI and git hook stay on the host and keep using `http://localhost:8080`.

```bash
# backend only, using the Ollama already running on your machine
docker compose up --build -d

# or run Ollama in Docker too
docker compose -f docker-compose.yml -f docker-compose.ollama.yml up --build -d
docker compose -f docker-compose.yml -f docker-compose.ollama.yml exec ollama ollama pull qwen2.5-coder:1.5b
```

`GITHUB_TOKEN` must be set in the shell that runs `docker compose`. It is passed in at runtime and is never baked into the image. The port is bound to `127.0.0.1` on purpose: the backend holds your GitHub token and must not be reachable from other machines. Stop with `docker compose down` (add `-v` to also remove the model volume).


## Tests and CI

```powershell
cd pr-agent-backend; .\mvnw test      # backend
cd cli; mvn test                       # CLI
```

`.github/workflows/ci.yml` runs `mvn -B verify` for both projects on every pull request and every push to `main`. The unit tests use mocks, so CI needs no Ollama and no token.

## Releases

Pushing a tag that starts with `v` runs `.github/workflows/release.yml`. It builds and tests both projects, then publishes a GitHub Release with `pr-agent-cli.jar`, `pr-agent-backend.jar`, `install.sh` and `SHA256SUMS.txt`. A tag containing a hyphen (such as `v0.1.0-rc1`) is marked as a pre-release.

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Known limitations

- **Type accuracy on judgement calls depends on the model.** With `qwen2.5-coder:1.5b` the model often labels new code `fix`. The file-path rules cover docs, tests and build files, but not feat vs fix vs refactor. A larger model needs no code change: set `OLLAMA_MODEL`.
- **The "Why" section is usually "Not evident from the diff."** A diff rarely says why a change was made, and the prompt tells the model not to guess.
- **Reviewer notes are heuristics.** They match file names and text patterns, so they can miss things and can flag harmless ones. They are prompts to look, not verdicts.
- **Only updates an open PR** from the hook. Use `--create-pr` after a branch's first push.
- **Local-first.** Each user runs their own backend and Ollama. There is no hosted version.
- **The newest features** (labels, `--create-pr`, the release workflow) have had the least real-world use. If one misbehaves, the backend log and the CLI message say what failed.

## Lessons learned

- Build JSON with a library, not string concatenation. In Windows PowerShell 5.1, `ConvertTo-Json` can turn a string from `Get-Content` into `{"value": ...}`. Use `[System.IO.File]::ReadAllText` and a .NET serializer.
- Don't ask a small model for JSON containing multi-line text. It writes raw line breaks inside strings and the parser throws. Ask for labeled lines and build the structure in code.
- Anything a fixed rule can decide (headings, title prefix, docs/test/build types) should not be left to the model.
- Measure before changing prompts or models. Three runs gave a very different picture from one run.
- `git diff --numstat` prints root-relative paths, but pathspecs are relative to the current directory. Always run git from the repo top-level.
- Spring AI generates its schema from enum constant names while Jackson honours `@JsonProperty`. Handle enum casing explicitly.
- Positional URI template variables are counted per occurrence, not per name.
- Git hooks run with their own environment: pin the `java` path at install time.
- Environment variables are per-terminal. After changing one, restart the process that needs it.
- PowerShell mangles `-D...=...` arguments unless the whole flag is single-quoted.
- A clean CI machine finds what your PC hides: a config file that was never committed made the first CI run fail.
