# Uninstalling diffscribe



Do only the parts you need. The first one is enough to stop diffscribe from running on `git push`.



## 1. Stop the hook (the important one)



In Git Bash, from the folder that has `uninstall.sh` (it's in `cli/`, and it's attached to each release):



```bash

./uninstall.sh

```



This:

- unsets git's \*\*global\*\* `core.hooksPath`, but \*\*only if it points at diffscribe\*\*. A hooks path you set yourself is left alone.

- deletes `\~/.pragent/` (the jar and the hook).



By hand, the same thing is:

```bash

git config --global --unset core.hooksPath

rm -rf \~/.pragent

```



*\*Check:\*\* `git config --global --get core.hooksPath` prints nothing, and `ls \~/.pragent` says the folder doesn't exist.



> When you installed, diffscribe replaced any global `core.hooksPath` you already had. If you had your own, set it again: `git config --global core.hooksPath <your-path>`.



## 2. Opt repos out (optional)



The global hook only acts in repos that contain `.pragent.properties`. Delete that file (and commit the deletion) to turn diffscribe off for one repo while leaving it installed elsewhere.



If the repo has its own `.githooks/pre-push` (this project does) and a repo-local `core.hooksPath`, remove that with `git config --unset core.hooksPath` inside the repo.



## 3. Stop the backend



- Run with `java -jar` or `mvnw`: press \*\*Ctrl+C\*\* in its terminal.

- Run with Docker:

&#x20; ```bash

&#x20; docker compose down -v        # -v also deletes the model volume if you used the bundled Ollama

&#x20; docker rmi diffscribe-backend

&#x20; ```



## 4. Remove your GitHub token



Delete the variable:



PowerShell:

```powershell

[Environment]::SetEnvironmentVariable("GITHUB\_TOKEN", $null, "User")

```

bash: delete the `export GITHUB\_TOKEN=...` line from `\~/.zshrc` or `\~/.bashrc`.



Then \*\*revoke it on GitHub\*\*: Settings → Developer settings → Personal access tokens → delete the token. Deleting the variable only removes your local copy, and the token stays valid on GitHub until you revoke it.



## 5. Free up disk space (optional)



```bash

ollama rm qwen2.5-coder:1.5b      # use the name shown by `ollama list`

```

You can uninstall Ollama itself from your system's installed-apps list if you don't use it for anything else.



## What stays behind



- \*\*Past PR text.\*\* The titles and descriptions diffscribe wrote stay on your PRs. Edit them by hand if you want them changed.

- \*\*The `type: ...` labels\*\* it created in your repos. Delete them on the repo's \*\*Labels\*\* page, or with the GitHub CLI: `gh label delete "type: feat"` (and `fix`, `refactor`, `docs`, `test`, `chore`).

- \*\*Release files\*\* you downloaded. Delete the folder.

