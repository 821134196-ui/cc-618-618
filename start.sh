#!/usr/bin/env bash
# ============================================================
# 城市 AED 巡检与急救取用回补系统 —— 一键启动
#
#   ./start.sh
#
# 流程：构建 Svelte 前端 -> 拷贝进 Quarkus 静态资源 ->
#       打包 Quarkus 后端（SQLite 内嵌）-> 启动服务
# 启动后访问：http://localhost:8080  （维护后台：http://localhost:8080/#/admin）
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- 定位 JDK 与 Maven（优先系统已安装的，其次 ~/tools 下的便携版） ----
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
  if [ -n "$MVN_HOME" ]; then
    export PATH="$MVN_HOME/bin:$PATH"
  fi
fi

command -v java >/dev/null 2>&1 || { echo "未找到 Java，请先安装 JDK 17+"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "未找到 Maven，请先安装 Maven 3.8+"; exit 1; }
command -v npm  >/dev/null 2>&1 || { echo "未找到 npm，请先安装 Node.js 18+"; exit 1; }

echo "==> Java: $(java -version 2>&1 | head -1)"
echo "==> Maven: $(mvn -version 2>/dev/null | head -1)"

# ---- 1. 构建前端 ----
echo "==> [1/3] 构建 Svelte 前端"
cd "$ROOT/frontend"
[ -d node_modules ] || npm install --no-audit --no-fund
npm run build

# ---- 2. 前端产物交给 Quarkus 托管 ----
echo "==> [2/3] 打包 Quarkus 后端（含前端静态资源）"
STATIC_DIR="$ROOT/backend/src/main/resources/META-INF/resources"
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -r "$ROOT/frontend/dist/." "$STATIC_DIR/"

cd "$ROOT/backend"
mvn -B -DskipTests -Dmaven.artifact.threads=1 -Dmaven.wagon.http.retryHandler.count=5 package

# ---- 3. 启动 ----
echo "==> [3/3] 启动服务"
echo "    公众查询页:  http://localhost:8080/"
echo "    维护后台:    http://localhost:8080/#/admin"
echo "    （首次启动会自动写入演示数据，数据库文件：backend/aed.db）"
cd "$ROOT/backend"
exec java -jar target/quarkus-app/quarkus-run.jar
