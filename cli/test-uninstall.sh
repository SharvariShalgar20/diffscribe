#!/bin/sh
# Tests uninstall.sh against a throwaway HOME and a throwaway git config.
# Run it from the folder that contains uninstall.sh.
# It never touches your real ~/.pragent or your real global git config.

set -e

# Keep paths like /opt/team/hooks literal when Git Bash hands them to git.exe.
# Otherwise they get rewritten to C:/Program Files/Git/opt/team/hooks.
export MSYS_NO_PATHCONV=1

export HOME=/tmp/fakehome
rm -rf "$HOME"
mkdir -p "$HOME/.pragent/githooks"
touch "$HOME/.pragent/pr-agent-cli.jar"

# git.exe is a native Windows program and can't resolve Git Bash's /tmp, and
# MSYS_NO_PATHCONV=1 means nothing translates it. Give git a real Windows-style
# path. On Linux/macOS cygpath doesn't exist, so the plain path is used.
if command -v cygpath >/dev/null 2>&1; then
  export GIT_CONFIG_GLOBAL="$(cygpath -m "$HOME")/.gitconfig"
else
  export GIT_CONFIG_GLOBAL="$HOME/.gitconfig"
fi
touch "$GIT_CONFIG_GLOBAL"

failures=0

check_equal () {   # description, expected, actual
  if [ "$2" = "$3" ]; then
    echo "PASS: $1"
  else
    echo "FAIL: $1 (expected [$2], got [$3])"
    failures=$((failures + 1))
  fi
}

check_dir_gone () {   # description
  if [ ! -d "$HOME/.pragent" ]; then
    echo "PASS: $1"
  else
    echo "FAIL: $1 (directory still exists)"
    failures=$((failures + 1))
  fi
}

hooks_path () { git config --global --get core.hooksPath || true; }

echo "=== 1. normal uninstall (posix-style path) ==="
git config --global core.hooksPath "$HOME/.pragent/githooks"
sh uninstall.sh
check_equal "hooksPath is unset" "" "$(hooks_path)"
check_dir_gone "~/.pragent deleted"

echo "=== 2. run again, nothing installed ==="
sh uninstall.sh
check_equal "still unset" "" "$(hooks_path)"

echo "=== 3. windows-style path as git stores it ==="
mkdir -p "$HOME/.pragent/githooks"
git config --global core.hooksPath "C:/Users/DELL/.pragent/githooks"
sh uninstall.sh
check_equal "hooksPath is unset" "" "$(hooks_path)"

echo "=== 4. someone else's hooks path must be preserved ==="
mkdir -p "$HOME/.pragent"
git config --global core.hooksPath "/opt/team/hooks"
sh uninstall.sh
check_equal "other hooksPath preserved" "/opt/team/hooks" "$(hooks_path)"

echo "=== 5. a path that merely contains the word must not match ==="
git config --global core.hooksPath "/home/me/.pragent/githooks-old"
sh uninstall.sh
check_equal "lookalike hooksPath preserved" "/home/me/.pragent/githooks-old" "$(hooks_path)"

echo "=== 6. HOME unset guard ==="
code=0
( unset HOME; sh uninstall.sh ) || code=$?
check_equal "exits with 1 and changes nothing" "1" "$code"

echo ""
if [ "$failures" -eq 0 ]; then
  echo "All checks passed."
else
  echo "$failures check(s) FAILED."
  exit 1
fi
