#!/bin/bash

################################################################################
# Milvus 一键安装脚本 (Docker 模式)
################################################################################
# 功能：
# 1. 自动检测环境 (Linux CentOS/Kylin, Windows Git Bash/WSL)
# 2. 检查并安装 Docker 和 Docker Compose (仅 Linux)
# 3. 部署 Milvus Standalone (Etcd, Minio, Milvus)
# 4. 验证安装状态
################################################################################

set -e

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

    # 生成 docker-compose.yml
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

    info "启动容器 (可能需要几分钟下载镜像)..."
    $DOCKER_COMPOSE_CMD up -d

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

    error "Milvus 启动超时，请检查容器状态: $DOCKER_COMPOSE_CMD ps"
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

    if [[ "$OS" != "windows" && "$OS" != "macos" ]]; then
        install_docker_linux
    fi

    install_docker_compose
    deploy_milvus

    echo ""
    info "=========================================="
    info "  安装完成！"
    info "=========================================="
    info "Milvus 地址: localhost:19530"
    info "Minio Console: http://localhost:9001 (minioadmin/minioadmin)"
    info "=========================================="
}

main "$@"
