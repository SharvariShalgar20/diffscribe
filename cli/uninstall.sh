#!/bin/sh
# Removes the global diffscribe install made by install.sh:
#   - unsets git's global core.hooksPath, but only if it points at ~/.pragent
#   - deletes ~/.pragent (the jar and the hook)
# It does not touch your repos, your GITHUB_TOKEN, or Ollama. See docs/UNINSTALL.md.

set -e

if [ -z "$HOME" ]; then
  echo "ERROR: \$HOME is not set. Nothing was changed."
  exit 1
fi
INSTALL_DIR="$HOME/.pragent"

current="$(git config --global --get core.hooksPath || true)"
case "$current" in
  */.pragent/githooks)
    git config --global --unset core.hooksPath
    echo "Unset global core.hooksPath (was: $current)"
    ;;
  "")
    echo "No global core.hooksPath is set. Nothing to unset."
    ;;
  *)
    echo "Global core.hooksPath points somewhere else ($current). Left unchanged."
    ;;
esac

if [ -d "$INSTALL_DIR" ]; then
  rm -rf "$INSTALL_DIR"
  echo "Deleted $INSTALL_DIR"
else
  echo "$INSTALL_DIR not found. Nothing to delete."
fi

echo ""
echo "Done. diffscribe no longer runs on 'git push'."
echo "If you had your own global hooks path before installing, set it again with:"
echo "  git config --global core.hooksPath <your-path>"
