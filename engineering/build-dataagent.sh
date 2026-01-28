#!/bin/bash

################################################################################
# DataAgent 编译打包脚本
################################################################################
# 功能：
# 1. 编译前端和后端项目
# 2. 打包所有必要的文件到 output 目录
# 3. 准备配置模板文件
#
# 使用说明：
# ./build-dataagent.sh [output_dir]
#
# 参数：
#   output_dir - 输出目录（可选，默认为 ./output）
################################################################################

set -e  # 遇到错误立即退出

# ============================================================================
# 颜色输出
# ============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

step() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# ============================================================================
# 脚本目录和输出目录
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$SCRIPT_DIR/output}"
BUILD_TIME=$(date +%Y%m%d_%H%M%S)

info "源码目录: $SCRIPT_DIR"
info "输出目录: $OUTPUT_DIR"

# 设置 Maven 编码选项
export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

# ============================================================================
# 检查环境
# ============================================================================
check_environment() {
    step "检查编译环境"
    
    # 检查 Java
    if ! command -v java &> /dev/null; then
        error "Java 未安装，请安装 Java 17 或更高版本"
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        error "Java 版本过低 (当前: $JAVA_VERSION, 需要: 17+)"
    fi
    info "Java 版本: $(java -version 2>&1 | head -n1)"
    
    # 检查 Maven Wrapper
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
    if [ ! -f "$PROJECT_ROOT/mvnw" ]; then
        error "Maven Wrapper 不存在，请确保项目完整"
    fi
    info "Maven Wrapper: OK"
    
    # 检查 Node.js
    if ! command -v node &> /dev/null; then
        error "Node.js 未安装，请安装 Node.js 16 或更高版本"
    fi
    
    NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
    if [ "$NODE_VERSION" -lt 16 ]; then
        error "Node.js 版本过低 (当前: $NODE_VERSION, 需要: 16+)"
    fi
    info "Node.js 版本: $(node -v)"
    
    # 检查 Yarn
    if ! command -v yarn &> /dev/null; then
        error "Yarn 未安装，请安装 Yarn: npm install -g yarn"
    fi
    info "Yarn 版本: $(yarn -v)"
    
    echo ""
    info "✅ 编译环境检查通过"
}

# ============================================================================
# 清理旧的构建产物
# ============================================================================
clean_build() {
    step "清理旧的构建产物"
    
    cd "$PROJECT_ROOT"
    
    # 清理后端
    if [ -f "./mvnw" ]; then
        info "清理后端构建产物..."
        export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
        ./mvnw clean -q
    fi
    
    # 清理前端
    if [ -d "data-agent-frontend/dist" ]; then
        info "清理前端构建产物..."
        rm -rf data-agent-frontend/dist
    fi
    
    info "✅ 清理完成"
}

# ============================================================================
# 编译后端
# ============================================================================
build_backend() {
    step "编译后端"
    
    cd "$PROJECT_ROOT"
    
    info "开始编译后端项目..."
    export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
    ./mvnw clean package -DskipTests=true
    
    # 查找生成的 JAR 文件
    JAR_FILE=$(find "$PROJECT_ROOT/data-agent-management/target" -name "spring-ai-audaque-data-agent-management-*.jar" ! -name "*.original" | head -n1)
    
    if [ ! -f "$JAR_FILE" ]; then
        error "后端编译失败，未找到 JAR 文件"
    fi
    
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    info "✅ 后端编译成功: $(basename $JAR_FILE) ($JAR_SIZE)"
    
    # 保存 JAR 文件路径供后续使用
    export BACKEND_JAR="$JAR_FILE"
}

# ============================================================================
# 编译前端
# ============================================================================
build_frontend() {
    step "编译前端"
    
    cd "$PROJECT_ROOT/data-agent-frontend"
    
    # 安装依赖
    if [ ! -d "node_modules" ]; then
        info "安装前端依赖..."
        yarn install
    else
        info "前端依赖已存在，跳过安装"
    fi
    
    # 构建生产版本
    info "构建前端生产版本..."
    yarn build
    
    # 检查构建结果
    if [ ! -d "dist" ] || [ ! -f "dist/index.html" ]; then
        error "前端编译失败，未找到 dist 目录或 index.html"
    fi
    
    DIST_SIZE=$(du -sh dist | cut -f1)
    info "✅ 前端编译成功: dist/ ($DIST_SIZE)"
}

# ============================================================================
# 打包输出
# ============================================================================
package_output() {
    step "打包输出文件"
    
    # 创建输出目录结构
    info "创建输出目录结构..."
    mkdir -p "$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR/backend"
    mkdir -p "$OUTPUT_DIR/frontend"
    mkdir -p "$OUTPUT_DIR/config"
    mkdir -p "$OUTPUT_DIR/scripts"
    
    # 复制后端 JAR
    info "复制后端 JAR..."
    cp "$BACKEND_JAR" "$OUTPUT_DIR/backend/dataagent-backend.jar"
    
    # 复制前端构建产物
    info "复制前端构建产物..."
    cp -r "$PROJECT_ROOT/data-agent-frontend/dist/"* "$OUTPUT_DIR/frontend/"
    
    # 复制配置模板
    info "复制配置模板..."
    cp "$PROJECT_ROOT/application.yml.sample" "$OUTPUT_DIR/config/application.yml.template"
    
    # 复制 Nginx 配置模板
    if [ -f "$PROJECT_ROOT/docker-file/config/nginx-production.conf" ]; then
        cp "$PROJECT_ROOT/docker-file/config/nginx-production.conf" "$OUTPUT_DIR/config/nginx-dataagent.conf.template"
    fi
    
    # 复制数据库初始化脚本
    info "复制数据库初始化脚本..."
    mkdir -p "$OUTPUT_DIR/config/sql/mysql"
    mkdir -p "$OUTPUT_DIR/config/sql/dameng"
    
    if [ -d "$PROJECT_ROOT/data-agent-management/src/main/resources/sql/mysql" ]; then
        cp -r "$PROJECT_ROOT/data-agent-management/src/main/resources/sql/mysql/"* "$OUTPUT_DIR/config/sql/mysql/"
    fi
    
    if [ -d "$PROJECT_ROOT/data-agent-management/src/main/resources/sql/dameng" ]; then
        cp -r "$PROJECT_ROOT/data-agent-management/src/main/resources/sql/dameng/"* "$OUTPUT_DIR/config/sql/dameng/"
    fi
    
    # 生成版本信息
    info "生成版本信息..."
    cat > "$OUTPUT_DIR/VERSION.txt" <<EOF
DataAgent Build Information
============================
Build Time: $BUILD_TIME
Build Host: $(hostname)
Build User: $(whoami)

Backend JAR: $(basename $BACKEND_JAR)
JAR Size: $(du -h "$BACKEND_JAR" | cut -f1)

Frontend Dist Size: $(du -sh "$PROJECT_ROOT/data-agent-frontend/dist" | cut -f1)

Java Version: $(java -version 2>&1 | head -n1)
Node Version: $(node -v)
Yarn Version: $(yarn -v)
EOF
    
    info "✅ 打包完成"
}

# ============================================================================
# 生成校验信息
# ============================================================================
generate_checksums() {
    step "生成文件校验信息"
    
    cd "$OUTPUT_DIR"
    
    info "计算文件 SHA256 校验和..."
    
    # 生成后端 JAR 校验和
    if command -v sha256sum &> /dev/null; then
        sha256sum backend/dataagent-backend.jar > backend/dataagent-backend.jar.sha256
        info "✅ 后端 JAR 校验和: $(cat backend/dataagent-backend.jar.sha256 | cut -d' ' -f1 | cut -c1-16)..."
    else
        warn "sha256sum 命令不可用，跳过校验和生成"
    fi
}

# ============================================================================
# 生成安装说明
# ============================================================================
generate_install_guide() {
    step "生成安装说明"
    
    cat > "$OUTPUT_DIR/INSTALL.txt" <<'EOF'
DataAgent 安装说明
==================

本目录包含编译好的 DataAgent 前后端文件和配置模板。

目录结构：
---------
output/
├── backend/                      # 后端文件
│   ├── dataagent-backend.jar    # 后端 JAR 包
│   └── dataagent-backend.jar.sha256  # 校验和
├── frontend/                     # 前端文件
│   ├── index.html
│   └── assets/
├── config/                       # 配置模板
│   ├── application.yml.template  # 后端配置模板
│   ├── nginx-dataagent.conf.template  # Nginx 配置模板
│   └── sql/                     # 数据库初始化脚本
│       ├── mysql/
│       └── dameng/
├── scripts/                      # 安装脚本（稍后添加）
├── VERSION.txt                   # 版本信息
└── INSTALL.txt                   # 本文件

Linux 安装：
-----------
1. 将 output 目录复制到目标服务器
2. 运行安装脚本：
   ./scripts/install-dataagent.sh --output-dir /path/to/output

Windows 安装：
-------------
1. 将 output 目录复制到目标服务器
2. 在 PowerShell 中运行：
   .\scripts\install-dataagent.ps1 -OutputDir "C:\path\to\output"

手动安装：
---------
如果不使用安装脚本，请参考以下步骤：

1. 后端部署：
   - 复制 backend/dataagent-backend.jar 到部署目录
   - 修改 config/application.yml.template 并重命名为 application.yml
   - 启动：java -jar dataagent-backend.jar

2. 前端部署：
   - 复制 frontend/* 到 Web 服务器根目录
   - 配置 Nginx 反向代理（参考 config/nginx-dataagent.conf.template）

3. 数据库初始化：
   - MySQL: 执行 config/sql/mysql/schema.sql
   - 达梦: 执行 config/sql/dameng/schema.sql

详细文档请参考项目主目录的 docs/ 文件夹。
EOF
    
    info "✅ 安装说明已生成: $OUTPUT_DIR/INSTALL.txt"
}

# ============================================================================
# 显示构建摘要
# ============================================================================
show_summary() {
    step "构建摘要"
    
    echo ""
    info "=========================================="
    info "  构建成功！"
    info "=========================================="
    echo ""
    info "输出目录: $OUTPUT_DIR"
    info ""
    info "目录结构:"
    info "  backend/dataagent-backend.jar    - 后端 JAR 包"
    info "  frontend/                        - 前端静态文件"
    info "  config/                          - 配置模板"
    info "  VERSION.txt                      - 版本信息"
    info "  INSTALL.txt                      - 安装说明"
    echo ""
    info "下一步："
    info "  1. 查看版本信息: cat $OUTPUT_DIR/VERSION.txt"
    info "  2. 查看安装说明: cat $OUTPUT_DIR/INSTALL.txt"
    info "  3. 将 output 目录复制到目标服务器"
    info "  4. 使用安装脚本进行部署"
    echo ""
    info "=========================================="
}

# ============================================================================
# 主流程
# ============================================================================
main() {
    echo ""
    info "=========================================="
    info "  DataAgent 编译打包脚本"
    info "=========================================="
    echo ""
    info "构建时间: $BUILD_TIME"
    echo ""
    
    # 检查环境
    check_environment
    
    # 清理旧的构建产物
    clean_build
    
    # 编译
    build_backend
    build_frontend
    
    # 打包
    package_output
    
    # 生成校验信息
    generate_checksums
    
    # 生成安装说明
    generate_install_guide
    
    # 显示摘要
    show_summary
}

# 执行主流程
main "$@"
