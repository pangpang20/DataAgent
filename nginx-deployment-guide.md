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