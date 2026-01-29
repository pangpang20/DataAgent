#!/bin/bash

################################################################################
# DataAgent 编译打包脚本
################################################################################
# 功能：
# 1. 编译前后端项目（可选）
# 2. 打包编译产物到 output 目录
# 3. 部署前后端（可选）
# 4. 配置并启动服务（可选）
#
# 使用说明：
# 1. 仅编译和打包：./build-dataagent.sh [output_dir]
# 2. 编译、打包并部署：./build-dataagent.sh [output_dir] --deploy --deploy-dir /opt/dataagent --db-type mysql --db-user root --db-password password123
# 3. 仅部署（使用现有打包）：./build-dataagent.sh --deploy --package-dir ./output --deploy-dir /opt/dataagent --db-type mysql
#
# 参数：
#   output_dir              - 输出目录（默认 ./output）
#   --deploy                - 部署模式（编译后自动部署）
#   --deploy-dir <path>     - 部署目录（默认 /opt/dataagent）
#   --package-dir <path>    - 打包文件所在目录（部署模式下使用）
#   --db-type <type>        - 数据库类型：mysql | dameng（默认 mysql）
#   --db-host <host>        - 数据库主机（默认 127.0.0.1）
#   --db-port <port>        - 数据库端口（可选）
#   --db-name <name>        - 数据库名称（默认 data_agent）
#   --db-user <user>        - 数据库用户（默认 root）
#   --db-password <pass>    - 数据库密码
#   --backend-port <port>   - 后端端口（默认 8065）
#   --frontend-port <port>  - 前端端口（默认 80）
#   --vector-store <type>   - 向量库类型：simple | milvus（默认 simple）
#   --milvus-host <host>    - Milvus 主机（默认 127.0.0.1）
#   --milvus-port <port>    - Milvus 端口（默认 19530）
#   --milvus-username <user> - Milvus 用户名（默认 root）
#   --milvus-password <pass> - Milvus 密码（默认 Milvus）
#   --milvus-database <db>  - Milvus 数据库（默认 default）
#   --milvus-collection <name> - Milvus 集合名（默认 data_agent_vector）
#   --help                  - 显示帮助信息
################################################################################

set -e  # 遇到错误立即退出

# ============================================================================
# 默认配置
# ============================================================================
DEPLOY=false
DEPLOY_DIR="/opt/dataagent"
PACKAGE_DIR=""
DB_TYPE="mysql"
DB_HOST="127.0.0.1"
DB_PORT=""
DB_NAME="data_agent"
DB_USER="root"
DB_PASSWORD=""
BACKEND_PORT=8065
FRONTEND_PORT=80
VECTOR_STORE_TYPE="simple"
MILVUS_HOST="127.0.0.1"
MILVUS_PORT=19530
MILVUS_USERNAME="root"
MILVUS_PASSWORD="Milvus"
MILVUS_DATABASE="default"
MILVUS_COLLECTION="data_agent_vector"
SHOW_HELP=false

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
# 显示帮助信息
# ============================================================================
show_help() {
    cat <<EOF
DataAgent Linux 编译部署脚本

使用方法:
  # 仅编译和打包
  ./build-dataagent.sh [output_dir]

  # 编译、打包并部署
  ./build-dataagent.sh [output_dir] --deploy --deploy-dir /opt/dataagent \
     --db-type mysql --db-user root --db-password password123

  # 仅部署（使用现有打包）
  ./build-dataagent.sh --deploy --package-dir ./output --deploy-dir /opt/dataagent \
     --db-type mysql --db-user root --db-password password123

参数:
  output_dir              输出目录（默认: ./output）
  --deploy                启用部署模式
  --deploy-dir <path>     部署目录（默认: /opt/dataagent）
  --package-dir <path>    打包目录（部署模式下使用）
  --db-type <type>        数据库类型: mysql | dameng（默认: mysql）
  --db-host <host>        数据库主机（默认: 127.0.0.1）
  --db-port <port>        数据库端口（MySQL:3306, 达梦:5236）
  --db-name <name>        数据库名称（默认: data_agent）
  --db-user <user>        数据库用户（默认: root）
  --db-password <pass>    数据库密码
  --backend-port <port>   后端端口（默认: 8065）
  --frontend-port <port>  前端端口（默认: 80）
  --vector-store <type>   向量库类型: simple | milvus（默认: simple）
  --milvus-host <host>    Milvus 主机（默认: 127.0.0.1）
  --milvus-port <port>    Milvus 端口（默认: 19530）
  --milvus-username <user> Milvus 用户名（默认: root）
  --milvus-password <pass> Milvus 密码（默认: Milvus）
  --milvus-database <db>  Milvus 数据库（默认: default）
  --milvus-collection <name> Milvus 集合名（默认: data_agent_vector）
  --help                  显示此帮助信息
EOF
    exit 0
}

# ============================================================================
# 解析命令行参数
# ============================================================================
parse_arguments() {
    local output_dir_arg=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --deploy)
                DEPLOY=true
                shift
                ;;
            --deploy-dir)
                DEPLOY_DIR="$2"
                shift 2
                ;;
            --package-dir)
                PACKAGE_DIR="$2"
                shift 2
                ;;
            --db-type)
                DB_TYPE="$2"
                shift 2
                ;;
            --db-host)
                DB_HOST="$2"
                shift 2
                ;;
            --db-port)
                DB_PORT="$2"
                shift 2
                ;;
            --db-name)
                DB_NAME="$2"
                shift 2
                ;;
            --db-user)
                DB_USER="$2"
                shift 2
                ;;
            --db-password)
                DB_PASSWORD="$2"
                shift 2
                ;;
            --backend-port)
                BACKEND_PORT="$2"
                shift 2
                ;;
            --frontend-port)
                FRONTEND_PORT="$2"
                shift 2
                ;;
            --vector-store)
                VECTOR_STORE_TYPE="$2"
                shift 2
                ;;
            --milvus-host)
                MILVUS_HOST="$2"
                shift 2
                ;;
            --milvus-port)
                MILVUS_PORT="$2"
                shift 2
                ;;
            --milvus-username)
                MILVUS_USERNAME="$2"
                shift 2
                ;;
            --milvus-password)
                MILVUS_PASSWORD="$2"
                shift 2
                ;;
            --milvus-database)
                MILVUS_DATABASE="$2"
                shift 2
                ;;
            --milvus-collection)
                MILVUS_COLLECTION="$2"
                shift 2
                ;;
            --help)
                SHOW_HELP=true
                shift
                ;;
            -*)
                error "未知参数: $1\n使用 --help 查看帮助信息"
                ;;
            *)
                # 位置参数作为输出目录
                if [ -z "$output_dir_arg" ]; then
                    output_dir_arg="$1"
                else
                    error "未知参数: $1\n使用 --help 查看帮助信息"
                fi
                shift
                ;;
        esac
    done
    
    # 设置输出目录
    if [ -z "$output_dir_arg" ]; then
        if [ -z "$PACKAGE_DIR" ]; then
            OUTPUT_DIR="$SCRIPT_DIR/output"
        else
            OUTPUT_DIR="$PACKAGE_DIR"
        fi
    else
        OUTPUT_DIR="$output_dir_arg"
    fi
    
    # 如果部署模式且没有指定PACKAGE_DIR，则使用OUTPUT_DIR
    if [ "$DEPLOY" = true ] && [ -z "$PACKAGE_DIR" ]; then
        PACKAGE_DIR="$OUTPUT_DIR"
    fi
    
    # 设置数据库默认值
    if [ -z "$DB_PORT" ]; then
        if [ "$DB_TYPE" = "dameng" ] || [ "$DB_TYPE" = "dm" ]; then
            DB_PORT="5236"
        else
            DB_PORT="3306"
        fi
    fi
    
    if [ -z "$DB_NAME" ]; then
        DB_NAME="data_agent"
    fi
    
    if [ -z "$DB_USER" ]; then
        if [ "$DB_TYPE" = "dameng" ] || [ "$DB_TYPE" = "dm" ]; then
            DB_USER="SYSDBA"
        else
            DB_USER="root"
        fi
    fi
}

# ============================================================================
# 脚本目录和输出目录
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 先解析参数
parse_arguments "$@"

# 检查是否显示帮助
if [ "$SHOW_HELP" = true ]; then
    show_help
fi

BUILD_TIME=$(date +%Y%m%d_%H%M%S)

info "源码目录: $SCRIPT_DIR"
info "输出目录: $OUTPUT_DIR"
if [ "$DEPLOY" = true ]; then
    info "部署目录: $DEPLOY_DIR"
    info "部署模式: 启用"
fi

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
# 部署功能函数
# ============================================================================

# 停止现有服务
stop_services() {
    step "停止现有服务"
    
    # 停止后端服务
    if [ -f "$DEPLOY_DIR/backend.pid" ]; then
        BACKEND_PID=$(cat "$DEPLOY_DIR/backend.pid")
        if ps -p $BACKEND_PID > /dev/null 2>&1; then
            info "停止后端服务 (PID: $BACKEND_PID)..."
            kill -15 $BACKEND_PID 2>/dev/null || true
            sleep 3
            
            # 强制停止
            if ps -p $BACKEND_PID > /dev/null 2>&1; then
                warn "强制停止后端服务..."
                kill -9 $BACKEND_PID 2>/dev/null || true
            fi
        fi
    fi
    
    # 检查端口占用
    if command -v lsof &> /dev/null && lsof -i:$BACKEND_PORT > /dev/null 2>&1; then
        BACKEND_PID=$(lsof -t -i:$BACKEND_PORT)
        warn "端口 $BACKEND_PORT 仍被占用 (PID: $BACKEND_PID)，强制停止..."
        kill -9 $BACKEND_PID 2>/dev/null || true
    fi
    
    info "✅ 服务已停止"
}

# 备份旧版本
backup_old_version() {
    step "备份旧版本"
    
    BACKUP_DIR="$DEPLOY_DIR/backup/$(date +%Y%m%d_%H%M%S)"
    
    if [ -d "$DEPLOY_DIR" ] && [ -f "$DEPLOY_DIR/dataagent-backend.jar" ]; then
        mkdir -p "$BACKUP_DIR"
        
        # 备份后端
        if [ -f "$DEPLOY_DIR/dataagent-backend.jar" ]; then
            cp "$DEPLOY_DIR/dataagent-backend.jar" "$BACKUP_DIR/"
        fi
        
        # 备份配置
        if [ -f "$DEPLOY_DIR/application.yml" ]; then
            cp "$DEPLOY_DIR/application.yml" "$BACKUP_DIR/"
        fi
        
        info "✅ 已备份到: $BACKUP_DIR"
    else
        info "未发现旧版本，跳过备份"
    fi
}

# 部署后端
deploy_backend() {
    step "部署后端"
    
    # 创建部署目录
    sudo mkdir -p "$DEPLOY_DIR"
    sudo mkdir -p "$DEPLOY_DIR/logs"
    sudo mkdir -p "$DEPLOY_DIR/uploads"
    sudo chown -R $USER:$USER "$DEPLOY_DIR"
    
    # 复制后端 JAR
    SOURCE_JAR="$PACKAGE_DIR/backend/dataagent-backend.jar"
    if [ ! -f "$SOURCE_JAR" ]; then
        error "找不到后端 JAR 文件: $SOURCE_JAR"
    fi
    
    cp "$SOURCE_JAR" "$DEPLOY_DIR/"
    info "✅ 后端 JAR 已部署"
    
    # 生成配置文件
    info "生成配置文件..."
    CONFIG_TEMPLATE="$PACKAGE_DIR/config/application.yml.template"
    if [ ! -f "$CONFIG_TEMPLATE" ]; then
        error "配置模板不存在: $CONFIG_TEMPLATE"
    fi
    
    cp "$CONFIG_TEMPLATE" "$DEPLOY_DIR/application.yml"
    
    # 转义特殊字符
    ESC_DB_USER=$(printf '%s\n' "$DB_USER" | sed 's/[[\.*^$()+?{|]/\\&/g')
    ESC_DB_PASS=$(printf '%s\n' "$DB_PASSWORD" | sed 's/[[\.*^$()+?{|]/\\&/g')
    ESC_DB_HOST=$(printf '%s\n' "$DB_HOST" | sed 's/[[\.*^$()+?{|]/\\&/g')
    ESC_DB_NAME=$(printf '%s\n' "$DB_NAME" | sed 's/[[\.*^$()+?{|]/\\&/g')
    
    # 根据数据库类型替换配置
    if [ "$DB_TYPE" = "dameng" ] || [ "$DB_TYPE" = "dm" ]; then
        info "应用达梦数据库配置..."
        sed -i "s|platform: mysql|platform: dameng|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|url: jdbc:mysql://[^?]*\?[^\n]*|url: jdbc:dm://$ESC_DB_HOST:$DB_PORT?zeroDateTimeBehavior=convertToNull\&useUnicode=true\&characterEncoding=UTF-8\&socketTimeout=100000\&connectTimeout=60000|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|driver-class-name: com.mysql.cj.jdbc.Driver|driver-class-name: dm.jdbc.driver.DmDriver|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|validation-query: SELECT 1|validation-query: SELECT 1 FROM DUAL|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|username: cyl|username: $ESC_DB_USER|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|password: Audaque@123|password: $ESC_DB_PASS|g" "$DEPLOY_DIR/application.yml"
    else
        info "应用 MySQL 数据库配置..."
        sed -i "s|url: jdbc:mysql://[^?]*\?[^\n]*|url: jdbc:mysql://$ESC_DB_HOST:$DB_PORT/$ESC_DB_NAME?useUnicode=true\&characterEncoding=utf-8\&zeroDateTimeBehavior=convertToNull\&transformedBitIsBoolean=true\&allowMultiQueries=true\&allowPublicKeyRetrieval=true\&useSSL=false\&serverTimezone=Asia/Shanghai|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|username: cyl|username: $ESC_DB_USER|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|password: Audaque@123|password: $ESC_DB_PASS|g" "$DEPLOY_DIR/application.yml"
    fi
    
    # 配置端口
    info "配置后端端口: $BACKEND_PORT"
    sed -i "s|port: 8065|port: $BACKEND_PORT|g" "$DEPLOY_DIR/application.yml"
    
    # 配置向量库
    info "配置向量库类型: $VECTOR_STORE_TYPE"
    sed -i "s|type: simple|type: $VECTOR_STORE_TYPE|g" "$DEPLOY_DIR/application.yml"
    
    if [ "$VECTOR_STORE_TYPE" = "milvus" ]; then
        info "开启 Milvus 自动配置..."
        sed -i "s|\${VECTOR_STORE_EXCLUDE:[^}]*}| |g" "$DEPLOY_DIR/application.yml"
        
        # 启用 Milvus（精确替换 milvus 段落下的 enabled）
        sed -i '/vectorstore:/,/elasticsearch:/ s|milvus:|milvus:|; /milvus:/,/elasticsearch:/ s|enabled: false|enabled: true|' "$DEPLOY_DIR/application.yml"
        
        # 转义特殊字符
        ESC_MILVUS_USER=$(printf '%s\n' "$MILVUS_USERNAME" | sed 's/[[\.*^$()+?{|]/\\&/g')
        ESC_MILVUS_PASS=$(printf '%s\n' "$MILVUS_PASSWORD" | sed 's/[[\.*^$()+?{|]/\\&/g')
        ESC_MILVUS_DB=$(printf '%s\n' "$MILVUS_DATABASE" | sed 's/[[\.*^$()+?{|]/\\&/g')
        ESC_MILVUS_COLL=$(printf '%s\n' "$MILVUS_COLLECTION" | sed 's/[[\.*^$()+?{|]/\\&/g')
        
        sed -i "s|host: localhost|host: $MILVUS_HOST|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|port: 19530|port: $MILVUS_PORT|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|username: root|username: $ESC_MILVUS_USER|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|password: Milvus|password: $ESC_MILVUS_PASS|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|database-name: default|database-name: $ESC_MILVUS_DB|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|collection-name: data_agent_vector|collection-name: $ESC_MILVUS_COLL|g" "$DEPLOY_DIR/application.yml"
    fi
    
    info "✅ 后端部署完成"
}

# 部署前端
deploy_frontend() {
    step "部署前端"
    
    # 创建前端目录
    FRONTEND_DIR="$DEPLOY_DIR/frontend"
    mkdir -p "$FRONTEND_DIR"
    
    # 复制前端文件
    rm -rf "$FRONTEND_DIR"/*
    if [ -d "$PACKAGE_DIR/frontend" ]; then
        cp -r "$PACKAGE_DIR/frontend/"* "$FRONTEND_DIR/"
    else
        error "找不到前端文件目录: $PACKAGE_DIR/frontend"
    fi
    
    info "✅ 前端文件已部署到: $FRONTEND_DIR"
    
    # 配置 Nginx
    configure_nginx
}

# 配置 Nginx
configure_nginx() {
    info "配置 Nginx..."
    
    # 获取本机 IP
    HOST_IP=$(hostname -I | awk '{print $1}')
    if [ -z "$HOST_IP" ]; then
        HOST_IP=$(ip route get 1 2>/dev/null | awk '{print $7}' | head -n1)
    fi
    if [ -z "$HOST_IP" ]; then
        HOST_IP="localhost"
    fi
    
    # 检测 Nginx 配置目录结构
    if [ -d "/etc/nginx/sites-available" ]; then
        NGINX_CONFIG_DIR="/etc/nginx/sites-available"
        NGINX_ENABLE_DIR="/etc/nginx/sites-enabled"
        NGINX_CONFIG_FILE="$NGINX_CONFIG_DIR/dataagent"
        USE_SITES_STYLE=true
    else
        NGINX_CONFIG_DIR="/etc/nginx/conf.d"
        NGINX_CONFIG_FILE="$NGINX_CONFIG_DIR/dataagent.conf"
        USE_SITES_STYLE=false
        sudo mkdir -p "$NGINX_CONFIG_DIR"
    fi
    
    # 复制并修改 Nginx 配置
    NGINX_TEMPLATE="$PACKAGE_DIR/config/nginx-dataagent.conf.template"
    NGINX_LOCAL_CONF="$DEPLOY_DIR/nginx-dataagent.conf"
    
    if [ ! -f "$NGINX_TEMPLATE" ]; then
        warn "Nginx 配置模板不存在，跳过 Nginx 配置"
        return 0
    fi
    
    cp "$NGINX_TEMPLATE" "$NGINX_LOCAL_CONF"
    
    # 替换配置参数
    FRONTEND_DIR="$DEPLOY_DIR/frontend"
    sed -i "s|server 127.0.0.1:8065|server 127.0.0.1:$BACKEND_PORT|g" "$NGINX_LOCAL_CONF"
    sed -i "s|listen 80;|listen $FRONTEND_PORT;|g" "$NGINX_LOCAL_CONF"
    sed -i "s|server_name .*;|server_name $HOST_IP localhost;|g" "$NGINX_LOCAL_CONF"
    sed -i "s|root .*;|root $FRONTEND_DIR;|g" "$NGINX_LOCAL_CONF"
    sed -i "s|alias .*/uploads/;|alias $DEPLOY_DIR/uploads/;|g" "$NGINX_LOCAL_CONF"
    
    # 部署配置
    sudo cp "$NGINX_LOCAL_CONF" "$NGINX_CONFIG_FILE"
    
    # 启用配置
    if [ "$USE_SITES_STYLE" = true ]; then
        sudo ln -sf "$NGINX_CONFIG_FILE" "$NGINX_ENABLE_DIR/dataagent"
        sudo rm -f "$NGINX_ENABLE_DIR/default"
    else
        sudo sed -i.bak 's/^\(\s*listen\s\+80\)/# \1/' /etc/nginx/nginx.conf 2>/dev/null || true
    fi
    
    # 测试配置
    if sudo nginx -t 2>&1; then
        info "✅ Nginx 配置测试通过"
        sudo systemctl reload nginx
    else
        error "Nginx 配置测试失败"
    fi
}

# 启动后端服务
start_backend() {
    step "启动后端服务"
    
    cd "$DEPLOY_DIR"
    
    # 后台启动
    nohup java -Xmx4g -Xms2g \
        -XX:+UseG1GC \
        -Dfile.encoding=UTF-8 \
        -Dsun.jnu.encoding=UTF-8 \
        -Dclient.encoding.override=UTF-8 \
        -jar dataagent-backend.jar \
        > /dev/null 2>&1 &
    
    BACKEND_PID=$!
    echo $BACKEND_PID > "$DEPLOY_DIR/backend.pid"
    info "后端服务已启动 (PID: $BACKEND_PID)"
    
    # 等待服务启动
    info "等待后端服务启动..."
    for i in {1..60}; do
        if curl -s http://localhost:$BACKEND_PORT/actuator/health > /dev/null 2>&1 || \
           curl -s http://localhost:$BACKEND_PORT/api/agent/list > /dev/null 2>&1; then
            info "✅ 后端服务启动成功！"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    
    echo ""
    error "后端服务启动超时，请查看日志: tail -f $DEPLOY_DIR/logs/application.log"
}

# 显示部署摘要
show_deploy_summary() {
    # 获取本机 IP
    HOST_IP=$(hostname -I | awk '{print $1}')
    if [ -z "$HOST_IP" ]; then
        HOST_IP=$(ip route get 1 2>/dev/null | awk '{print $7}' | head -n1)
    fi
    if [ -z "$HOST_IP" ]; then
        HOST_IP="localhost"
    fi
    
    echo ""
    info "=========================================="
    info "  部署完成！"
    info "=========================================="
    echo ""
    info "访问地址:"
    info "  后端: http://$HOST_IP:$BACKEND_PORT"
    info "  健康检查: http://$HOST_IP:$BACKEND_PORT/actuator/health"
    echo ""
    info "前端部署:"
    info "  前端目录: $DEPLOY_DIR/frontend"
    info "  访问地址: http://$HOST_IP:$FRONTEND_PORT"
    echo ""
    info "管理命令:"
    info "  查看后端日志: tail -f $DEPLOY_DIR/logs/application.log"
    info "  停止后端: kill \$(cat $DEPLOY_DIR/backend.pid)"
    echo ""
    info "配置信息:"
    info "  部署目录: $DEPLOY_DIR"
    info "  数据库类型: $DB_TYPE"
    info "  数据库地址: $DB_HOST:$DB_PORT"
    info "  向量库类型: $VECTOR_STORE_TYPE"
    echo ""
    info "=========================================="
}

# 部署流程
deploy_process() {
    if [ "$DEPLOY" = true ]; then
        stop_services
        backup_old_version
        deploy_backend
        deploy_frontend
        start_backend
        show_deploy_summary
    fi
}

# ============================================================================
# 显示构建摘要
# ============================================================================
show_summary() {
    step "构建摘要"
    
    if [ "$DEPLOY" = true ]; then
        echo ""
        info "构建和部署已完成！"
        echo ""
        info "输出目录: $OUTPUT_DIR"
        info "部署目录: $DEPLOY_DIR"
    else
        echo ""
        info "=========================================="
        info "  构建成功！"
        info "=========================================="
        echo ""
        info "输出目录: $OUTPUT_DIR"
        echo ""
        info "目录结构:"
        info "  backend/dataagent-backend.jar    - 后端 JAR 包"
        info "  frontend/                        - 前端静态文件"
        info "  config/                          - 配置模板"
        info "  scripts/                         - 安装脚本"
        info "  VERSION.txt                      - 版本信息"
        info "  INSTALL.txt                      - 安装说明"
        echo ""
        info "下一步："
        info "  1. 查看版本信息: cat $OUTPUT_DIR/VERSION.txt"
        info "  2. 查看安装说明: cat $OUTPUT_DIR/INSTALL.txt"
        info "  3. 将 output 目录复制到目标服务器"
        info "  4. 使用脚本进行部署: ./build-dataagent.sh --deploy --deploy-dir /opt/dataagent --package-dir $OUTPUT_DIR"
        echo ""
        info "Linux 本地一键部署："
        info "  ./build-dataagent.sh --deploy --deploy-dir /opt/dataagent --package-dir $OUTPUT_DIR --db-type mysql --db-user root --db-password password123"
        echo ""
        info "=========================================="
    fi
}

# ============================================================================
# 主流程
# ============================================================================
main() {
    echo ""
    info "=========================================="
    info "  DataAgent 编译部署脚本"
    info "=========================================="
    echo ""
    info "构建时间: $BUILD_TIME"
    echo ""
    
    if [ "$DEPLOY" != true ]; then
        # 只在非部署模式下执行编译流程
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
    fi
    
    # 如果需要部署，则执行部署流程
    deploy_process
    
    # 显示摘要
    show_summary
}

# 执行主流程
main "$@"