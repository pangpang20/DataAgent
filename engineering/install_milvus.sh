#!/bin/bash

################################################################################
# Milvus 一键安装脚本 (二进制模式)
################################################################################
# 功能：
# 1. 自动检测环境 (Linux CentOS/Kylin)
# 2. 单机本地二进制部署 (Etcd, Minio, Milvus)
# 3. 支持指定已下载的安装包
# 4. 验证安装状态
#
# 使用方式：
#   ./install_milvus.sh
#   ./install_milvus.sh --etcd-package /path/to/etcd.tar.gz
#   ./install_milvus.sh --minio-package /path/to/minio --milvus-package /path/to/milvus.tar.gz
################################################################################

set -e

# ============================================================================
# 脚本目录
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ============================================================================
# 默认配置
# ============================================================================
MILVUS_VERSION="v2.6.7"
ETCD_VERSION="v3.5.25"
MINIO_VERSION="RELEASE.2024-12-18T13-15-44Z"

# 自定义安装包路径（可选）
ETCD_PACKAGE=""
MINIO_PACKAGE=""
MILVUS_PACKAGE=""
INSTALL_DIR="/data/milvus-local"

# 强制重新安装标志
FORCE_REINSTALL=false

# 架构检测
ARCH=""

# ============================================================================
# 颜色输出
# ============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

show_help() {
    cat <<EOF
${GREEN}Milvus 二进制一键安装脚本${NC}

用法:
    $0 [选项]

选项:
    --etcd-package PATH      指定 Etcd 安装包路径 (tar.gz 格式)
    --minio-package PATH     指定 Minio 安装包路径 (二进制文件)
    --milvus-package PATH    指定 Milvus 安装包路径 (rpm 格式)
    --force                  强制重新安装，删除现有安装和配置
    --help, -h               显示此帮助信息

示例:
    # 自动下载所有组件
    $0

    # 使用本地 Etcd 安装包
    $0 --etcd-package /path/to/etcd-v3.5.5-linux-amd64.tar.gz

    # 使用多个本地安装包
    $0 --etcd-package /path/to/etcd.tar.gz \\
       --minio-package /path/to/minio \\
       --milvus-package /path/to/milvus_2.6.7-1_amd64.rpm

说明:
    - 如果不指定安装包路径，脚本将自动从官方仓库下载
    - 自动检测系统架构 (x86_64/amd64 或 aarch64/arm64)
    - Etcd 需要 tar.gz 格式的压缩包
    - Minio 需要可执行的二进制文件
    - Milvus 需要 rpm 格式的安装包
    - 安装目录: ${INSTALL_DIR} (Etcd/Minio), /var/lib/milvus (Milvus)
    - 服务端口: Milvus(19530), Minio Console(9001)

EOF
    exit 0
}

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --etcd-package)
            ETCD_PACKAGE="$2"
            shift 2
            ;;
        --minio-package)
            MINIO_PACKAGE="$2"
            shift 2
            ;;
        --milvus-package)
            MILVUS_PACKAGE="$2"
            shift 2
            ;;
        --force)
            FORCE_REINSTALL=true
            shift 1
            ;;
        --help|-h)
            show_help
            ;;
        *)
            error "未知参数: $1 (使用 --help 查看帮助信息)"
            ;;
    esac
done

# ============================================================================
# 环境检测
# ============================================================================
detect_os() {
    OS_TYPE=$(uname -s)
    case "$OS_TYPE" in
        Linux*)
            if [ -f /etc/os-release ]; then
                . /etc/os-release
                OS=$ID
                info "检测到 Linux 操作系统: $OS $VERSION_ID"
            else
                OS="linux"
                info "检测到 Linux 操作系统"
            fi
            ;;
        *)
            error "仅支持 Linux 系统的二进制部署"
            ;;
    esac
}

# ============================================================================
# 架构检测
# ============================================================================
detect_arch() {
    MACHINE_ARCH=$(uname -m)
    case "$MACHINE_ARCH" in
        x86_64)
            ARCH="amd64"
            info "检测到架构: x86_64 (amd64)"
            ;;
        aarch64|arm64)
            ARCH="arm64"
            info "检测到架构: ARM64"
            ;;
        *)
            error "不支持的架构: $MACHINE_ARCH (仅支持 x86_64 和 aarch64/arm64)"
            ;;
    esac
}

# ============================================================================
# GCC 版本检查
# ============================================================================
check_gcc_version() {
    info "检查 GCC 版本..."
    
    # 检查 gcc-11 是否存在
    if ! command -v gcc-11 &> /dev/null; then
        error "未找到 gcc，请先安装 GCC 11 或更高版本"
    fi
    
    # 获取 gcc-11版本
    GCC_VERSION=$(gcc-11 -dumpversion | cut -d. -f1)
    
    if [[ -z "$GCC_VERSION" ]]; then
        error "无法获取 GCC 版本信息"
    fi
    
    info "当前 GCC 版本: $GCC_VERSION"
    
    # 检查版本是否 >= 11
    if [[ $GCC_VERSION -lt 11 ]]; then
        error "GCC 版本过低 (当前: $GCC_VERSION)，Milvus 需要 GCC 11 或更高版本。请升级 GCC 或设置 gcc-11 为默认编译器。"
    fi
    
    info "GCC 版本检查通过 (版本: $GCC_VERSION >= 11)"
}

# ============================================================================
# 强制卸载现有安装
# ============================================================================
force_uninstall() {
    if [[ "$FORCE_REINSTALL" != "true" ]]; then
        return 0
    fi
    
    warn "========================================="
    warn "  强制重新安装模式已启用"
    warn "========================================="
    
    # 停止并删除服务
    info "停止并删除现有服务..."
    
    if systemctl is-active --quiet etcd; then
        sudo systemctl stop etcd
    fi
    if systemctl is-enabled --quiet etcd &> /dev/null; then
        sudo systemctl disable etcd
    fi
    if [[ -f "/etc/systemd/system/etcd.service" ]]; then
        sudo rm -f /etc/systemd/system/etcd.service
        info "已删除 Etcd 服务配置"
    fi
    
    if systemctl is-active --quiet minio; then
        sudo systemctl stop minio
    fi
    if systemctl is-enabled --quiet minio &> /dev/null; then
        sudo systemctl disable minio
    fi
    if [[ -f "/etc/systemd/system/minio.service" ]]; then
        sudo rm -f /etc/systemd/system/minio.service
        info "已删除 Minio 服务配置"
    fi
    
    if systemctl is-active --quiet milvus; then
        sudo systemctl stop milvus
    fi
    if systemctl is-enabled --quiet milvus &> /dev/null; then
        sudo systemctl disable milvus
    fi
    
    # 卸载 Milvus RPM
    if rpm -q milvus &> /dev/null; then
        info "卸载 Milvus RPM 包..."
        sudo rpm -e milvus
        info "已卸载 Milvus"
    fi
    
    # 删除安装目录
    if [[ -d "$INSTALL_DIR" ]]; then
        info "删除安装目录 $INSTALL_DIR..."
        sudo rm -rf "$INSTALL_DIR"
        info "已删除安装目录"
    fi
    
    # 删除 Milvus 数据目录（可选，谨慎操作）
    if [[ -d "/var/lib/milvus" ]]; then
        warn "删除 Milvus 数据目录 /var/lib/milvus..."
        sudo rm -rf /var/lib/milvus
        info "已删除 Milvus 数据目录"
    fi
    
    sudo systemctl daemon-reload
    info "强制卸载完成"
    echo ""
}



# ============================================================================
# 本地二进制部署
# ============================================================================
deploy_milvus_local() {
    info "准备本地二进制部署 (Standalone 模式)..."
    
    mkdir -p "$INSTALL_DIR/bin" "$INSTALL_DIR/configs" "$INSTALL_DIR/data"
    cd "$INSTALL_DIR"

    # 1. 安装 etcd
    if [[ ! -f "bin/etcd" ]]; then
        if [[ -n "$ETCD_PACKAGE" && -f "$ETCD_PACKAGE" ]]; then
            info "使用指定的 Etcd 安装包: $ETCD_PACKAGE"
            cp "$ETCD_PACKAGE" etcd.tar.gz
        else
            info "下载 Etcd $ETCD_VERSION ($ARCH)..."
            ETCD_URL="https://github.com/etcd-io/etcd/releases/download/${ETCD_VERSION}/etcd-${ETCD_VERSION}-linux-${ARCH}.tar.gz"
            curl -L "$ETCD_URL" -o etcd.tar.gz || error "下载失败，请检查网络或手动下载"
        fi
        tar -zxf etcd.tar.gz
        mv etcd-${ETCD_VERSION}-linux-${ARCH}/etcd* bin/
        rm -rf etcd.tar.gz etcd-${ETCD_VERSION}-linux-${ARCH}
        info "Etcd 安装完成"
    else
        info "Etcd 已存在，跳过安装"
    fi

    # 2. 安装 Minio
    if [[ ! -f "bin/minio" ]]; then
        if [[ -n "$MINIO_PACKAGE" && -f "$MINIO_PACKAGE" ]]; then
            info "使用指定的 Minio 安装包: $MINIO_PACKAGE"
            cp "$MINIO_PACKAGE" bin/minio
        else
            info "下载 Minio ($ARCH)..."
            MINIO_URL="https://dl.min.io/server/minio/release/linux-${ARCH}/archive/minio.${MINIO_VERSION}"
            curl -L "$MINIO_URL" -o bin/minio || error "下载失败，请检查网络或手动下载"
        fi
        chmod +x bin/minio
        info "Minio 安装完成"
    else
        info "Minio 已存在，跳过安装"
    fi

    # 3. 安装 Milvus (使用 RPM 包)
    info "检查 Milvus 安装状态..."
    if ! rpm -q milvus &> /dev/null; then
        if [[ -n "$MILVUS_PACKAGE" && -f "$MILVUS_PACKAGE" ]]; then
            info "使用指定的 Milvus 安装包: $MILVUS_PACKAGE"
            sudo rpm -ivh "$MILVUS_PACKAGE"
        else
            info "下载 Milvus $MILVUS_VERSION RPM 包 ($ARCH)..."
            MILVUS_RPM="milvus_${MILVUS_VERSION#v}-1_${ARCH}.rpm"
            MILVUS_URL="https://github.com/milvus-io/milvus/releases/download/${MILVUS_VERSION}/${MILVUS_RPM}"
            curl -L "$MILVUS_URL" -o "$MILVUS_RPM" || error "下载失败，请检查网络或手动下载 RPM 包"
            sudo rpm -ivh "$MILVUS_RPM"
            rm -f "$MILVUS_RPM"
        fi
        info "Milvus 安装完成"
    else
        info "Milvus 已安装，跳过安装"
    fi

    # 4. 创建并启动 systemd 服务
    info "配置 systemd 服务..."
    
    # 创建 Etcd systemd 服务
    if [[ ! -f "/etc/systemd/system/etcd.service" ]]; then
        info "创建 Etcd systemd 服务..."
        sudo tee /etc/systemd/system/etcd.service > /dev/null <<EOF
[Unit]
Description=Etcd Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$INSTALL_DIR
ExecStart=$INSTALL_DIR/bin/etcd --data-dir $INSTALL_DIR/data/etcd
Restart=on-failure
RestartSec=5
StandardOutput=append:$INSTALL_DIR/etcd.log
StandardError=append:$INSTALL_DIR/etcd.log

[Install]
WantedBy=multi-user.target
EOF
        sudo systemctl daemon-reload
        info "Etcd 服务创建完成"
    else
        info "Etcd 服务已存在，跳过创建"
    fi
    
    # 创建 Minio systemd 服务
    if [[ ! -f "/etc/systemd/system/minio.service" ]]; then
        info "创建 Minio systemd 服务..."
        sudo tee /etc/systemd/system/minio.service > /dev/null <<EOF
[Unit]
Description=MinIO Object Storage
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$INSTALL_DIR
Environment="MINIO_ROOT_USER=minioadmin"
Environment="MINIO_ROOT_PASSWORD=minioadmin"
ExecStart=$INSTALL_DIR/bin/minio server $INSTALL_DIR/data/minio --console-address :9001
Restart=on-failure
RestartSec=5
StandardOutput=append:$INSTALL_DIR/minio.log
StandardError=append:$INSTALL_DIR/minio.log

[Install]
WantedBy=multi-user.target
EOF
        sudo systemctl daemon-reload
        info "Minio 服务创建完成"
    else
        info "Minio 服务已存在，跳过创建"
    fi
    
    # 启动 Etcd 服务
    if systemctl is-active --quiet etcd; then
        info "Etcd 服务已在运行"
    else
        info "启动 Etcd 服务..."
        sudo systemctl start etcd
        sudo systemctl enable etcd
        info "Etcd 服务已启动并设置为开机自启"
    fi
    
    # 启动 Minio 服务
    if systemctl is-active --quiet minio; then
        info "Minio 服务已在运行"
    else
        info "启动 Minio 服务..."
        sudo systemctl start minio
        sudo systemctl enable minio
        info "Minio 服务已启动并设置为开机自启"
    fi

    # 等待基础组件
    sleep 5

    # 配置 Milvus 配置文件
    info "配置 Milvus 连接本地 Etcd 和 Minio..."
    MILVUS_CONFIG="/etc/milvus/configs/milvus.yaml"
    if [[ -f "$MILVUS_CONFIG" ]]; then
        # 备份原始配置
        if [[ ! -f "${MILVUS_CONFIG}.bak" ]]; then
            sudo cp "$MILVUS_CONFIG" "${MILVUS_CONFIG}.bak"
            info "已备份原始配置到 ${MILVUS_CONFIG}.bak"
        fi
        
        # 修改 Etcd 配置
        if ! grep -q "endpoints: localhost:2379" "$MILVUS_CONFIG"; then
            sudo sed -i '/^etcd:/,/^[^ ]/ s|endpoints:.*|endpoints: localhost:2379|' "$MILVUS_CONFIG"
            info "已配置 Etcd 地址: localhost:2379"
        fi
        
        # 修改 Minio 配置
        if ! grep -q "address: localhost" "$MILVUS_CONFIG" | head -1; then
            sudo sed -i '/^minio:/,/^[^ ]/ s|address:.*|address: localhost|' "$MILVUS_CONFIG"
            sudo sed -i '/^minio:/,/^[^ ]/ s|port:.*|port: 9000|' "$MILVUS_CONFIG"
            info "已配置 Minio 地址: localhost:9000"
        fi
        
        if ! grep -q "accessKeyID: minioadmin" "$MILVUS_CONFIG"; then
            sudo sed -i '/^minio:/,/^[^ ]/ s|accessKeyID:.*|accessKeyID: minioadmin|' "$MILVUS_CONFIG"
            sudo sed -i '/^minio:/,/^[^ ]/ s|secretAccessKey:.*|secretAccessKey: minioadmin|' "$MILVUS_CONFIG"
            info "已配置 Minio 认证信息"
        fi
        
        # 配置日志路径
        sudo mkdir -p /var/log/milvus
        if ! grep -q "rootPath: /var/log/milvus" "$MILVUS_CONFIG"; then
            # 在配置文件末尾的 log 段落中查找并替换 rootPath
            # 使用更精确的匹配：在 log.file 部分查找 rootPath（注释行包含 "Root path to the log files"）
            sudo sed -i '/# Root path to the log files/,/maxBackups:/ s|^    rootPath:.*|    rootPath: /var/log/milvus|' "$MILVUS_CONFIG"
            info "已配置日志路径: /var/log/milvus"
        fi
        
        info "Milvus 配置文件已更新"
    else
        warn "未找到 Milvus 配置文件: $MILVUS_CONFIG"
    fi

    # 配置 Milvus systemd 服务环境变量
    info "配置 Milvus 服务环境变量..."
    if [[ -f "/lib/systemd/system/milvus.service" ]]; then
        MILVUS_SERVICE="/lib/systemd/system/milvus.service"
    elif [[ -f "/usr/lib/systemd/system/milvus.service" ]]; then
        MILVUS_SERVICE="/usr/lib/systemd/system/milvus.service"
    else
        warn "未找到 Milvus systemd 服务文件"
        MILVUS_SERVICE=""
    fi
    
    if [[ -n "$MILVUS_SERVICE" ]]; then
        # 检查是否已存在 LD_LIBRARY_PATH 配置
        if ! grep -q "LD_LIBRARY_PATH=/usr/local/gcc-11/lib64" "$MILVUS_SERVICE"; then
            # 在 [Service] 段落的 Environment 行后添加
            sudo sed -i '/^Environment=/a Environment="LD_LIBRARY_PATH=/usr/local/gcc-11/lib64:${LD_LIBRARY_PATH}"' "$MILVUS_SERVICE"
            sudo systemctl daemon-reload
            info "Milvus 服务环境变量配置完成"
        else
            info "Milvus 服务环境变量已配置"
        fi
    fi

    # 启动 Milvus (使用 systemd 服务)
    if systemctl is-active --quiet milvus; then
        info "Milvus 服务已在运行"
    else
        info "启动 Milvus 服务..."
        sudo systemctl start milvus
        sudo systemctl enable milvus
        info "Milvus 服务已启动并设置为开机自启"
    fi

    check_health
}

# ============================================================================
# 健康检查
# ============================================================================
check_health() {
    info "等待 Milvus 启动就绪..."
    MAX_RETRIES=30
    COUNT=0
    while [ $COUNT -lt $MAX_RETRIES ]; do
        if curl -s http://localhost:9091/healthz &> /dev/null; then
            info "Milvus 启动成功！"
            return 0
        fi
        echo -n "."
        sleep 5
        COUNT=$((COUNT + 1))
    done

    error "Milvus 启动超时，请检查日志或状态。"
}

# ============================================================================
# 主流程
# ============================================================================
main() {
    echo ""
    info "=========================================="
    info "  Milvus 二进制一键安装脚本"
    info "=========================================="
    echo ""

    detect_os
    detect_arch
    check_gcc_version
    force_uninstall
    deploy_milvus_local

    echo ""
    info "=========================================="
    info "  安装完成！"
    info "=========================================="
    info "Milvus 地址: localhost:19530"
    info "Minio Console: http://localhost:9001 (minioadmin/minioadmin)"
    info "=========================================="
}

main "$@"
