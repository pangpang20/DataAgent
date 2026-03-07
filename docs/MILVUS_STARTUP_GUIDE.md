# Milvus 服务启动与自启动配置指南

本文档介绍如何在服务器重启后启动 Milvus 服务，以及配置开机自启动。

## 手动启动 Milvus

服务器重启后，需要手动启动 Milvus 服务：

```bash
# 进入 docker-compose.yml 所在目录
cd /data/docker_milvus   # 根据实际路径调整

# 确认当前目录正确，里面有 docker-compose.yml
pwd

# 启动 Milvus 服务
docker compose up -d
# 注意：新版 Docker 使用 docker compose（空格）
# 旧版 Docker 使用 docker-compose up -d
```

## 配置开机自启动

### 1. 创建 systemd 服务文件

创建一个名为 `milvus.service` 的 systemd 服务文件：

```bash
sudo nano /etc/systemd/system/milvus.service
```

### 2. 配置服务内容

将以下内容完整复制粘贴到文件中（注意修改 `WorkingDirectory` 为实际路径）：

```ini
[Unit]
Description=Milvus Standalone with Docker Compose
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/data/docker_milvus          # ← 改成你的 docker-compose.yml 所在目录！
ExecStart=/usr/bin/docker compose up -d       # 注意：新版 docker 用 docker compose（空格），旧版可能是 docker-compose
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=300                           # 启动超时，Milvus 启动慢可以调大

[Install]
WantedBy=multi-user.target
```

以下是参考：

```ini
[Unit]
Description=Milvus Standalone with Docker Compose
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/docker_milvus
ExecStart=/usr/bin/docker-compose up -d
ExecStop=/usr/bin/docker-compose down
TimeoutStartSec=300

[Install]
WantedBy=multi-user.target 
```

**重要说明：**

- `WorkingDirectory`：必须是 `docker-compose.yml` 所在的绝对路径，否则会找不到文件
- `ExecStart`：如果你的系统是旧版 Docker（命令是 `docker-compose` 而非 `docker compose`），改成：
  ```ini
  ExecStart=/usr/local/bin/docker-compose up -d
  ExecStop=/usr/local/bin/docker-compose down
  ```
- 可以使用 `which docker-compose` 或 `which docker` 检查命令路径

保存退出：`Ctrl+O` → `Enter` → `Ctrl+X`

### 3. 重新加载 systemd 配置

```bash
sudo systemctl daemon-reload
```

### 4. 启用开机自启动

```bash
sudo systemctl enable milvus.service
```

## 常用服务管理命令

```bash
# 查看服务状态
sudo systemctl status milvus.service

# 手动启动服务
sudo systemctl start milvus.service

# 手动停止服务
sudo systemctl stop milvus.service

# 禁用开机自启动
sudo systemctl disable milvus.service

# 查看服务日志
sudo journalctl -u milvus.service
```

## 验证自启动配置

配置完成后，可以通过以下方式验证：

1. 检查服务是否已启用：
   ```bash
   systemctl is-enabled milvus.service
   ```

2. 重启服务器验证：
   ```bash
   sudo reboot
   ```
   重启后检查 Milvus 是否自动启动：
   ```bash
   docker ps | grep milvus
   ```
