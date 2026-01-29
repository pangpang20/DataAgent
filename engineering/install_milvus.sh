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
    - 安装目录: ./milvus-local (Etcd/Minio), /var/lib/milvus (Milvus)
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
# 本地二进制部署
# ============================================================================
deploy_milvus_local() {
    info "准备本地二进制部署 (Standalone 模式)..."
    INSTALL_DIR="$(pwd)/milvus-local"
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

    # 4. 启动服务 (后台运行)
    info "启动后台服务..."
    
    # 启动 Etcd
    if ! pgrep -f "etcd --data-dir" > /dev/null; then
        nohup ./bin/etcd --data-dir ./data/etcd > etcd.log 2>&1 &
        echo $! > etcd.pid
        info "Etcd 已启动"
    else
        info "Etcd 已在运行"
    fi
    
    # 启动 Minio
    if ! pgrep -f "minio server" > /dev/null; then
        export MINIO_ROOT_USER=minioadmin
        export MINIO_ROOT_PASSWORD=minioadmin
        nohup ./bin/minio server ./data/minio --console-address ":9001" > minio.log 2>&1 &
        echo $! > minio.pid
        info "Minio 已启动"
    else
        info "Minio 已在运行"
    fi

    # 等待基础组件
    sleep 5

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
