#!/bin/bash

################################################################################
# Milvus 一键安装脚本 (Docker 模式)
################################################################################
# 功能：
# 1. 自动检测环境 (Linux CentOS/Kylin, Windows Git Bash/WSL)
# 2. 支持 Docker 部署或单机本地二进制部署 (通过参数控制)
# 3. 自动安装 Docker 和 Docker Compose (仅 Linux 且模式为 docker 时)
# 4. 部署 Milvus Standalone (Etcd, Minio, Milvus)
# 5. 验证安装状态
#
# 使用方式：
#   ./install_milvus.sh --mode docker   (默认，使用容器)
#   ./install_milvus.sh --mode local    (单机本地二进制安装，仅限 Linux)
################################################################################

set -e

# ============================================================================
# 脚本目录
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ============================================================================
# 默认配置
# ============================================================================
INSTALL_MODE="docker"  # 默认安装模式
MILVUS_VERSION="v2.5.0"
ETCD_VERSION="v3.5.5"
MINIO_VERSION="RELEASE.2023-03-20T20-16-18Z"

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --mode)
            INSTALL_MODE="$2"
            shift 2
            ;;
        *)
            error "未知参数: $1"
            ;;
    esac
done

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
        Darwin*)
            OS="macos"
            info "检测到 macOS"
            ;;
        MINGW*|MSYS*|CYGWIN*)
            OS="windows"
            info "检测到 Windows 环境 (Git Bash/MSYS)"
            ;;
        *)
            OS="unknown"
            warn "未知操作系统类型: $OS_TYPE"
            ;;
    esac
}

# ============================================================================
# 检查并安装 Docker (Linux 专用)
# ============================================================================
install_docker_linux() {
    if command -v docker &> /dev/null; then
        info "Docker 已安装: $(docker -v)"
        return 0
    fi

    warn "Docker 未安装，尝试自动安装..."
    if [[ "$OS" == "centos" || "$OS" == "rhel" || "$OS" == "kylin" ]]; then
        sudo yum install -y yum-utils
        sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
        sudo yum install -y docker-ce docker-ce-cli containerd.io
        sudo systemctl start docker
        sudo systemctl enable docker
        info "Docker 安装完成"
    elif [[ "$OS" == "ubuntu" || "$OS" == "debian" ]]; then
        sudo apt-get update
        sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
        sudo apt-get update
        sudo apt-get install -y docker-ce docker-ce-cli containerd.io
        sudo systemctl start docker
        sudo systemctl enable docker
        info "Docker 安装完成"
    else
        error "不支持自动安装 Docker 的系统: $OS，请手动安装 Docker 后重试"
    fi
}

# ============================================================================
# 检查并安装 Docker Compose
# ============================================================================
install_docker_compose() {
    if docker compose version &> /dev/null; then
        DOCKER_COMPOSE_CMD="docker compose"
        info "检测到 Docker Compose 插件"
        return 0
    elif command -v docker-compose &> /dev/null; then
        DOCKER_COMPOSE_CMD="docker-compose"
        info "检测到 docker-compose 独立程序"
        return 0
    fi

    if [[ "$OS" == "windows" || "$OS" == "macos" ]]; then
        error "未检测到 Docker Compose，请确保已安装 Docker Desktop 并启用了 Compose 组件"
    fi

    warn "Docker Compose 未安装，尝试下载安装..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    DOCKER_COMPOSE_CMD="docker-compose"
    info "Docker Compose 安装完成"
}

# ============================================================================
# 部署 Milvus
# ============================================================================
deploy_milvus() {
    info "准备部署 Milvus Standalone..."
    
    # 创建配置目录
    mkdir -p milvus-deploy
    cd milvus-deploy

    # 使用配置模板（SCRIPT_DIR 已经是项目根目录）
    MILVUS_TEMPLATE="$SCRIPT_DIR/docker-file/config/milvus-docker-compose.yml"
    
    if [ -f "$MILVUS_TEMPLATE" ]; then
        info "复制 Milvus Docker Compose 配置模板..."
        cp "$MILVUS_TEMPLATE" docker-compose.yml
        info "已使用模板: $MILVUS_TEMPLATE"
    else
        warn "未找到模板文件: $MILVUS_TEMPLATE"
        warn "使用默认配置"
        # 生成 docker-compose.yml（备用方案）
        cat <<EOF > docker-compose.yml
version: '3.5'

services:
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.5
    environment:
      - ETCD_AUTO_COMPACTION_RETENTION_RENDER_BY_KEY=1
      - ETCD_AUTO_COMPACTION_RETENTION=1
    volumes:
      - \${DOCKER_VOLUME_DIRECTORY:-.}/volumes/etcd:/etcd
    command: etcd -advertise-client-urls http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    ports:
      - "9001:9001"
      - "9000:9000"
    volumes:
      - \${DOCKER_VOLUME_DIRECTORY:-.}/volumes/minio:/export
    command: minio server /export --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  standalone:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.5.0
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - \${DOCKER_VOLUME_DIRECTORY:-.}/volumes/milvus:/var/lib/milvus
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      timeout: 20s
      retries: 3
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - "etcd"
      - "minio"

networks:
  default:
    name: milvus
EOF
    fi

    info "启动容器 (可能需要几分钟下载镜像)..."
    $DOCKER_COMPOSE_CMD up -d

    check_health
}

# ============================================================================
# 本地二进制部署 (仅限 Linux)
# ============================================================================
deploy_milvus_local() {
    if [[ "$OS" == "windows" ]]; then
        error "单机本地二进制模式 (--mode local) 暂不支持直接在 Windows 上运行，请使用 Docker 模式或在 WSL 中执行。"
    fi

    info "准备本地二进制部署 (Standalone 模式)..."
    INSTALL_DIR="$(pwd)/milvus-local"
    mkdir -p "$INSTALL_DIR/bin" "$INSTALL_DIR/configs" "$INSTALL_DIR/data"
    cd "$INSTALL_DIR"

    # 1. 下载 etcd
    if [[ ! -f "bin/etcd" ]]; then
        info "下载 Etcd $ETCD_VERSION..."
        ETCD_URL="https://github.com/etcd-io/etcd/releases/download/${ETCD_VERSION}/etcd-${ETCD_VERSION}-linux-amd64.tar.gz"
        curl -L "$ETCD_URL" -o etcd.tar.gz
        tar -zxf etcd.tar.gz
        mv etcd-${ETCD_VERSION}-linux-amd64/etcd* bin/
        rm -rf etcd.tar.gz etcd-${ETCD_VERSION}-linux-amd64
    fi

    # 2. 下载 Minio
    if [[ ! -f "bin/minio" ]]; then
        info "下载 Minio..."
        curl -L "https://dl.min.io/server/minio/release/linux-amd64/archive/minio.${MINIO_VERSION}" -o bin/minio
        chmod +x bin/minio
    fi

    # 3. 下载 Milvus
    if [[ ! -f "bin/milvus" ]]; then
        info "下载 Milvus $MILVUS_VERSION..."
        # 注意：这里下载的是 standalone 预编译二进制文件
        MILVUS_URL="https://github.com/milvus-io/milvus/releases/download/${MILVUS_VERSION}/milvus-standalone-linux-amd64.tar.gz"
        if curl -I "$MILVUS_URL" 2>&1 | grep -q "404"; then
            warn "官方可能未提供该版本的预编译 standalone 二进制包，尝试下载通用包..."
            # 如果不存在 standalone 专用包，则可能需要手动配置。此处仅作为示例逻辑
        fi
        curl -L "$MILVUS_URL" -o milvus.tar.gz || warn "下载失败，请手动下载并放入 bin 目录"
        if [[ -f milvus.tar.gz ]]; then
            tar -zxf milvus.tar.gz
            mv milvus bin/
            rm -rf milvus.tar.gz
        fi
    fi

    # 4. 启动服务 (后台运行)
    info "启动后台服务..."
    
    # 启动 Etcd
    nohup ./bin/etcd --data-dir ./data/etcd > etcd.log 2>&1 &
    echo $! > etcd.pid
    
    # 启动 Minio
    export MINIO_ROOT_USER=minioadmin
    export MINIO_ROOT_PASSWORD=minioadmin
    nohup ./bin/minio server ./data/minio --console-address ":9001" > minio.log 2>&1 &
    echo $! > minio.pid

    # 等待基础组件
    sleep 5

    # 启动 Milvus
    # 注意：本地模式通常需要 milvus.yaml 配置文件，此处假设使用默认配置或已存在
    if [[ -f "bin/milvus" ]]; then
        nohup ./bin/milvus run standalone > milvus.log 2>&1 &
        echo $! > milvus.pid
    else
        error "未发现 Milvus 二进制文件，请确保已下载并放置在 $INSTALL_DIR/bin/milvus"
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
    info "  Milvus 一键安装脚本"
    info "=========================================="
    echo ""

    detect_os

    if [[ "$INSTALL_MODE" == "docker" ]]; then
        if [[ "$OS" != "windows" && "$OS" != "macos" ]]; then
            install_docker_linux
        fi
        install_docker_compose
        deploy_milvus
    elif [[ "$INSTALL_MODE" == "local" ]]; then
        deploy_milvus_local
    else
        error "不支持的安装模式: $INSTALL_MODE (请使用 docker 或 local)"
    fi

    echo ""
    info "=========================================="
    info "  安装完成！"
    info "=========================================="
    info "Milvus 地址: localhost:19530"
    info "Minio Console: http://localhost:9001 (minioadmin/minioadmin)"
    info "=========================================="
}

main "$@"
