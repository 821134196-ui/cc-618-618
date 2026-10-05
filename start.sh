#!/usr/bin/env bash
# 城市 AED 设备巡检与急救取用回补系统 —— 一键启动
# 用法: ./start.sh          构建前端 + 打包后端 + 启动（http://localhost:8080）
#       ./start.sh --test   只运行后端验证测试
set -euo pipefail
cd "$(dirname "$0")"

# ---- 定位 Java / Maven：优先系统 PATH，其次本地下载的工具链 ----
if ! command -v java >/dev/null 2>&1 && [ -d /home/node/tools/jdk ]; then
  export JAVA_HOME=/home/node/tools/jdk
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v mvn >/dev/null 2>&1 && [ -d /home/node/tools/maven ]; then
  export PATH="/home/node/tools/maven/bin:$PATH"
fi
command -v java >/dev/null 2>&1 || { echo "未找到 Java，请安装 JDK 17+"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "未找到 Maven"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "未找到 Node.js"; exit 1; }

if [ "${1:-}" = "--test" ]; then
  (cd backend && mvn -B test)
  exit 0
fi

echo "==> 1/3 构建前端 (Svelte)"
(cd frontend && npm install --no-fund --no-audit && npm run build)

echo "==> 2/3 打包后端 (Quarkus)，前端静态资源一并嵌入"
rm -rf backend/src/main/resources/META-INF/resources
mkdir -p backend/src/main/resources/META-INF/resources
cp -r frontend/dist/* backend/src/main/resources/META-INF/resources/
(cd backend && mvn -B -q package -DskipTests)

echo "==> 3/3 启动服务"
echo "    公众查询页:  http://localhost:8080/"
echo "    维护工作台:  http://localhost:8080/#/admin"
echo "    数据库文件:  backend/aed-city.db (SQLite，首次启动自动建表并写入演示数据)"
cd backend && exec java -jar target/quarkus-app/quarkus-run.jar
