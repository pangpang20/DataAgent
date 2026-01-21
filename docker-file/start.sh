#!/bin/bash

#Copyright 2024-2026 the original author or authors.
#
#Licensed under the Apache License, Version 2.0 (the "License");
#you may not use this file except in compliance with the License.
#You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing, software
#distributed under the License is distributed on an "AS IS" BASIS,
#WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#See the License for the specific language governing permissions and
#limitations under the License.

echo "=================================================="
echo "  启动 Audaque DataAgent 服务"
echo "=================================================="

# 检查是否有正在运行的容器
if docker compose ps | grep -q "Up"; then
    echo "检测到正在运行的容器，正在停止..."
    docker compose down
fi

# 清理未使用的资源
echo "清理未使用的 Docker 资源..."
docker system prune -f

# 重新构建并启动
echo "重新构建并启动服务..."
docker compose up -d --build

# 等待服务启动
echo "等待服务启动..."
sleep 5

# 显示服务状态
echo ""
echo "=================================================="
echo "  服务状态"
echo "=================================================="
docker compose ps

echo ""
echo "=================================================="
echo "  访问地址"
echo "=================================================="
echo "前端: http://localhost:3000"
echo "后端: http://localhost:8065"
echo ""
echo "查看日志: docker compose logs -f"
echo "=================================================="
