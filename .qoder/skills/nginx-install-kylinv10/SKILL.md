---
name: nginx-install-kylinv10
description: 在KylinV10操作系统上安装和配置Nginx，支持离线和在线安装模式。使用当需要在KylinV10系统上部署Nginx服务器时。
---

# KylinV10 Nginx 安装技能

## 概述

本技能用于在麒麟V10 (KylinV10) 操作系统上安装和配置Nginx服务器，支持在线和离线两种安装模式。该技能涵盖从环境检查到服务配置的完整流程。

## 安装前准备

### 1. 系统检查
- 操作系统：KylinV10 (x86_64 或 arm64)
- 系统架构：x86_64 或 aarch64
- 磁盘空间：至少 500MB 可用空间
- 内存：至少 512MB
- 网络：在线安装需要网络连接

### 2. 权限要求
- 需要 root 权限或 sudo 权限

## 在线安装模式

### 1. 更新系统包管理器

```bash
# 更新包管理器缓存
sudo apt update

# 或者对于 KylinV10 基于 Red Hat 的版本
sudo yum update
```

### 2. 安装 Nginx

```bash
# KylinV10 基于 Debian/Ubuntu 系统
sudo apt install -y nginx

# 或者对于 KylinV10 基于 Red Hat/CentOS 系统
sudo yum install -y nginx

# 或者使用 dnf (较新的版本)
sudo dnf install -y nginx
```

### 3. 验证安装

```bash
# 检查 Nginx 版本
nginx -v

# 检查 Nginx 配置语法
nginx -t
```

## 离线安装模式

### 1. 准备安装包

从有网络连接的机器下载 Nginx 安装包及其依赖：

```bash
# 下载 Nginx 包
# 对于基于 Debian/Ubuntu 的 KylinV10
apt download nginx

# 或者从官网下载对应架构的包
wget http://nginx.org/packages/rhel/7/x86_64/RPMS/nginx-1.20.2-1.el7.ngx.x86_64.rpm
```

### 2. 传输安装包到目标系统

使用 USB 设备、SCP 或其他方式将安装包传输到目标系统。

### 3. 安装 Nginx 离线包

```bash
# 对于基于 Debian/Ubuntu 的 KylinV10
sudo dpkg -i nginx_*.deb

# 对于基于 Red Hat/CentOS 的 KylinV10
sudo rpm -ivh nginx-*.rpm
```

### 4. 处理依赖关系

如果出现依赖问题，下载并安装所需的依赖包：

```bash
# 检查缺失的依赖
ldd $(which nginx) | grep "not found"

# 安装缺失的依赖包
sudo dpkg -i dependency_package.deb  # Debian/Ubuntu
sudo rpm -ivh dependency_package.rpm  # Red Hat/CentOS
```

## Nginx 服务管理

### 启动 Nginx 服务

```bash
# 使用 systemd
sudo systemctl start nginx
sudo systemctl enable nginx

# 或者直接启动
sudo nginx
```

### 检查服务状态

```bash
# 检查 Nginx 服务状态
sudo systemctl status nginx

# 检查进程
ps aux | grep nginx
```

### 停止和重启服务

```bash
# 停止服务
sudo systemctl stop nginx

# 重启服务
sudo systemctl restart nginx

# 重新加载配置（不中断连接）
sudo systemctl reload nginx
```

## 配置 Nginx

### 1. 主配置文件位置

- `/etc/nginx/nginx.conf` - 主配置文件
- `/etc/nginx/conf.d/` - 额外配置文件目录
- `/etc/nginx/sites-available/` - 可用站点配置
- `/etc/nginx/sites-enabled/` - 启用的站点配置

### 2. 基本配置示例

创建一个基本的配置文件 `/etc/nginx/conf.d/default.conf`：

```nginx
server {
    listen 80;
    server_name localhost;
    
    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
    }
    
    error_page 500 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
```

### 3. 验证配置

```bash
# 测试配置语法
sudo nginx -t

# 重新加载配置
sudo nginx -s reload
```

## 防火墙配置

### 配置 UFW (如果使用)

```bash
# 允许 HTTP
sudo ufw allow 'Nginx Full'

# 或者允许特定端口
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

### 配置 Firewalld (如果使用)

```bash
# 允许 HTTP 服务
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https

# 重载防火墙规则
sudo firewall-cmd --reload
```

## 测试 Nginx 安装

### 1. 本地测试

```bash
# 检查端口监听
sudo netstat -tlnp | grep :80

# 或者使用 ss 命令
sudo ss -tlnp | grep :80
```

### 2. Web 测试

```bash
# 使用 curl 测试
curl http://localhost

# 或者在浏览器中访问
# http://<服务器IP>
```

## 常见问题排查

### 1. 端口冲突

```bash
# 检查端口占用
sudo netstat -tlnp | grep :80

# 查找占用进程
sudo lsof -i :80
```

### 2. 权限问题

```bash
# 检查 Nginx 用户
ps aux | grep nginx

# 检查配置文件权限
ls -la /etc/nginx/
```

### 3. 配置错误

```bash
# 检查错误日志
sudo tail -f /var/log/nginx/error.log

# 检查访问日志
sudo tail -f /var/log/nginx/access.log
```

## 输出部署文档

将以下内容保存为 `nginx-deployment-guide.md` 到项目根目录：

```markdown
# Nginx 部署文档

## 概述

本文档介绍如何在 KylinV10 系统上部署 Nginx 服务器，支持在线和离线两种安装模式。

## 安装模式

### 在线安装

1. 更新系统包管理器
   ```bash
   sudo apt update  # Debian/Ubuntu
   # 或
   sudo yum update  # Red Hat/CentOS
   ```

2. 安装 Nginx
   ```bash
   sudo apt install -y nginx  # Debian/Ubuntu
   # 或
   sudo yum install -y nginx  # Red Hat/CentOS
   ```

### 离线安装

1. 准备安装包
   - 从有网络的机器下载 nginx 包
   - 传输到目标机器

2. 安装
   ```bash
   sudo dpkg -i nginx_*.deb  # Debian/Ubuntu
   # 或
   sudo rpm -ivh nginx-*.rpm  # Red Hat/CentOS
   ```

## 服务管理

```bash
# 启动服务
sudo systemctl start nginx
sudo systemctl enable nginx

# 检查状态
sudo systemctl status nginx
```

## 配置文件

- 主配置：`/etc/nginx/nginx.conf`
- 站点配置：`/etc/nginx/conf.d/`

## 测试

访问 `http://<服务器IP>` 验证 Nginx 是否正常运行。
```

## 维护和升级

### 备份配置

```bash
# 备份整个配置目录
sudo cp -r /etc/nginx /etc/nginx.backup.$(date +%Y%m%d)
```

### 升级 Nginx

```bash
# 在线升级
sudo apt update && sudo apt upgrade nginx  # Debian/Ubuntu
sudo yum update nginx  # Red Hat/CentOS
```

## 安全建议

1. 定期更新 Nginx 以获取安全补丁
2. 配置适当的安全头部
3. 限制不必要的访问
4. 定期检查日志文件

## 参考资料

- Nginx 官方文档：http://nginx.org/en/docs/
- KylinV10 官方文档：https://www.kylinos.cn/