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
# 步骤 6：比对并合并配置文件（仅添加新增配置项，跳过敏感配置）
# ----------------------------------------------------------------------------
log "检查配置文件更新..."

TEMPLATE_CONFIG="$PACKAGE_DIR/config/application.yml.template"
EXISTING_CONFIG="$DEPLOY_DIR/application.yml"

if [ -f "$TEMPLATE_CONFIG" ] && [ -f "$EXISTING_CONFIG" ]; then
    log "发现现有配置文件，开始比对新增配置项..."
    log "  Template: $TEMPLATE_CONFIG"
    log "  Existing: $EXISTING_CONFIG"

    # 导出环境变量供 Python 脚本使用
    export TEMPLATE_CONFIG
    export EXISTING_CONFIG

    # 使用 Python yaml 库进行可靠的 YAML 比对和合并
    python_output=$(python3 << 'PYTHON_MERGE_SCRIPT'
import yaml
import sys
import os

template_path = os.environ.get('TEMPLATE_CONFIG')
old_config_path = os.environ.get('EXISTING_CONFIG')

if not template_path or not old_config_path:
    print("ERROR: Missing environment variables")
    sys.exit(1)

if not os.path.exists(template_path):
    print(f"ERROR: Template file not found: {template_path}")
    sys.exit(1)

if not os.path.exists(old_config_path):
    print(f"ERROR: Old config file not found: {old_config_path}")
    sys.exit(1)

# 敏感配置模式（不更新）
SENSITIVE_PATTERNS = [
    'spring.datasource',
    'spring.data.redis',
    'spring.ai.vectorstore.milvus',
    'spring.ai.vectorstore.elasticsearch',
    'oss.access-key',
]

def is_sensitive(key):
    """判断是否为敏感配置"""
    return any(p in key for p in SENSITIVE_PATTERNS)

def flatten_yaml(data, parent_key='', sep='.'):
    """将 YAML 扁平化为 dot notation: {server.port: 8065, ...}"""
    items = {}
    if isinstance(data, dict):
        for k, v in data.items():
            new_key = f"{parent_key}{sep}{k}" if parent_key else k
            if isinstance(v, dict):
                items.update(flatten_yaml(v, new_key, sep))
            else:
                items[new_key] = v
    return items

def dict_to_yaml(data, indent=0):
    """将字典转换为带缩进的 YAML 字符串"""
    lines = []
    prefix = "  " * indent
    for key, value in data.items():
        if isinstance(value, dict):
            lines.append(f"{prefix}{key}:")
            lines.append(dict_to_yaml(value, indent + 1))
        else:
            # 格式化值
            if value is None:
                yaml_value = "null"
            elif isinstance(value, bool):
                yaml_value = str(value).lower()
            elif isinstance(value, str):
                # 特殊字符需要引号
                if any(c in value for c in [':', '#', '{', '}', '[', ']', '&', '*', '?', '|', '-', '<', '>', '!', '@', '%']):
                    yaml_value = f"'{value}'"
                else:
                    yaml_value = value
            else:
                yaml_value = str(value)
            lines.append(f"{prefix}{key}: {yaml_value}")
    return "\n".join(lines)

def group_by_parent(keys_values):
    """将扁平的 key-value 按父节点分组，重建层级结构"""
    result = {}
    for key, value in keys_values:
        parts = key.split('.')
        current = result
        for i, part in enumerate(parts[:-1]):
            if part not in current:
                current[part] = {}
            current = current[part]
        current[parts[-1]] = value
    return result

# 读取 YAML 文件
try:
    with open(template_path, 'r', encoding='utf-8') as f:
        template_data = yaml.safe_load(f)
    with open(old_config_path, 'r', encoding='utf-8') as f:
        old_data = yaml.safe_load(f)
except Exception as e:
    print(f"ERROR: Failed to load YAML files: {e}")
    sys.exit(1)

# 扁平化
template_flat = flatten_yaml(template_data)
old_flat = flatten_yaml(old_data)

print(f"Template 配置项数量：{len(template_flat)}")
print(f"Existing 配置项数量：{len(old_flat)}")

# 找出新增的配置项（排除敏感配置）
new_configs = []
for key, value in template_flat.items():
    if key not in old_flat:
        if is_sensitive(key):
            print(f"跳过敏感配置：{key}")
            continue
        # 排除包含敏感路径的配置
        if any(s in key for s in ['.url', '.username', '.password', '.host', '.port']):
            continue
        new_configs.append((key, value))

if not new_configs:
    print("NO_NEW_CONFIG")
    sys.exit(0)

print(f"发现 {len(new_configs)} 个新增配置项")

# 按父节点分组，生成结构化的 YAML
grouped_configs = {}
for key, value in new_configs:
    parts = key.split('.')
    # 取前两级作为分组依据 (如 alibaba.data-agent)
    group_key = '.'.join(parts[:-1]) if len(parts) > 1 else parts[0]
    if group_key not in grouped_configs:
        grouped_configs[group_key] = []
    grouped_configs[group_key].append((key, value))

# 追加到配置文件
try:
    with open(old_config_path, 'a', encoding='utf-8') as f:
        f.write("\n")
        f.write("# ============================================================================\n")
        f.write("# 新增配置项（由 update_dataagent.sh 自动添加，已跳过数据库/Milvus/Redis 等敏感配置）\n")
        f.write("# ============================================================================\n")

        for group_key, items in grouped_configs.items():
            # 按层级重建 YAML 结构
            config_dict = {}
            for key, value in items:
                # 提取相对于 group 的子路径
                if group_key:
                    sub_key = key[len(group_key)+1:] if len(group_key) < len(key) else key.split('.')[-1]
                else:
                    sub_key = key
                # 构建嵌套字典
                parts = sub_key.split('.')
                current = config_dict
                for i, part in enumerate(parts[:-1]):
                    if part not in current:
                        current[part] = {}
                    current = current[part]
                current[parts[-1]] = value

            # 生成 YAML 并写入
            f.write(f"\n# Group: {group_key}\n")
            yaml_content = dict_to_yaml(config_dict)
            f.write(yaml_content)
            f.write("\n")

    print(f"ADDED:{len(new_configs)}")
except Exception as e:
    print(f"ERROR: Failed to write config: {e}")
    sys.exit(1)
PYTHON_MERGE_SCRIPT
)
    python_exit_code=$?

    # 显示详细日志
    echo "$python_output" | while read -r line; do
        log "$line"
    done

    # 检查是否成功
    if echo "$python_output" | grep -q "^ERROR:"; then
        log "警告：配置合并执行出错，跳过配置更新"
    elif [ "$python_output" = "NO_NEW_CONFIG" ]; then
        log "✅ 配置已是最新，无需添加新配置项"
    elif echo "$python_output" | grep -q "^ADDED:"; then
        added_num=$(echo "$python_output" | grep "^ADDED:" | cut -d: -f2)
        if [ -n "$added_num" ] && [ "$added_num" -gt 0 ]; then
            log "✅ 已添加 $added_num 个新增配置项（已跳过数据库/Milvus/Redis 等敏感配置）"
        fi
    else
        log "警告：未检测到新增配置项"
    fi
elif [ ! -f "$EXISTING_CONFIG" ] && [ -f "$TEMPLATE_CONFIG" ]; then
    log "现有配置文件不存在，从模板复制..."
    cp "$TEMPLATE_CONFIG" "$EXISTING_CONFIG"
    log "✅ 已创建新配置文件"
elif [ ! -f "$TEMPLATE_CONFIG" ]; then
    log "警告：更新包中未找到配置模板文件，跳过配置比对"
fi

# ----------------------------------------------------------------------------
# 步骤 7：替换前端文件
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
# 步骤 8：清理临时文件
# ----------------------------------------------------------------------------
log "清理临时文件..."
rm -rf "$TEMP_DIR"

# ----------------------------------------------------------------------------
# 步骤 9：启动后端服务
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
# 步骤 10：重新加载 Nginx
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
# 步骤 11：显示摘要
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
