# diffscribe quickstart



From nothing to an automatically written PR description in about 15 minutes. Each step ends with a check, so you know it worked before moving on.



## What you'll have at the end



On every `git push`, the title, description and a `type:` label of your open GitHub PR are written for you by a model running on **your own machine**.



**What leaves your machine:** only the generated PR title, description and label, sent to GitHub with your own token. The code diff goes only to the diffscribe backend and Ollama running locally.



## Before you start

| You need | Check |
|---|---|
| Java 17 or newer | `java -version` |
| Git (and **Git Bash** on Windows) | `git --version` |
| A GitHub account and a repo with an open PR (or one you're about to open) | |
| About 1 GB of free disk for the smallest model | |



## 1. Install Ollama and a model



Install [Ollama](https://ollama.com), then:



```bash

ollama pull qwen2.5-coder:1.5b

ollama list

```



**Check:** `ollama list` shows `qwen2.5-coder:1.5b`.



The 1.5b model is the smallest that works. It is weaker at deciding between feat, fix and refactor. If you have the disk space, `qwen2.5-coder:3b` or `7b` are better (set `OLLAMA_MODEL` before starting the backend).



## 2. Create a GitHub token



GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token. Tick the **`repo`** scope and copy the token straight away; GitHub shows it once.



Save it as an environment variable. **Never** put it in a file that gets committed.



Windows (PowerShell):

```powershell

[Environment]::SetEnvironmentVariable("GITHUB\_TOKEN", "ghp\_your\_token\_here", "User")

```

macOS / Linux: add `export GITHUB\_TOKEN="ghp\_your\_token\_here"` to `\~/.zshrc` or `\~/.bashrc`.



Now **close every terminal and open a new one** (and restart your IDE if you start things from it). Each terminal keeps its own copy of the environment.



**Check** (in a new terminal): `$env:GITHUB\_TOKEN.Substring(0,4)` prints `ghp\_` (PowerShell), or `echo ${GITHUB\_TOKEN:0:4}` (bash).



## 3. Get diffscribe



**Easiest: prebuilt files.\*\* From the repo's **Releases** page, download `pr-agent-cli.jar`, `pr-agent-backend.jar`, `install.sh` and `uninstall.sh` into one folder.



**Or build from source:**

```bash

git clone https://github.com/SharvariShalgar20/diffscribe.git

cd diffscribe

(cd pr-agent-backend \&\& ./mvnw -B -DskipTests package)

(cd cli \&\& mvn -B clean package)

```

On Windows use `.\mvnw` in PowerShell for the backend.



**Check:** the files exist (`pr-agent-cli.jar` and `pr-agent-backend.jar`, the latter under `pr-agent-backend/target/` if you built it).



## 4. Start the backend



Keep Ollama running, then, in the terminal where `GITHUB\_TOKEN` is set:



```bash

java -jar pr-agent-backend.jar

```

(from source: `cd pr-agent-backend && ./mvnw spring-boot:run`.) Prefer Docker? See the "Docker" section of the README.



**Check**, in a second terminal:

```bash

curl http://localhost:8080/api/health       # prints OK

curl http://localhost:8080/api/test-llm     # a short answer from the model

```



If the backend stops at startup saying `GITHUB\_TOKEN is not set`, the terminal can't see the variable. Go back to step 2.



\## 5. Install the git hook



In **Git Bash**, from the folder with the CLI jar:



```bash

./install.sh pr-agent-cli.jar

```

(from source, inside `cli/`, just `./install.sh`.)



It copies the jar to `\~.pragent/` and sets a global git hook. The hook only runs in repos you opt in (next step), and it never blocks a push.



**Check:** the output ends with `Done. pr-agent will now run automatically on 'git push'...`



> It also takes over git's global `core.hooksPath` setting. If you already use one, see `docs/UNINSTALL.md` first.



## 6. Opt a repo in



In the repo you want diffscribe on, create a file named `.pragent.properties` at the top level:



```properties

default.base=main

```

Use your repo's main branch name. Commit the file if you want it shared with your team.



**Check (nothing is sent):** `java -jar ~/.pragent/pr-agent-cli.jar --dry-run` on a branch with commits prints the filtered diff the model would see.



## 7. Your first PR



```bash

git checkout -b try-diffscribe

\# make a change, then:

git add . && git commit -m "try diffscribe"

git push -u origin try-diffscribe

```

The hook runs and says there's no open PR yet. That is normal for a branch's first push, because the PR can't exist until the branch does. Now create it:



```bash

java -jar ~/.pragent/pr-agent-cli.jar --create-pr

```

It prints `Created PR #N: <link>`. From now on every `git push` to this branch updates that PR automatically.



If you open PRs on GitHub yourself, that works too: open the PR, push again, and the title and description are replaced.



**Check:** open the PR on GitHub. You should see a title like `feat: add ...`, a description with `What changed`, `Why` and `How to test`, a `type: feat` label, and sometimes a \*\*Reviewer notes\*\* block at the bottom.



## Day to day



Before you push, have **Ollama and the backend running**. If either is off, the push still goes through and the hook prints why it skipped the update.



`--dry-run` shows what would be sent without calling anything.



## Troubleshooting
| What you see | What to do |
|---|---|
| The hook prints nothing | The repo has no `.pragent.properties` at its top level, or a repo-local hooks path overrides the global one: `git config --get core.hooksPath` in the repo |
| `No open PR found` | Create the PR first (`--create-pr` after the branch is pushed), or push again once it exists |
| `Could not connect to backend` | Start the backend (step 4) |
| `503 ... Ollama is not reachable` | Start Ollama, and check `ollama list` |
| `502 ... GitHub rejected the token` | The token expired, was revoked, or lacks the `repo` scope. Make a new one |
| `UnsupportedClassVersionError` | The Java the hook found is older than 17. Install Java 17+, then run `install.sh` again so it records the right one |
| `422 ... could not create the PR` | The branch isn't pushed, has no new commits against the base, or already has a PR |
| The description looks odd or the type is wrong | The small model got it wrong. Edit the PR by hand, or try a larger model |



More detail is in the README's error table.



## Uninstalling



See [UNINSTALL.md](UNINSTALL.md).

