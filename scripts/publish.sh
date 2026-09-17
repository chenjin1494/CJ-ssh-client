#!/usr/bin/env bash
set -euo pipefail

git rev-parse --git-dir >/dev/null 2>&1 || git init -b main
git add .
if ! git diff --cached --quiet; then
  git commit -m "Initial commit: Android SSH Client with CI/CD"
fi

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  if git remote get-url origin >/dev/null 2>&1; then
    git push -u origin HEAD:main
  else
    gh repo create AndroidSSHClient --public --source=. --remote=origin --push
  fi
else
  printf '%s\n' 'GitHub CLI is unavailable or not authenticated.'
  printf '%s\n' 'Push manually: git push -u origin main'
  printf '%s\n' 'Then configure repository Actions secrets documented in README.md.'
fi
