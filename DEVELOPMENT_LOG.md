### Repository Configuration (`.pragent.properties`)
- **Per-Repo Settings**: Enables repository-level CLI configuration without requiring repetitive flags.
- **Precedence**: CLI arguments override file-defined settings (e.g., `--base develop` overrides `default.base=main`).
- **Version Control**: Intended to be committed to Git (do not add to `.gitignore`).

---

### Global Pre-Push Hook & Build Target (`feature/hook-installer`)
- **One-Time Setup (`install.sh`)**: Copies `pr-agent-cli.jar` to `~/.pragent/`, bakes the resolved `java` executable path, and configures `git config --global core.hooksPath ~/.pragent/githooks`.
- **Opt-In Triggering**: Automates PR updates on `git push` solely in repositories containing `.pragent.properties`. Local repository hook overrides remain respected.
- **Target Downgrade**: Lowered Java build target from 21 to 17 in `cli/pom.xml` to match installed JDK environments without impacting feature capabilities.

---

### feature/ollama-failure-handling: fail fast instead of multi-minute retries

- Added OllamaHealthChecker - a 3-second pre-flight check before any
generation attempt, instead of relying on Spring AI's own retry logic (which can take several minutes to give up when Ollama is fully down).
- Centralized exception-to-HTTP-status mapping into ApiExceptionHandler (@RestControllerAdvice), removing the inline try/catch from PrUpdateController.
- Added spring.ai.retry.max-attempts=2 for defense in depth on real transient errors.
