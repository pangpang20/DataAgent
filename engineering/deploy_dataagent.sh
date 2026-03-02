#!/usr/bin/env bash

set -euo pipefail

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

log "=== Step 1: 同步代码 ==="
cd /opt/DataAgent
git reset --hard; git clean -fd;git pull
export JAVA_HOME=/opt/java
export PATH=$JAVA_HOME/bin:$PATH

package_dir="/data/DataAgent_src"
deploy_dir="/data/dataagent"

log "=== Step 2: 编译打包 ==="
cd /opt/DataAgent/engineering
./build-dataagent.sh ${package_dir}
version=$(grep "Build Time:" ${package_dir}/VERSION.txt | awk '{print $NF}')
tar -czvf DataAgent_${}version}.tar.gz -C ${package_dir}

log "=== Step 3: 部署 DataAgent（使用 Milvus + 达梦） ==="
./build-dataagent.sh \
  --deploy \
  --deploy-dir ${deploy_dir} \
  --package-dir ${package_dir} \
  --db-type dameng \
  --db-host 172.16.1.137 \
  --db-port 5236 \
  --db-name cyl \
  --db-user cyl \
  --db-password "Audaque@123" \
  --backend-port 8065 \
  --frontend-port 8080 \
  --vector-store milvus \
  --milvus-host 172.16.1.137 \
  --milvus-port 19530 \
  --milvus-username root \
  --milvus-password Milvus \
  --milvus-database default \
  --milvus-collection data_agent_vector

log "=== 部署完成，一切顺利 ==="