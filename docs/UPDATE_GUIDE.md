# DataAgent 更新指南

本文档介绍如何在已部署的 Linux 服务器上更新 DataAgent 前后端，**保留现有配置不修改**。

## 📋 更新前准备

### 1. 确认当前部署信息

```bash
# 1. 确认部署目录（通常是以下之一）
ls -la /data/dataagent/        # 常见部署目录
ls -la /opt/dataagent/         # 或者这个

# 2. 确认现有包
ls -lt /data/DataAgent_*.tar.gz | head -5

# 3. 确认当前运行的服务
systemctl status dataagent
ps aux | grep dataagent
```

### 2. 准备更新包

假设更新包为 `DataAgent_20260310_172350.tar.gz`，位于 `/data/` 目录下：

```bash
# 确认包存在
ls -lh /data/DataAgent_20260310_172350.tar.gz

# 查看包内容结构
tar -tzvf /data/DataAgent_20260310_172350.tar.gz | head -20
```

典型的包结构：
```
DataAgent_20260310_172350/
├── backend/
│   └── dataagent-backend.jar
├── frontend/
│   ├── index.html
│   └── assets/
├── config/
│   └── application.yml.template
└── scripts/
```

---

## 🔄 更新步骤

### 方式一：自动部署（推荐）

如果已有部署脚本，可直接使用：

```bash
# 使用现有的 build-dataagent.sh 脚本进行部署
cd /opt/DataAgent/engineering

# 使用现有配置进行部署（会保留原有配置）
./build-dataagent.sh \
  --deploy \
  --package-dir /data/DataAgent_20260310_172350 \
  --deploy-dir /data/dataagent
```

---

### 方式二：手动更新（详细步骤）

#### 步骤 1：备份当前版本

```bash
# 创建备份目录（带时间戳）
BACKUP_DIR="/data/dataagent_backup_$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR

# 备份当前部署目录
if [ -d "/data/dataagent" ]; then
    cp -r /data/dataagent $BACKUP_DIR/
    echo "✅ 备份完成：$BACKUP_DIR"
else
    echo "⚠️ 未发现现有部署目录"
fi
```

#### 步骤 2：停止服务

```bash
# 停止 systemd 服务
sudo systemctl stop dataagent

# 确认服务已停止
systemctl status dataagent

# 确认进程已退出（可选）
ps aux | grep dataagent | grep -v grep
```

#### 步骤 3：解压更新包

```bash
# 设置变量
NEW_PACKAGE="/data/DataAgent_20260310_172350.tar.gz"
DEPLOY_DIR="/data/dataagent"

# 解压新包到临时目录
TEMP_DIR="/tmp/dataagent_update_$$"
mkdir -p $TEMP_DIR
tar -xzvf $NEW_PACKAGE -C $TEMP_DIR

# 查看解压后的目录结构
ls -la $TEMP_DIR/
```

#### 步骤 4：替换后端文件

```bash
# 备份配置文件（重要！）
if [ -f "$DEPLOY_DIR/application.yml" ]; then
    cp $DEPLOY_DIR/application.yml $TEMP_DIR/backup_application.yml
    echo "✅ 配置文件已备份"
fi

# 替换后端 JAR
cp $TEMP_DIR/DataAgent_*/backend/dataagent-backend.jar $DEPLOY_DIR/
echo "✅ 后端 JAR 已替换"
```

#### 步骤 5：替换前端文件

```bash
# 替换前端文件
rm -rf $DEPLOY_DIR/frontend/*
cp -r $TEMP_DIR/DataAgent_*/frontend/* $DEPLOY_DIR/frontend/
echo "✅ 前端文件已替换"
```

#### 步骤 6：恢复配置

```bash
# 恢复原有的 application.yml
if [ -f "$TEMP_DIR/backup_application.yml" ]; then
    cp $TEMP_DIR/backup_application.yml $DEPLOY_DIR/application.yml
    echo "✅ 配置文件已恢复"
fi
```

#### 步骤 7：清理临时文件

```bash
# 删除临时解压目录
rm -rf $TEMP_DIR
```

#### 步骤 8：启动服务

```bash
# 启动后端服务
sudo systemctl start dataagent

# 查看服务状态
sudo systemctl status dataagent
```

#### 步骤 9：验证更新

```bash
# 1. 检查后端进程
ps aux | grep dataagent | grep -v grep

# 2. 检查端口监听
netstat -tlnp | grep 8065

# 3. 检查健康端点
curl http://localhost:8065/actuator/health

# 4. 检查前端
curl http://localhost:8080/ | head -20

# 5. 查看服务日志
sudo journalctl -u dataagent -f --since "1 minute ago"
```

---

## 📝 快速更新脚本

将以下内容保存为 `update_dataagent.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail

# 配置变量
PACKAGE_PATH="/data/DataAgent_20260310_172350.tar.gz"  # 修改为实际包路径
DEPLOY_DIR="/data/dataagent"                            # 修改为实际部署目录
BACKUP_BASE="/data/dataagent_backup"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

log "=== 开始更新 DataAgent ==="
log "更新包：$PACKAGE_PATH"
log "部署目录：$DEPLOY_DIR"

# 1. 备份当前版本
BACKUP_DIR="${BACKUP_BASE}_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"
if [ -d "$DEPLOY_DIR" ]; then
    cp -r "$DEPLOY_DIR" "$BACKUP_DIR/"
    log "✅ 备份完成：$BACKUP_DIR"
fi

# 2. 停止服务
log "停止服务..."
sudo systemctl stop dataagent
sleep 2

# 3. 解压新包
TEMP_DIR="/tmp/dataagent_update_$$"
mkdir -p "$TEMP_DIR"
tar -xzf "$PACKAGE_PATH" -C "$TEMP_DIR"
log "✅ 解压完成"

# 4. 备份并恢复配置
if [ -f "$DEPLOY_DIR/application.yml" ]; then
    cp "$DEPLOY_DIR/application.yml" "$TEMP_DIR/config_backup/"
    log "✅ 配置文件已备份"
fi

# 5. 替换文件
log "替换后端 JAR..."
cp "$TEMP_DIR"/DataAgent_*/backend/dataagent-backend.jar "$DEPLOY_DIR/"

log "替换前端文件..."
rm -rf "$DEPLOY_DIR/frontend"/*
cp -r "$TEMP_DIR"/DataAgent_*/frontend/* "$DEPLOY_DIR/frontend/"

# 6. 恢复配置
if [ -f "$TEMP_DIR/config_backup/application.yml" ]; then
    cp "$TEMP_DIR/config_backup/application.yml" "$DEPLOY_DIR/"
    log "✅ 配置文件已恢复"
fi

# 7. 清理
rm -rf "$TEMP_DIR"

# 8. 启动服务
log "启动服务..."
sudo systemctl start dataagent

# 9. 验证
sleep 5
if systemctl is-active --quiet dataagent; then
    log "✅ 服务启动成功"
    log "访问地址：http://$(hostname -I | awk '{print $1}'):8080"
else
    log "❌ 服务启动失败，请检查日志：journalctl -u dataagent -f"
    exit 1
fi

log "=== 更新完成 ==="
```

使用方法：

```bash
# 1. 创建脚本
vi /opt/DataAgent/update_dataagent.sh
# 粘贴上述脚本内容，修改 PACKAGE_PATH 和 DEPLOY_DIR

# 2. 赋予执行权限
chmod +x /opt/DataAgent/update_dataagent.sh

# 3. 执行更新
sudo /opt/DataAgent/update_dataagent.sh
```

---

## 🔧 故障排查

### 服务启动失败

```bash
# 查看详细日志
sudo journalctl -u dataagent -f

# 检查端口占用
lsof -i:8065

# 检查配置文件
cat $DEPLOY_DIR/application.yml | grep -A5 "datasource"
```

### 前端无法访问

```bash
# 检查 Nginx 状态
sudo systemctl status nginx

# 检查 Nginx 配置
sudo nginx -t

# 检查前端文件权限
ls -la $DEPLOY_DIR/frontend/
```

### 回滚到旧版本

```bash
# 从备份恢复
BACKUP_DIR="/data/dataagent_backup_YYYYMMDD_HHMMSS"  # 替换为实际备份目录

# 停止服务
sudo systemctl stop dataagent

# 恢复文件
rm -rf $DEPLOY_DIR/*
cp -r $BACKUP_DIR/dataagent/* $DEPLOY_DIR/

# 启动服务
sudo systemctl start dataagent
```

---

## 📋 检查清单

更新前确认：
- [ ] 已确认更新包路径和完整性
- [ ] 已备份现有部署和配置
- [ ] 已通知相关用户即将停机更新

更新后确认：
- [ ] 后端服务正常运行
- [ ] 前端页面可访问
- [ ] 数据库连接正常
- [ ] API 接口响应正常
- [ ] 日志无异常错误

---

## 💡 提示

1. **配置保留**：更新过程中，`application.yml` 配置文件会被自动备份和恢复，确保数据库连接等配置不丢失

2. **回滚方案**：每次更新都会自动创建带时间戳的备份，可随时回滚

3. **停机时间**：通常更新过程在 1-2 分钟内完成

4. **生产环境**：建议先在测试环境验证后再更新生产环境
