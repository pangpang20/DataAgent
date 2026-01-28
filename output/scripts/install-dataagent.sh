#!/bin/bash

################################################################################
# DataAgent 安装脚本 (Linux)
################################################################################
# 功能：
# 1. 从 output 目录读取编译好的前后端文件
# 2. 检查并安装所需环境（Java 17、Nginx）
# 3. 部署前后端
# 4. 配置并启动服务
#
# 支持的操作系统：
# - CentOS 7/8
# - Kylin V10
# - Ubuntu/Debian (实验性支持)
#
# 使用说明：
# ./install-dataagent.sh --output-dir <output_path> [options]
#
# 参数：
#   --output-dir <path>     - 编译输出目录（必需）
#   --deploy-dir <path>     - 部署目录（可选，默认 /opt/dataagent）
#   --db-type <type>        - 数据库类型：mysql | dameng（可选，默认 mysql）
#   --db-host <host>        - 数据库主机（可选，默认 127.0.0.1）
#   --db-port <port>        - 数据库端口（可选）
#   --db-name <name>        - 数据库名称（可选）
#   --db-user <user>        - 数据库用户（可选）
#   --db-password <pass>    - 数据库密码（可选）
#   --backend-port <port>   - 后端端口（可选，默认 8065）
#   --frontend-port <port>  - 前端端口（可选，默认 80）
#   --vector-store <type>   - 向量库类型：simple | milvus（可选，默认 simple）
#   --skip-deps             - 跳过依赖安装
#   --help                  - 显示帮助信息
################################################################################

set -e  # 遇到错误立即退出

# ============================================================================
# 脚本目录
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ============================================================================
# 默认配置
# ============================================================================
OUTPUT_DIR=""
DEPLOY_DIR="/opt/dataagent"
DB_TYPE="mysql"
DB_HOST="127.0.0.1"
DB_PORT=""
DB_NAME=""
DB_USER=""
DB_PASSWORD=""
BACKEND_PORT=8065
FRONTEND_PORT=80
VECTOR_STORE_TYPE="simple"
MILVUS_HOST="127.0.0.1"
MILVUS_PORT="19530"
MILVUS_COLLECTION="data_agent"
SKIP_DEPS=false

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
DataAgent Linux 安装脚本

使用方法:
  $0 --output-dir <output_path> [options]

必需参数:
  --output-dir <path>       编译输出目录

可选参数:
  --deploy-dir <path>       部署目录（默认: /opt/dataagent）
  --db-type <type>          数据库类型: mysql | dameng（默认: mysql）
  --db-host <host>          数据库主机（默认: 127.0.0.1）
  --db-port <port>          数据库端口（MySQL:3306, 达梦:5236）
  --db-name <name>          数据库名称（默认: data_agent）
  --db-user <user>          数据库用户（默认: root）
  --db-password <pass>      数据库密码
  --backend-port <port>     后端端口（默认: 8065）
  --frontend-port <port>    前端端口（默认: 80）
  --vector-store <type>     向量库类型: simple | milvus（默认: simple）
  --skip-deps               跳过依赖安装
  --help                    显示此帮助信息

示例:
  # 基本安装
  $0 --output-dir ./output

  # 指定数据库配置
  $0 --output-dir ./output \\
     --db-type mysql \\
     --db-host 192.168.1.100 \\
     --db-name dataagent \\
     --db-user admin \\
     --db-password secret123

  # 使用达梦数据库
  $0 --output-dir ./output \\
     --db-type dameng \\
     --db-host 192.168.1.200 \\
     --db-port 5236
EOF
    exit 0
}

# ============================================================================
# 解析命令行参数
# ============================================================================
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --output-dir)
                OUTPUT_DIR="$2"
                shift 2
                ;;
            --deploy-dir)
                DEPLOY_DIR="$2"
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
            --skip-deps)
                SKIP_DEPS=true
                shift
                ;;
            --help)
                show_help
                ;;
            *)
                error "未知参数: $1\n使用 --help 查看帮助信息"
                ;;
        esac
    done
    
    # 验证必需参数
    if [ -z "$OUTPUT_DIR" ]; then
        error "缺少必需参数: --output-dir\n使用 --help 查看帮助信息"
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
# 检测操作系统
# ============================================================================
detect_os() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
    else
        error "无法检测操作系统类型"
    fi
    
    info "检测到操作系统: $OS $OS_VERSION"
    
    # 验证支持的操作系统
    case $OS in
        centos|rhel|kylin)
            info "✅ 支持的操作系统"
            ;;
        ubuntu|debian)
            warn "实验性支持的操作系统，可能存在兼容性问题"
            ;;
        *)
            error "不支持的操作系统: $OS"
            ;;
    esac
}

# ============================================================================
# 验证 output 目录
# ============================================================================
validate_output_dir() {
    step "验证编译输出目录"
    
    if [ ! -d "$OUTPUT_DIR" ]; then
        error "输出目录不存在: $OUTPUT_DIR"
    fi
    
    # 检查必需文件
    if [ ! -f "$OUTPUT_DIR/backend/dataagent-backend.jar" ]; then
        error "后端 JAR 文件不存在: $OUTPUT_DIR/backend/dataagent-backend.jar"
    fi
    
    if [ ! -d "$OUTPUT_DIR/frontend" ] || [ ! -f "$OUTPUT_DIR/frontend/index.html" ]; then
        error "前端文件不存在或不完整: $OUTPUT_DIR/frontend/"
    fi
    
    if [ ! -f "$OUTPUT_DIR/config/application.yml.template" ]; then
        error "配置模板不存在: $OUTPUT_DIR/config/application.yml.template"
    fi
    
    info "✅ 输出目录验证通过"
    
    # 显示版本信息
    if [ -f "$OUTPUT_DIR/VERSION.txt" ]; then
        echo ""
        info "构建版本信息:"
        cat "$OUTPUT_DIR/VERSION.txt" | grep -E "Build Time|Backend JAR|JAR Size" | sed 's/^/  /'
    fi
}

# ============================================================================
# 检查并安装 Java 17
# ============================================================================
check_install_java() {
    if [ "$SKIP_DEPS" = true ]; then
        info "跳过 Java 检查（--skip-deps）"
        return 0
    fi
    
    step "检查 Java 环境"
    
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -ge 17 ]; then
            info "Java 已安装: $(java -version 2>&1 | head -n1)"
            return 0
        else
            warn "Java 版本过低 (需要 17+)，将安装 Java 17"
        fi
    else
        warn "Java 未安装，将自动安装 Java 17"
    fi
    
    # 安装 Java 17
    case $OS in
        ubuntu|debian)
            info "使用 apt 安装 OpenJDK 17..."
            sudo apt-get update
            sudo apt-get install -y openjdk-17-jdk
            ;;
        centos|rhel|kylin)
            info "使用 yum 安装 OpenJDK 17..."
            sudo yum install -y java-17-openjdk java-17-openjdk-devel
            ;;
        *)
            error "不支持的操作系统: $OS"
            ;;
    esac
    
    # 设置 JAVA_HOME
    JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    export JAVA_HOME
    export PATH=$JAVA_HOME/bin:$PATH
    
    info "✅ Java 安装完成: $(java -version 2>&1 | head -n1)"
}

# ============================================================================
# 检查并安装 Nginx
# ============================================================================
check_install_nginx() {
    if [ "$SKIP_DEPS" = true ]; then
        info "跳过 Nginx 检查（--skip-deps）"
        return 0
    fi
    
    step "检查 Nginx"
    
    if command -v nginx &> /dev/null; then
        info "Nginx 已安装: $(nginx -v 2>&1)"
        return 0
    fi
    
    warn "Nginx 未安装，将自动安装"
    
    case $OS in
        ubuntu|debian)
            info "使用 apt 安装 Nginx..."
            sudo apt-get update
            sudo apt-get install -y nginx
            ;;
        centos|rhel|kylin)
            info "使用 yum 安装 Nginx..."
            sudo yum install -y nginx
            ;;
        *)
            error "不支持的操作系统: $OS"
            ;;
    esac
    
    # 启动 Nginx
    sudo systemctl enable nginx
    sudo systemctl start nginx
    
    info "✅ Nginx 安装完成: $(nginx -v 2>&1)"
}

# ============================================================================
# 检查数据库
# ============================================================================
check_database() {
    step "检查数据库连接"
    
    if [ "$DB_TYPE" = "dameng" ] || [ "$DB_TYPE" = "dm" ]; then
        check_dameng
    else
        check_mysql
    fi
}

check_mysql() {
    info "检查 MySQL 连接..."
    
    if ! command -v mysql &> /dev/null; then
        warn "MySQL 客户端未安装，跳过数据库连接测试"
        warn "请确保 MySQL 服务已启动并已执行数据库初始化脚本"
        warn "初始化脚本位置: $OUTPUT_DIR/config/sql/mysql/"
        return 0
    fi
    
    # 测试数据库连接
    if [ -n "$DB_PASSWORD" ]; then
        MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASSWORD"
    else
        MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER"
    fi
    
    if $MYSQL_CMD -e "USE $DB_NAME;" 2>/dev/null; then
        info "✅ 数据库连接正常"
    else
        warn "数据库连接失败或数据库不存在"
        warn "首次部署请手动执行数据库初始化："
        warn "  $MYSQL_CMD < $OUTPUT_DIR/config/sql/mysql/schema.sql"
    fi
}

check_dameng() {
    info "检查达梦数据库连接..."
    
    if ! command -v disql &> /dev/null; then
        warn "达梦数据库客户端未安装，跳过数据库连接测试"
        warn "请确保达梦数据库服务已启动并已执行数据库初始化脚本"
        warn "初始化脚本位置: $OUTPUT_DIR/config/sql/dameng/"
        return 0
    fi
    
    # 测试数据库连接
    if echo "SELECT 1 FROM DUAL;" | disql $DB_USER/\"$DB_PASSWORD\"@$DB_HOST:$DB_PORT &>/dev/null; then
        info "✅ 数据库连接正常"
    else
        warn "数据库连接失败或数据库不存在"
        warn "首次部署请手动执行数据库初始化："
        warn "  disql $DB_USER/\"$DB_PASSWORD\"@$DB_HOST:$DB_PORT < $OUTPUT_DIR/config/sql/dameng/schema.sql"
    fi
}

# ============================================================================
# 配置防火墙
# ============================================================================
configure_firewall() {
    step "配置防火墙"
    
    # 检测防火墙类型
    if command -v firewall-cmd &> /dev/null; then
        if systemctl is-active --quiet firewalld; then
            info "检测到 firewalld 正在运行"
            
            # 开放端口
            sudo firewall-cmd --permanent --add-port=$FRONTEND_PORT/tcp
            sudo firewall-cmd --permanent --add-port=$BACKEND_PORT/tcp
            sudo firewall-cmd --reload
            
            info "✅ 已开放端口: $FRONTEND_PORT, $BACKEND_PORT"
        else
            info "firewalld 未运行，跳过防火墙配置"
        fi
    elif command -v iptables &> /dev/null; then
        if sudo iptables -L INPUT -n | grep -q "DROP\|REJECT"; then
            warn "检测到 iptables 限制规则，请手动添加以下规则："
            warn "  sudo iptables -I INPUT -p tcp --dport $FRONTEND_PORT -j ACCEPT"
            warn "  sudo iptables -I INPUT -p tcp --dport $BACKEND_PORT -j ACCEPT"
        else
            info "iptables 未启用限制，跳过配置"
        fi
    else
        info "未检测到防火墙，跳过配置"
    fi
}

# ============================================================================
# 停止服务
# ============================================================================
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

# ============================================================================
# 备份旧版本
# ============================================================================
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

# ============================================================================
# 部署后端
# ============================================================================
deploy_backend() {
    step "部署后端"
    
    # 创建部署目录
    sudo mkdir -p "$DEPLOY_DIR"
    sudo chown -R $USER:$USER "$DEPLOY_DIR"
    
    # 复制后端 JAR
    cp "$OUTPUT_DIR/backend/dataagent-backend.jar" "$DEPLOY_DIR/"
    info "✅ 后端 JAR 已部署"
    
    # 生成配置文件
    info "生成配置文件..."
    cp "$OUTPUT_DIR/config/application.yml.template" "$DEPLOY_DIR/application.yml"
    
    # 转义特殊字符
    ESC_DB_USER=$(echo "$DB_USER" | sed 's/[&/|]/\\&/g')
    ESC_DB_PASS=$(echo "$DB_PASSWORD" | sed 's/[&/|]/\\&/g')
    ESC_DB_HOST=$(echo "$DB_HOST" | sed 's/[&/|]/\\&/g')
    ESC_DB_NAME=$(echo "$DB_NAME" | sed 's/[&/|]/\\&/g')
    
    # 根据数据库类型替换配置
    if [ "$DB_TYPE" = "dameng" ] || [ "$DB_TYPE" = "dm" ]; then
        info "应用达梦数据库配置..."
        sed -i -e "s|platform: mysql|platform: dameng|g" \
               -e "s|url: jdbc:mysql://127.0.0.1:3306/data_agent.*|url: jdbc:dm://$ESC_DB_HOST:$DB_PORT?zeroDateTimeBehavior=convertToNull\&useUnicode=true\&characterEncoding=UTF-8|g" \
               -e "s|driver-class-name: com.mysql.cj.jdbc.Driver|driver-class-name: dm.jdbc.driver.DmDriver|g" \
               -e "s|validation-query: SELECT 1|validation-query: SELECT 1 FROM DUAL|g" \
               -e "s|username: cyl|username: $ESC_DB_USER|g" \
               -e "s|password: Audaque@123|password: $ESC_DB_PASS|g" \
               "$DEPLOY_DIR/application.yml"
    else
        info "应用 MySQL 数据库配置..."
        sed -i -e "s|url: jdbc:mysql://127.0.0.1:3306/data_agent|url: jdbc:mysql://$ESC_DB_HOST:$DB_PORT/$ESC_DB_NAME|g" \
               -e "s|username: cyl|username: $ESC_DB_USER|g" \
               -e "s|password: Audaque@123|password: $ESC_DB_PASS|g" \
               "$DEPLOY_DIR/application.yml"
    fi
    
    # 配置向量库
    info "配置向量库类型: $VECTOR_STORE_TYPE"
    sed -i "s|type: simple|type: $VECTOR_STORE_TYPE|g" "$DEPLOY_DIR/application.yml"
    
    if [ "$VECTOR_STORE_TYPE" = "milvus" ]; then
        info "开启 Milvus 自动配置..."
        sed -i "s|\${VECTOR_STORE_EXCLUDE:.*}| |g" "$DEPLOY_DIR/application.yml"
        
        # 取消注释 Milvus 配置
        sed -i "/# milvus:/s/^      # //" "$DEPLOY_DIR/application.yml"
        sed -i "/#   enabled:/s/^      # //" "$DEPLOY_DIR/application.yml"
        sed -i "/#   client:/s/^      # //" "$DEPLOY_DIR/application.yml"
        sed -i "/#     host:/s/^      # //" "$DEPLOY_DIR/application.yml"
        sed -i "/#     port:/s/^      # //" "$DEPLOY_DIR/application.yml"
        sed -i "/#   collection-name:/s/^      # //" "$DEPLOY_DIR/application.yml"
        
        sed -i "s|host: localhost|host: $MILVUS_HOST|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|port: 19530|port: $MILVUS_PORT|g" "$DEPLOY_DIR/application.yml"
        sed -i "s|collection-name: data_agent_vector|collection-name: $MILVUS_COLLECTION|g" "$DEPLOY_DIR/application.yml"
    fi
    
    info "✅ 后端部署完成"
}

# ============================================================================
# 部署前端
# ============================================================================
deploy_frontend() {
    step "部署前端"
    
    # 创建前端目录
    sudo mkdir -p /var/www/dataagent
    
    # 复制前端文件
    sudo rm -rf /var/www/dataagent/*
    sudo cp -r "$OUTPUT_DIR/frontend/"* /var/www/dataagent/
    
    info "✅ 前端文件已部署到: /var/www/dataagent/"
    
    # 配置 Nginx
    configure_nginx
}

# ============================================================================
# 配置 Nginx
# ============================================================================
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
    NGINX_TEMPLATE="$OUTPUT_DIR/config/nginx-dataagent.conf.template"
    NGINX_LOCAL_CONF="$DEPLOY_DIR/nginx-dataagent.conf"
    
    if [ ! -f "$NGINX_TEMPLATE" ]; then
        warn "Nginx 配置模板不存在，跳过 Nginx 配置"
        return 0
    fi
    
    cp "$NGINX_TEMPLATE" "$NGINX_LOCAL_CONF"
    
    # 替换配置参数
    sed -i "s|server 127.0.0.1:8065|server 127.0.0.1:$BACKEND_PORT|g" "$NGINX_LOCAL_CONF"
    sed -i "s|listen 80;|listen $FRONTEND_PORT;|g" "$NGINX_LOCAL_CONF"
    sed -i "s|server_name .*;|server_name $HOST_IP localhost;|g" "$NGINX_LOCAL_CONF"
    sed -i "s|root .*;|root /var/www/dataagent;|g" "$NGINX_LOCAL_CONF"
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

# ============================================================================
# 启动后端服务
# ============================================================================
start_backend() {
    step "启动后端服务"
    
    cd "$DEPLOY_DIR"
    
    # 后台启动
    nohup java -Xmx4g -Xms2g \
        -XX:+UseG1GC \
        -Dfile.encoding=UTF-8 \
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

# ============================================================================
# 验证服务
# ============================================================================
verify_services() {
    step "验证服务状态"
    
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
    info "  服务验证"
    info "=========================================="
    
    # 验证后端
    if curl -s http://localhost:$BACKEND_PORT/api/agent/list > /dev/null 2>&1; then
        info "✅ 后端服务正常: http://$HOST_IP:$BACKEND_PORT"
    else
        warn "❌ 后端服务异常"
    fi
    
    # 验证前端
    if curl -s http://localhost:$FRONTEND_PORT > /dev/null 2>&1; then
        info "✅ 前端服务正常: http://$HOST_IP:$FRONTEND_PORT"
    else
        warn "❌ 前端服务异常"
    fi
}

# ============================================================================
# 显示安装摘要
# ============================================================================
show_summary() {
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
    info "  安装完成！"
    info "=========================================="
    echo ""
    info "访问地址:"
    info "  前端: http://$HOST_IP:$FRONTEND_PORT"
    info "  后端: http://$HOST_IP:$BACKEND_PORT"
    info "  健康检查: http://$HOST_IP:$BACKEND_PORT/actuator/health"
    echo ""
    info "管理命令:"
    info "  查看后端日志: tail -f $DEPLOY_DIR/logs/application.log"
    info "  查看前端日志: sudo tail -f /var/log/nginx/access.log"
    info "  停止后端: kill \$(cat $DEPLOY_DIR/backend.pid)"
    info "  重启 Nginx: sudo systemctl restart nginx"
    echo ""
    info "配置信息:"
    info "  部署目录: $DEPLOY_DIR"
    info "  数据库类型: $DB_TYPE"
    info "  数据库地址: $DB_HOST:$DB_PORT"
    info "  向量库类型: $VECTOR_STORE_TYPE"
    info "=========================================="
}

# ============================================================================
# 主流程
# ============================================================================
main() {
    echo ""
    info "=========================================="
    info "  DataAgent Linux 安装脚本"
    info "=========================================="
    echo ""
    
    # 解析参数
    parse_arguments "$@"
    
    # 检测操作系统
    detect_os
    
    # 验证 output 目录
    validate_output_dir
    
    # 检查并安装依赖
    check_install_java
    check_install_nginx
    check_database
    
    # 配置防火墙
    configure_firewall
    
    # 停止服务
    stop_services
    
    # 备份旧版本
    backup_old_version
    
    # 部署
    deploy_backend
    deploy_frontend
    
    # 启动服务
    start_backend
    
    # 验证
    verify_services
    
    # 显示摘要
    show_summary
}

# 执行主流程
main "$@"
