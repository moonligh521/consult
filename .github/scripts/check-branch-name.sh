#!/usr/bin/env bash
# 校验合并请求的头分支是否符合 docs/engineering/git-workflow.md。
set -euo pipefail

if [ "${1:-}" = "" ]; then
  echo "用法: check-branch-name.sh <分支名>" >&2
  exit 2
fi

name="$1"
feature='^feature-V(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-[0-9]{8}(-[a-z0-9]+(-[a-z0-9]+)*)?$'
release='^release-V(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-[0-9]{8}$'
hotfix='^hotfix-V(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-[0-9]{8}$'

if printf '%s\n' "$name" | grep -Eq "$feature|$release|$hotfix"; then
  echo "ok: $name"
  exit 0
fi

echo "分支名不符合规范: $name" >&2
echo "允许 feature-V主.次-年月日[-短英文名]、release-V主.次-年月日、hotfix-V主.次.修订-年月日" >&2
exit 1
