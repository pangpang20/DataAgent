#!/usr/bin/env bash

set -euo pipefail

################################################################################
# DataAgent 更新脚本
# 功能：更新已部署的前后端，保留配置文件，不修改 nginx 配置
################################################################################

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $*" >&2
    exit 1
}

# ============================================================================
# 默认配置
# ============================================================================
PACKAGE_PATH=""
DEPLOY_DIR="/data/dataagent"
BACKUP_BASE="/data/dataagent_backup"

# ============================================================================
# 显示帮助信息
# ============================================================================
show_help() {
    cat <<EOF
DataAgent 更新脚本 - 仅更新前后端，保留配置文件

使用方法:
  ./update_dataagent.sh --package <tar.gz 包路径> --deploy-dir <部署目录>

参数:
  --package <path>        更新包路径 (必需)，如：/data/DataAgent_20260310_172350.tar.gz
  --deploy-dir <path>     部署目录 (默认：/data/dataagent)
  --backup-dir <path>     备份目录前缀 (默认：/data/dataagent_backup)
  --help                  显示此帮助信息

示例:
  # 使用默认部署目录
  ./update_dataagent.sh --package /data/DataAgent_20260310_172350.tar.gz

  # 指定部署目录
  ./update_dataagent.sh --package /data/DataAgent_20260310_172350.tar.gz --deploy-dir /opt/dataagent

EOF
    exit 0
}

# ============================================================================
# 解析命令行参数
# ============================================================================
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --package)
                PACKAGE_PATH="$2"
                shift 2
                ;;
            --deploy-dir)
                DEPLOY_DIR="$2"
                shift 2
                ;;
            --backup-dir)
                BACKUP_BASE="$2"
                shift 2
                ;;
            --help)
                SHOW_HELP=true
                shift
                ;;
            -*)
                error "未知参数：$1，使用 --help 查看帮助"
                ;;
            *)
                error "未知参数：$1"
                ;;
        esac
    done

    if [ -z "$PACKAGE_PATH" ]; then
        error "必须指定 --package 参数"
    fi

    if [ ! -f "$PACKAGE_PATH" ]; then
        error "更新包不存在：$PACKAGE_PATH"
    fi
}

# ============================================================================
# 主流程
# ============================================================================

parse_arguments "$@"

log "=== 开始更新 DataAgent ==="
log "更新包：$PACKAGE_PATH"
log "部署目录：$DEPLOY_DIR"

# ----------------------------------------------------------------------------
# 步骤 1：检查环境
# ----------------------------------------------------------------------------
log "检查环境..."

if ! command -v systemctl &> /dev/null; then
    error "systemctl 不可用，请确认系统支持 systemd"
fi

# 检查部署目录是否存在
if [ ! -d "$DEPLOY_DIR" ]; then
    error "部署目录不存在：$DEPLOY_DIR"
fi

# 检查后端是否正在运行
if systemctl is-active --quiet dataagent 2>/dev/null; then
    log "后端服务正在运行"
else
    log "警告：后端服务未运行，更新后将启动"
fi

# ----------------------------------------------------------------------------
# 步骤 2：创建备份
# ----------------------------------------------------------------------------
BACKUP_DIR="${BACKUP_BASE}_$(date +%Y%m%d_%H%M%S)"
log "创建备份目录：$BACKUP_DIR"
mkdir -p "$BACKUP_DIR"

# 备份后端 JAR
if [ -f "$DEPLOY_DIR/dataagent-backend.jar" ]; then
    log "备份后端 JAR..."
    cp "$DEPLOY_DIR/dataagent-backend.jar" "$BACKUP_DIR/"
else
    log "警告：未找到后端 JAR 文件"
fi

# 备份前端文件
if [ -d "$DEPLOY_DIR/frontend" ]; then
    log "备份前端文件..."
    cp -r "$DEPLOY_DIR/frontend" "$BACKUP_DIR/frontend_old"
else
    log "警告：未找到前端目录"
fi

# 备份配置文件（仅备份，不恢复）
if [ -f "$DEPLOY_DIR/application.yml" ]; then
    cp "$DEPLOY_DIR/application.yml" "$BACKUP_DIR/"
    log "备份配置文件 application.yml"
fi

# 备份 Nginx 配置
if [ -f "$DEPLOY_DIR/nginx-dataagent.conf" ]; then
    cp "$DEPLOY_DIR/nginx-dataagent.conf" "$BACKUP_DIR/"
    log "备份 Nginx 配置 nginx-dataagent.conf"
fi

log "✅ 备份完成：$BACKUP_DIR"

# ----------------------------------------------------------------------------
# 步骤 3：解压更新包
# ----------------------------------------------------------------------------
log "解压更新包..."
TEMP_DIR="/tmp/dataagent_update_$$"
mkdir -p "$TEMP_DIR"

# 解压 tar.gz 包
tar -xzf "$PACKAGE_PATH" -C "$TEMP_DIR"

# 获取解压后的目录名（通常是 DataAgent_版本号）
PACKAGE_DIR=$(find "$TEMP_DIR" -maxdepth 1 -type d -name "DataAgent_*" | head -n1)
if [ -z "$PACKAGE_DIR" ]; then
    error "更新包格式不正确，未找到 DataAgent_* 目录"
fi

log "✅ 解压完成：$PACKAGE_DIR"

# ----------------------------------------------------------------------------
# 步骤 4：停止服务
# ----------------------------------------------------------------------------
log "停止后端服务..."
systemctl stop dataagent || true
sleep 2

# 确认服务已停止
if systemctl is-active --quiet dataagent 2>/dev/null; then
    error "后端服务未能停止"
fi

log "✅ 后端服务已停止"

# ----------------------------------------------------------------------------
# 步骤 5：替换后端 JAR
# ----------------------------------------------------------------------------
NEW_JAR=$(find "$PACKAGE_DIR/backend" -name "*.jar" | head -n1)
if [ -z "$NEW_JAR" ]; then
    error "更新包中未找到后端 JAR 文件"
fi

log "替换后端 JAR: $(basename $NEW_JAR)"
cp "$NEW_JAR" "$DEPLOY_DIR/dataagent-backend.jar"
log "✅ 后端 JAR 已替换"

# ----------------------------------------------------------------------------
# 步骤 6：替换前端文件
# ----------------------------------------------------------------------------
if [ -d "$PACKAGE_DIR/frontend" ]; then
    log "替换前端文件..."
    rm -rf "$DEPLOY_DIR/frontend"
    mkdir -p "$DEPLOY_DIR/frontend"
    cp -r "$PACKAGE_DIR/frontend/"* "$DEPLOY_DIR/frontend/"
    log "✅ 前端文件已替换"
else
    log "警告：更新包中未找到前端目录，跳过前端更新"
fi

# ----------------------------------------------------------------------------
# 步骤 7：清理临时文件
# ----------------------------------------------------------------------------
log "清理临时文件..."
rm -rf "$TEMP_DIR"

# ----------------------------------------------------------------------------
# 步骤 8：启动后端服务
# ----------------------------------------------------------------------------
log "启动后端服务..."
systemctl start dataagent

# 等待服务启动
log "等待后端服务启动..."
MAX_WAIT=60
for i in $(seq 1 $MAX_WAIT); do
    if systemctl is-active --quiet dataagent 2>/dev/null; then
        log "✅ 后端服务已启动"
        break
    fi
    if [ $i -eq $MAX_WAIT ]; then
        error "后端服务启动超时，请检查日志：journalctl -u dataagent -f"
    fi
    sleep 1
done

# ----------------------------------------------------------------------------
# 步骤 9：重新加载 Nginx
# ----------------------------------------------------------------------------
if systemctl is-active --quiet nginx 2>/dev/null; then
    log "重新加载 Nginx..."
    if nginx -t 2>/dev/null; then
        systemctl reload nginx
        log "✅ Nginx 已重新加载"
    else
        log "警告：Nginx 配置测试失败，跳过重新加载"
    fi
else
    log "Nginx 未运行，跳过重新加载"
fi

# ----------------------------------------------------------------------------
# 步骤 10：显示摘要
# ----------------------------------------------------------------------------
echo ""
log "=========================================="
log "  更新完成！"
log "=========================================="
echo ""
log "备份目录：$BACKUP_DIR"
log "部署目录：$DEPLOY_DIR"
echo ""
log "服务状态:"
systemctl status dataagent --no-pager -l | head -10
echo ""

# 获取本机 IP
HOST_IP=$(hostname -I | awk '{print $1}')
if [ -z "$HOST_IP" ]; then
    HOST_IP=$(ip route get 1 2>/dev/null | awk '{print $7}' | head -n1)
fi
if [ -z "$HOST_IP" ]; then
    HOST_IP="localhost"
fi

log "访问地址:"
log "  后端：http://$HOST_IP:8065"
log "  前端：http://$HOST_IP:8080"
echo ""
log "如需回滚，执行:"
log "  # 停止服务"
log "  systemctl stop dataagent"
log "  # 恢复备份"
log "  cp $BACKUP_DIR/dataagent-backend.jar $DEPLOY_DIR/"
log "  rm -rf $DEPLOY_DIR/frontend && cp -r $BACKUP_DIR/frontend_old $DEPLOY_DIR/frontend"
log "  # 启动服务"
log "  systemctl start dataagent"
echo ""
log "=========================================="
