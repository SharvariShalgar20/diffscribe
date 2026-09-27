#!/bin/sh
# One-time machine setup: installs the pr-agent CLI jar and pre-push hook to
# a global location, and points git's global hooksPath at it - so every repo
# on this machine gets automatic PR updates, not just this one. Run this
# from the cli/ directory, after `mvn clean package`.

set -e

INSTALL_DIR="$HOME/.pragent"
mkdir -p "$INSTALL_DIR/githooks"

cp target/pr-agent-cli.jar "$INSTALL_DIR/pr-agent-cli.jar"
echo "Installed jar to $INSTALL_DIR/pr-agent-cli.jar"

JAVA_BIN="$(command -v java)"
if [ -z "$JAVA_BIN" ]; then
  echo "ERROR: could not find 'java' on PATH. Install aborted."
  exit 1
fi
echo "Using java at: $JAVA_BIN"

cat > "$INSTALL_DIR/githooks/pre-push" << HOOK
#!/bin/sh
CLI_JAR="$INSTALL_DIR/pr-agent-cli.jar"
JAVA_BIN="$JAVA_BIN"

if [ ! -f "\$CLI_JAR" ]; then
  echo "pr-agent: CLI jar not found at \$CLI_JAR, skipping."
  exit 0
fi

REPO_ROOT="\$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "\$REPO_ROOT" ] || [ ! -f "\$REPO_ROOT/.pragent.properties" ]; then
  exit 0
fi

echo "pr-agent: updating PR description..."
"\$JAVA_BIN" -jar "\$CLI_JAR" || echo "pr-agent: update failed or no PR exists yet for this branch (this is OK, push continues)."

exit 0
HOOK
chmod +x "$INSTALL_DIR/githooks/pre-push"
echo "Installed global pre-push hook to $INSTALL_DIR/githooks/pre-push"

git config --global core.hooksPath "$INSTALL_DIR/githooks"
echo "Set core.hooksPath globally to $INSTALL_DIR/githooks"

echo ""
echo "Done. pr-agent will now run automatically on 'git push' for any repo"
echo "that has a .pragent.properties file at its root."