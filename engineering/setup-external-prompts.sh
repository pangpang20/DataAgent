#!/bin/bash

# DataAgent 外部Prompt目录初始化脚本
# 用途: 从JAR包或源码复制Prompt模板到外部目录，实现无需重编译的热更新

set -e

# 默认配置
DEFAULT_PROMPT_DIR="/opt/dataagent/prompts"
SOURCE_PROMPTS_DIR="data-agent-management/src/main/resources/prompts"
JAR_FILE=""

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示使用说明
show_usage() {
    cat << EOF
使用方法:
  $0 [选项]

选项:
  -d, --dir <directory>     指定外部Prompt目录 (默认: ${DEFAULT_PROMPT_DIR})
  -j, --jar <jar-file>      从JAR包中提取Prompt模板
  -s, --source              从源码目录复制Prompt模板 (默认)
  -h, --help                显示此帮助信息

示例:
  # 从源码复制到默认目录
  $0

  # 从源码复制到自定义目录
  $0 -d /home/dataagent/prompts

  # 从JAR包提取到指定目录
  $0 -j data-agent-management/target/spring-ai-audaque-data-agent-management-1.0.0.jar -d /opt/dataagent/prompts

使用说明:
  1. 运行此脚本后，会在指定目录创建Prompt模板文件
  2. 启动DataAgent时，设置环境变量: export DATAAGENT_PROMPT_DIR=/opt/dataagent/prompts
  3. 或使用JVM参数: java -Ddataagent.prompt.dir=/opt/dataagent/prompts -jar ...
  4. 修改Prompt后，调用API热更新: curl -X POST http://localhost:8080/api/prompt-config/reload-all
EOF
}

# 解析命令行参数
PROMPT_DIR="${DEFAULT_PROMPT_DIR}"
USE_SOURCE=true

while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--dir)
            PROMPT_DIR="$2"
            shift 2
            ;;
        -j|--jar)
            JAR_FILE="$2"
            USE_SOURCE=false
            shift 2
            ;;
        -s|--source)
            USE_SOURCE=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "未知选项: $1"
            show_usage
            exit 1
            ;;
    esac
done

print_info "DataAgent 外部Prompt目录初始化工具"
print_info "目标目录: ${PROMPT_DIR}"

# 创建目标目录
if [ ! -d "${PROMPT_DIR}" ]; then
    print_info "创建目标目录: ${PROMPT_DIR}"
    mkdir -p "${PROMPT_DIR}"
else
    print_warn "目标目录已存在: ${PROMPT_DIR}"
    read -p "是否覆盖现有文件? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "已取消操作"
        exit 0
    fi
fi

# 从源码复制
if [ "$USE_SOURCE" = true ]; then
    if [ ! -d "${SOURCE_PROMPTS_DIR}" ]; then
        print_error "源码Prompt目录不存在: ${SOURCE_PROMPTS_DIR}"
        print_error "请确保在DataAgent项目根目录下运行此脚本"
        exit 1
    fi

    print_info "从源码复制Prompt模板..."
    cp -v "${SOURCE_PROMPTS_DIR}"/*.txt "${PROMPT_DIR}/"
    
    if [ $? -eq 0 ]; then
        print_info "Prompt模板复制成功!"
    else
        print_error "复制失败"
        exit 1
    fi
else
    # 从JAR包提取
    if [ -z "${JAR_FILE}" ]; then
        print_error "请指定JAR文件路径 (使用 -j 选项)"
        exit 1
    fi

    if [ ! -f "${JAR_FILE}" ]; then
        print_error "JAR文件不存在: ${JAR_FILE}"
        exit 1
    fi

    print_info "从JAR包提取Prompt模板: ${JAR_FILE}"
    
    # 创建临时目录
    TEMP_DIR=$(mktemp -d)
    trap "rm -rf ${TEMP_DIR}" EXIT

    # 解压JAR包中的prompts目录
    unzip -q "${JAR_FILE}" "BOOT-INF/classes/prompts/*.txt" -d "${TEMP_DIR}" 2>/dev/null
    
    if [ $? -ne 0 ]; then
        print_error "从JAR包提取失败，可能JAR包中不包含Prompt文件"
        exit 1
    fi

    # 复制到目标目录
    cp -v "${TEMP_DIR}"/BOOT-INF/classes/prompts/*.txt "${PROMPT_DIR}/"
    
    if [ $? -eq 0 ]; then
        print_info "Prompt模板提取成功!"
    else
        print_error "复制失败"
        exit 1
    fi
fi

# 统计文件数量
FILE_COUNT=$(ls -1 "${PROMPT_DIR}"/*.txt 2>/dev/null | wc -l)
print_info "共复制 ${FILE_COUNT} 个Prompt模板文件"

# 列出文件
print_info "Prompt模板列表:"
ls -lh "${PROMPT_DIR}"/*.txt

# 显示下一步操作
echo ""
print_info "====== 下一步操作 ======"
echo ""
echo "1. 配置环境变量 (选择其一):"
echo "   方式1 - 环境变量:"
echo "     export DATAAGENT_PROMPT_DIR=${PROMPT_DIR}"
echo ""
echo "   方式2 - JVM系统属性:"
echo "     java -Ddataagent.prompt.dir=${PROMPT_DIR} -jar dataagent-backend.jar"
echo ""
echo "   方式3 - systemd服务 (编辑 /etc/systemd/system/dataagent.service):"
echo "     Environment=\"DATAAGENT_PROMPT_DIR=${PROMPT_DIR}\""
echo ""
echo "2. 启动DataAgent服务"
echo ""
echo "3. 验证外部Prompt配置:"
echo "   curl http://localhost:8080/api/prompt-config/external-dir"
echo ""
echo "4. 修改Prompt后，热更新缓存:"
echo "   curl -X POST http://localhost:8080/api/prompt-config/reload-all"
echo ""
print_info "详细文档: docs/EXTERNAL_PROMPTS_GUIDE.md"
