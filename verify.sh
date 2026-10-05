#!/usr/bin/env bash
# ============================================================
# 验证脚本：运行后端集成测试（覆盖关键业务约束）+ 前端构建
#   ./verify.sh
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -z "${JAVA_HOME:-}" ] || ! [ -x "$JAVA_HOME/bin/java" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  else
    JAVA_HOME="$(ls -d "$HOME"/tools/jdk-* 2>/dev/null | head -1 || true)"
  fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
if ! command -v mvn >/dev/null 2>&1; then
  MVN_HOME="$(ls -d "$HOME"/tools/apache-maven-* 2>/dev/null | head -1 || true)"
  [ -n "$MVN_HOME" ] && export PATH="$MVN_HOME/bin:$PATH"
fi

echo "==> 后端集成测试（过期不可用 / 取用-回补-复检 / 批次核对 / 防刷新 / 信息隔离 / 地图列表一致）"
cd "$ROOT/backend"
mvn -B -Dmaven.artifact.threads=1 -Dmaven.wagon.http.retryHandler.count=5 test

echo "==> 前端构建检查"
cd "$ROOT/frontend"
[ -d node_modules ] || npm install --no-audit --no-fund
npm run build

echo "==> 全部验证通过"
