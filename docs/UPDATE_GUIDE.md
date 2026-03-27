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
├── scripts/
│   ├── build-dataagent.sh       # 构建脚本
│   └── update_dataagent.sh      # 更新脚本 ✅
└── VERSION.txt
```

---

## 🔄 更新步骤（推荐使用更新脚本）

### 方式一：使用 update_dataagent.sh 脚本（推荐）

这是最简单、最安全的更新方式，脚本会自动完成所有步骤。

#### 步骤 1：检查脚本是否存在

```bash
# 检查 scripts 目录中是否有更新脚本
ls -la /data/DataAgent_*/scripts/update_dataagent.sh

# 或者从源码目录执行
ls -la /opt/DataAgent/engineering/update_dataagent.sh
```

#### 步骤 2：执行更新脚本

```bash
# 方式 A：使用部署包中的脚本
cd /data/DataAgent_*/scripts/
./update_dataagent.sh --package /data/DataAgent_20260310_172350.tar.gz

# 方式 B：使用源码目录的脚本
cd /opt/DataAgent/engineering/
./update_dataagent.sh --package /data/DataAgent_20260310_172350.tar.gz
```

#### 步骤 3：指定部署目录（可选）

如果部署目录不是默认的 `/data/dataagent`，可以指定：

```bash
./update_dataagent.sh \
  --package /data/DataAgent_20260310_172350.tar.gz \
  --deploy-dir /opt/dataagent
```

#### 步骤 4：验证更新

```bash
# 1. 检查服务状态
systemctl status dataagent

# 2. 检查后端进程
ps aux | grep dataagent | grep -v grep

# 3. 检查端口监听
netstat -tlnp | grep 8065

# 4. 检查健康端点
curl http://localhost:8065/actuator/health

# 5. 检查前端
curl http://localhost:8080/ | head -20

# 6. 查看服务日志
sudo journalctl -u dataagent -f --since "1 minute ago"
```

---

### 方式二：手动更新（仅当脚本不可用时）

如果更新脚本不可用，可以手动执行以下步骤。

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
```

#### 步骤 4：备份配置并替换文件

```bash
# 备份配置文件（重要！）
if [ -f "$DEPLOY_DIR/application.yml" ]; then
    cp $DEPLOY_DIR/application.yml $TEMP_DIR/backup_application.yml
fi
if [ -f "$DEPLOY_DIR/nginx-dataagent.conf" ]; then
    cp $DEPLOY_DIR/nginx-dataagent.conf $TEMP_DIR/backup_nginx.conf
fi

# 替换后端 JAR
cp $TEMP_DIR/DataAgent_*/backend/dataagent-backend.jar $DEPLOY_DIR/

# 替换前端文件
rm -rf $DEPLOY_DIR/frontend/*
cp -r $TEMP_DIR/DataAgent_*/frontend/* $DEPLOY_DIR/frontend/

# 恢复配置
if [ -f "$TEMP_DIR/backup_application.yml" ]; then
    cp $TEMP_DIR/backup_application.yml $DEPLOY_DIR/
fi
if [ -f "$TEMP_DIR/backup_nginx.conf" ]; then
    cp $TEMP_DIR/backup_nginx.conf $DEPLOY_DIR/
fi
```

#### 步骤 5：启动服务并验证

```bash
# 启动后端服务
sudo systemctl start dataagent

# 等待 5 秒后验证
sleep 5

# 检查服务状态
systemctl status dataagent

# 清理临时文件
rm -rf $TEMP_DIR
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

# 重启 Nginx
sudo systemctl reload nginx

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
cp $BACKUP_DIR/dataagent-backend.jar $DEPLOY_DIR/
rm -rf $DEPLOY_DIR/frontend
cp -r $BACKUP_DIR/frontend_old $DEPLOY_DIR/frontend

# 启动服务
sudo systemctl start dataagent
```

---

## 📋 检查清单

更新前确认：
- [ ] 已确认更新包路径和完整性
- [ ] 已确认 `update_dataagent.sh` 脚本存在
- [ ] 已通知相关用户即将停机更新

更新后确认：
- [ ] 后端服务正常运行
- [ ] 前端页面可访问
- [ ] 数据库连接正常
- [ ] API 接口响应正常
- [ ] 日志无异常错误

---

## 💡 提示

1. **配置保留**：`update_dataagent.sh` 会自动备份和恢复配置文件，确保数据库连接等配置不丢失

2. **备份内容**：
   - `dataagent-backend.jar` - 后端 JAR 文件
   - `frontend/` - 前端文件目录
   - `application.yml` - 后端配置文件
   - `nginx-dataagent.conf` - Nginx 配置文件

3. **回滚方案**：每次更新都会自动创建带时间戳的备份，可随时回滚

4. **停机时间**：通常更新过程在 1-2 分钟内完成

5. **生产环境**：建议先在测试环境验证后再更新生产环境

6. **脚本位置**：更新脚本位于 `/opt/DataAgent/engineering/update_dataagent.sh`，打包后也会在 `scripts/` 目录中

---

## 📖 相关文档

- [编译与安装指南](DEPLOY_GUIDE.md) - 完整编译和部署流程
- [达梦数据库部署手册](DEPLOY_BY_DAMENG.md) - 达梦数据库环境下的部署
