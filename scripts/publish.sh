#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 <github_token> [repo_name]" >&2
  exit 1
fi

TOKEN="$1"
REPO="${2:-nullvariant}"
API="https://api.github.com"

OWNER="$(curl -fsS -H "Authorization: Bearer $TOKEN" "$API/user" | jq -r '.login')"
if [ -z "$OWNER" ] || [ "$OWNER" = "null" ]; then
  echo "token rejected" >&2
  exit 1
fi

FULL="$OWNER/$REPO"

curl -fsS -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"name\":\"$REPO\",\"private\":false,\"auto_init\":false}" \
  "$API/user/repos" >/dev/null 2>&1 || true

git push "https://x-access-token:$TOKEN@github.com/$FULL.git" main

echo "repo:    https://github.com/$FULL"
echo "actions: https://github.com/$FULL/actions"
