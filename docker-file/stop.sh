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
echo "  停止 Audaque DataAgent 服务"
echo "=================================================="

# 停止所有容器
echo "正在停止容器..."
docker compose down

# 显示剩余容器
echo ""
echo "=================================================="
echo "  剩余容器"
echo "=================================================="
docker compose ps

echo ""
echo "=================================================="
echo "  服务已停止"
echo "=================================================="
echo "如需清理所有数据卷，请执行: docker compose down -v"
echo "如需清理未使用的镜像，请执行: docker system prune -a"
echo "=================================================="
