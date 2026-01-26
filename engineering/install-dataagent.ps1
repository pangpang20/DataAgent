################################################################################
# DataAgent 安装脚本 (Windows)
################################################################################
# 功能：
# 1. 从 output 目录读取编译好的前后端文件
# 2. 检查所需环境（Java 17）
# 3. 部署前后端
# 4. 配置并启动服务
#
# 使用说明：
# .\install-dataagent.ps1 -OutputDir <output_path> [options]
#
# 参数：
#   -OutputDir <path>     - 编译输出目录（必需）
#   -DeployDir <path>     - 部署目录（可选，默认 C:\DataAgent）
#   -DbType <type>        - 数据库类型：mysql | dameng（可选，默认 mysql）
#   -DbHost <host>        - 数据库主机（可选，默认 127.0.0.1）
#   -DbPort <port>        - 数据库端口（可选）
#   -DbName <name>        - 数据库名称（可选）
#   -DbUser <user>        - 数据库用户（可选）
#   -DbPassword <pass>    - 数据库密码（可选）
#   -BackendPort <port>   - 后端端口（可选，默认 8065）
#   -FrontendPort <port>  - 前端端口（可选，默认 8080）
#   -VectorStore <type>   - 向量库类型：simple | milvus（可选，默认 simple）
#   -SkipDeps             - 跳过依赖检查
#   -Help                 - 显示帮助信息
################################################################################

param(
    [string]$OutputDir = "",
    [string]$DeployDir = "C:\DataAgent",
    [string]$DbType = "mysql",
    [string]$DbHost = "127.0.0.1",
    [string]$DbPort = "",
    [string]$DbName = "",
    [string]$DbUser = "",
    [string]$DbPassword = "",
    [int]$BackendPort = 8065,
    [int]$FrontendPort = 8080,
    [string]$VectorStore = "simple",
    [string]$MilvusHost = "127.0.0.1",
    [int]$MilvusPort = 19530,
    [string]$MilvusCollection = "data_agent",
    [switch]$SkipDeps,
    [switch]$Help
)

# ============================================================================
# 颜色输出函数
# ============================================================================
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    exit 1
}

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Blue
    Write-Host "  $Message" -ForegroundColor Blue
    Write-Host "========================================" -ForegroundColor Blue
}

# ============================================================================
# 显示帮助信息
# ============================================================================
function Show-Help {
    @"
DataAgent Windows 安装脚本

使用方法:
  .\install-dataagent.ps1 -OutputDir <output_path> [options]

必需参数:
  -OutputDir <path>       编译输出目录

可选参数:
  -DeployDir <path>       部署目录（默认: C:\DataAgent）
  -DbType <type>          数据库类型: mysql | dameng（默认: mysql）
  -DbHost <host>          数据库主机（默认: 127.0.0.1）
  -DbPort <port>          数据库端口（MySQL:3306, 达梦:5236）
  -DbName <name>          数据库名称（默认: data_agent）
  -DbUser <user>          数据库用户（默认: root）
  -DbPassword <pass>      数据库密码
  -BackendPort <port>     后端端口（默认: 8065）
  -FrontendPort <port>    前端端口（默认: 8080，Windows 不建议使用 80）
  -VectorStore <type>     向量库类型: simple | milvus（默认: simple）
  -SkipDeps               跳过依赖检查
  -Help                   显示此帮助信息

示例:
  # 基本安装
  .\install-dataagent.ps1 -OutputDir .\output

  # 指定数据库配置
  .\install-dataagent.ps1 -OutputDir .\output ``
     -DbType mysql ``
     -DbHost 192.168.1.100 ``
     -DbName dataagent ``
     -DbUser admin ``
     -DbPassword secret123

  # 使用达梦数据库
  .\install-dataagent.ps1 -OutputDir .\output ``
     -DbType dameng ``
     -DbHost 192.168.1.200 ``
     -DbPort 5236
"@
    exit 0
}

# ============================================================================
# 初始化参数
# ============================================================================
function Initialize-Parameters {
    if ($Help) {
        Show-Help
    }
    
    if ([string]::IsNullOrEmpty($OutputDir)) {
        Write-Error-Custom "缺少必需参数: -OutputDir`n使用 -Help 查看帮助信息"
    }
    
    # 转换为绝对路径
    $script:OutputDir = [System.IO.Path]::GetFullPath($OutputDir)
    $script:DeployDir = [System.IO.Path]::GetFullPath($DeployDir)
    
    # 设置数据库默认值
    if ([string]::IsNullOrEmpty($DbPort)) {
        if ($DbType -eq "dameng" -or $DbType -eq "dm") {
            $script:DbPort = "5236"
        } else {
            $script:DbPort = "3306"
        }
    }
    
    if ([string]::IsNullOrEmpty($DbName)) {
        $script:DbName = "data_agent"
    }
    
    if ([string]::IsNullOrEmpty($DbUser)) {
        if ($DbType -eq "dameng" -or $DbType -eq "dm") {
            $script:DbUser = "SYSDBA"
        } else {
            $script:DbUser = "root"
        }
    }
}

# ============================================================================
# 检查管理员权限
# ============================================================================
function Test-Administrator {
    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($currentUser)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# ============================================================================
# 验证 output 目录
# ============================================================================
function Test-OutputDirectory {
    Write-Step "验证编译输出目录"
    
    if (-not (Test-Path $OutputDir)) {
        Write-Error-Custom "输出目录不存在: $OutputDir"
    }
    
    # 检查必需文件
    $backendJar = Join-Path $OutputDir "backend\dataagent-backend.jar"
    if (-not (Test-Path $backendJar)) {
        Write-Error-Custom "后端 JAR 文件不存在: $backendJar"
    }
    
    $frontendIndex = Join-Path $OutputDir "frontend\index.html"
    if (-not (Test-Path $frontendIndex)) {
        Write-Error-Custom "前端文件不存在或不完整: $(Join-Path $OutputDir 'frontend')"
    }
    
    $configTemplate = Join-Path $OutputDir "config\application.yml.template"
    if (-not (Test-Path $configTemplate)) {
        Write-Error-Custom "配置模板不存在: $configTemplate"
    }
    
    Write-Info "✅ 输出目录验证通过"
    
    # 显示版本信息
    $versionFile = Join-Path $OutputDir "VERSION.txt"
    if (Test-Path $versionFile) {
        Write-Host ""
        Write-Info "构建版本信息:"
        Get-Content $versionFile | Select-String "Build Time|Backend JAR|JAR Size" | ForEach-Object {
            Write-Host "  $_"
        }
    }
}

# ============================================================================
# 检查 Java 环境
# ============================================================================
function Test-JavaEnvironment {
    if ($SkipDeps) {
        Write-Info "跳过 Java 检查（-SkipDeps）"
        return
    }
    
    Write-Step "检查 Java 环境"
    
    try {
        $javaVersion = java -version 2>&1 | Select-String "version" | ForEach-Object { $_ -replace '.*"(\d+).*', '$1' }
        
        if ([int]$javaVersion -ge 17) {
            $fullVersion = (java -version 2>&1)[0]
            Write-Info "Java 已安装: $fullVersion"
            return
        } else {
            Write-Warn "Java 版本过低 (需要 17+)"
        }
    } catch {
        Write-Warn "Java 未安装"
    }
    
    Write-Error-Custom @"
请手动安装 Java 17 或更高版本：
1. 下载 OpenJDK 17: https://adoptium.net/
2. 安装后设置 JAVA_HOME 环境变量
3. 将 %JAVA_HOME%\bin 添加到 PATH
4. 重新运行此脚本
"@
}

# ============================================================================
# 检查数据库连接
# ============================================================================
function Test-DatabaseConnection {
    Write-Step "检查数据库连接"
    
    if ($DbType -eq "dameng" -or $DbType -eq "dm") {
        Test-DamengConnection
    } else {
        Test-MySqlConnection
    }
}

function Test-MySqlConnection {
    Write-Info "检查 MySQL 连接..."
    
    # 检查 MySQL 客户端
    $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysqlCmd) {
        Write-Warn "MySQL 客户端未安装，跳过数据库连接测试"
        Write-Warn "请确保 MySQL 服务已启动并已执行数据库初始化脚本"
        Write-Warn "初始化脚本位置: $(Join-Path $OutputDir 'config\sql\mysql')"
        return
    }
    
    # 测试连接
    $mysqlArgs = "-h$DbHost", "-P$DbPort", "-u$DbUser"
    if (-not [string]::IsNullOrEmpty($DbPassword)) {
        $mysqlArgs += "-p$DbPassword"
    }
    $mysqlArgs += "-e", "USE $DbName;"
    
    try {
        & mysql $mysqlArgs 2>$null
        Write-Info "✅ 数据库连接正常"
    } catch {
        Write-Warn "数据库连接失败或数据库不存在"
        Write-Warn "首次部署请手动执行数据库初始化："
        Write-Warn "  mysql $($mysqlArgs -join ' ') < $(Join-Path $OutputDir 'config\sql\mysql\schema.sql')"
    }
}

function Test-DamengConnection {
    Write-Info "检查达梦数据库连接..."
    
    # 检查达梦客户端
    $disqlCmd = Get-Command disql -ErrorAction SilentlyContinue
    if (-not $disqlCmd) {
        Write-Warn "达梦数据库客户端未安装，跳过数据库连接测试"
        Write-Warn "请确保达梦数据库服务已启动并已执行数据库初始化脚本"
        Write-Warn "初始化脚本位置: $(Join-Path $OutputDir 'config\sql\dameng')"
        return
    }
    
    # 测试连接
    try {
        echo "SELECT 1 FROM DUAL;" | disql "$DbUser/`"$DbPassword`"@$DbHost`:$DbPort" 2>$null | Out-Null
        Write-Info "✅ 数据库连接正常"
    } catch {
        Write-Warn "数据库连接失败或数据库不存在"
        Write-Warn "首次部署请手动执行数据库初始化"
    }
}

# ============================================================================
# 配置防火墙
# ============================================================================
function Set-FirewallRules {
    Write-Step "配置防火墙"
    
    if (-not (Test-Administrator)) {
        Write-Warn "需要管理员权限才能配置防火墙规则"
        Write-Warn "请手动添加防火墙规则或以管理员身份运行脚本"
        return
    }
    
    try {
        # 添加后端端口规则
        $ruleName = "DataAgent Backend Port $BackendPort"
        $existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
        if (-not $existingRule) {
            New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort $BackendPort -Action Allow | Out-Null
            Write-Info "✅ 已开放后端端口: $BackendPort"
        } else {
            Write-Info "后端端口规则已存在: $BackendPort"
        }
        
        # 添加前端端口规则
        $ruleName = "DataAgent Frontend Port $FrontendPort"
        $existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
        if (-not $existingRule) {
            New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort $FrontendPort -Action Allow | Out-Null
            Write-Info "✅ 已开放前端端口: $FrontendPort"
        } else {
            Write-Info "前端端口规则已存在: $FrontendPort"
        }
    } catch {
        Write-Warn "配置防火墙规则失败: $_"
        Write-Warn "请手动添加防火墙规则"
    }
}

# ============================================================================
# 停止服务
# ============================================================================
function Stop-Services {
    Write-Step "停止现有服务"
    
    # 停止后端服务
    $pidFile = Join-Path $DeployDir "backend.pid"
    if (Test-Path $pidFile) {
        $pid = Get-Content $pidFile
        $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Info "停止后端服务 (PID: $pid)..."
            Stop-Process -Id $pid -Force
            Start-Sleep -Seconds 2
        }
    }
    
    # 检查端口占用
    $connections = Get-NetTCPConnection -LocalPort $BackendPort -ErrorAction SilentlyContinue
    if ($connections) {
        foreach ($conn in $connections) {
            $pid = $conn.OwningProcess
            Write-Warn "端口 $BackendPort 仍被占用 (PID: $pid)，强制停止..."
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        }
    }
    
    Write-Info "✅ 服务已停止"
}

# ============================================================================
# 备份旧版本
# ============================================================================
function Backup-OldVersion {
    Write-Step "备份旧版本"
    
    $backupDir = Join-Path $DeployDir "backup\$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    $backendJar = Join-Path $DeployDir "dataagent-backend.jar"
    
    if ((Test-Path $DeployDir) -and (Test-Path $backendJar)) {
        New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
        
        # 备份后端
        if (Test-Path $backendJar) {
            Copy-Item $backendJar $backupDir
        }
        
        # 备份配置
        $configFile = Join-Path $DeployDir "application.yml"
        if (Test-Path $configFile) {
            Copy-Item $configFile $backupDir
        }
        
        Write-Info "✅ 已备份到: $backupDir"
    } else {
        Write-Info "未发现旧版本，跳过备份"
    }
}

# ============================================================================
# 部署后端
# ============================================================================
function Deploy-Backend {
    Write-Step "部署后端"
    
    # 创建部署目录
    if (-not (Test-Path $DeployDir)) {
        New-Item -ItemType Directory -Path $DeployDir -Force | Out-Null
    }
    
    # 复制后端 JAR
    $sourceJar = Join-Path $OutputDir "backend\dataagent-backend.jar"
    $targetJar = Join-Path $DeployDir "dataagent-backend.jar"
    Copy-Item $sourceJar $targetJar -Force
    Write-Info "✅ 后端 JAR 已部署"
    
    # 生成配置文件
    Write-Info "生成配置文件..."
    $configTemplate = Join-Path $OutputDir "config\application.yml.template"
    $configFile = Join-Path $DeployDir "application.yml"
    
    $config = Get-Content $configTemplate -Raw
    
    # 转义正则表达式特殊字符
    function Escape-RegexChars {
        param([string]$text)
        return [regex]::Escape($text)
    }
    
    # 根据数据库类型替换配置
    if ($DbType -eq "dameng" -or $DbType -eq "dm") {
        Write-Info "应用达梦数据库配置..."
        $config = $config -replace "platform: mysql", "platform: dameng"
        $config = $config -replace "url: jdbc:mysql://127\.0\.0\.1:3306/data_agent.*", "url: jdbc:dm://$DbHost`:$DbPort`?zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=UTF-8"
        $config = $config -replace "driver-class-name: com\.mysql\.cj\.jdbc\.Driver", "driver-class-name: dm.jdbc.driver.DmDriver"
        $config = $config -replace "validation-query: SELECT 1", "validation-query: SELECT 1 FROM DUAL"
        $config = $config -replace "username: cyl", "username: $DbUser"
        $config = $config -replace "password: Audaque@123", "password: $DbPassword"
    } else {
        Write-Info "应用 MySQL 数据库配置..."
        $config = $config -replace "url: jdbc:mysql://127\.0\.0\.1:3306/data_agent", "url: jdbc:mysql://$DbHost`:$DbPort/$DbName"
        $config = $config -replace "username: cyl", "username: $DbUser"
        $config = $config -replace "password: Audaque@123", "password: $DbPassword"
    }
    
    # 配置向量库
    Write-Info "配置向量库类型: $VectorStore"
    $config = $config -replace "type: simple", "type: $VectorStore"
    
    if ($VectorStore -eq "milvus") {
        Write-Info "开启 Milvus 自动配置..."
        $config = $config -replace '\$\{VECTOR_STORE_EXCLUDE:.*\}', ' '
        
        # 取消注释 Milvus 配置
        $config = $config -replace "      # milvus:", "      milvus:"
        $config = $config -replace "      #   enabled:", "        enabled:"
        $config = $config -replace "      #   client:", "        client:"
        $config = $config -replace "      #     host:", "          host:"
        $config = $config -replace "      #     port:", "          port:"
        $config = $config -replace "      #   collection-name:", "        collection-name:"
        
        $config = $config -replace "host: localhost", "host: $MilvusHost"
        $config = $config -replace "port: 19530", "port: $MilvusPort"
        $config = $config -replace "collection-name: data_agent_vector", "collection-name: $MilvusCollection"
    }
    
    # 保存配置文件
    $config | Set-Content -Path $configFile -Encoding UTF8
    
    Write-Info "✅ 后端部署完成"
}

# ============================================================================
# 部署前端
# ============================================================================
function Deploy-Frontend {
    Write-Step "部署前端"
    
    $frontendDir = Join-Path $DeployDir "frontend"
    
    # 创建前端目录
    if (Test-Path $frontendDir) {
        Remove-Item $frontendDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $frontendDir -Force | Out-Null
    
    # 复制前端文件
    $sourceDir = Join-Path $OutputDir "frontend\*"
    Copy-Item $sourceDir $frontendDir -Recurse -Force
    
    Write-Info "✅ 前端文件已部署到: $frontendDir"
    
    # 配置前端服务（使用 Python HTTP Server 或其他简单 Web 服务器）
    Write-Info ""
    Write-Info "前端部署说明："
    Write-Info "  Windows 环境建议使用以下方式之一运行前端："
    Write-Info "  1. 使用 Python: python -m http.server $FrontendPort --directory `"$frontendDir`""
    Write-Info "  2. 使用 Node.js: npx http-server `"$frontendDir`" -p $FrontendPort"
    Write-Info "  3. 安装 Nginx for Windows 并配置（高级用户）"
}

# ============================================================================
# 启动后端服务
# ============================================================================
function Start-Backend {
    Write-Step "启动后端服务"
    
    $jarFile = Join-Path $DeployDir "dataagent-backend.jar"
    
    # 启动后端服务
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = "java"
    $startInfo.Arguments = "-Xmx4g -Xms2g -XX:+UseG1GC -Dfile.encoding=UTF-8 -jar `"$jarFile`""
    $startInfo.WorkingDirectory = $DeployDir
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $process.Start() | Out-Null
    
    $pid = $process.Id
    $pid | Set-Content (Join-Path $DeployDir "backend.pid")
    
    Write-Info "后端服务已启动 (PID: $pid)"
    
    # 等待服务启动
    Write-Info "等待后端服务启动..."
    for ($i = 1; $i -le 60; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$BackendPort/actuator/health" -UseBasicParsing -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                Write-Info "✅ 后端服务启动成功！"
                return
            }
        } catch {
            # 继续等待
        }
        
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$BackendPort/api/agent/list" -UseBasicParsing -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                Write-Info "✅ 后端服务启动成功！"
                return
            }
        } catch {
            # 继续等待
        }
        
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 1
    }
    
    Write-Host ""
    Write-Warn "后端服务启动超时，请查看日志: $(Join-Path $DeployDir 'logs\application.log')"
}

# ============================================================================
# 验证服务
# ============================================================================
function Test-Services {
    Write-Step "验证服务状态"
    
    $hostIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.PrefixOrigin -eq "Dhcp" -or $_.PrefixOrigin -eq "Manual" } | Select-Object -First 1).IPAddress
    if ([string]::IsNullOrEmpty($hostIp)) {
        $hostIp = "localhost"
    }
    
    Write-Host ""
    Write-Info "=========================================="
    Write-Info "  服务验证"
    Write-Info "=========================================="
    
    # 验证后端
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$BackendPort/api/agent/list" -UseBasicParsing -ErrorAction Stop
        Write-Info "✅ 后端服务正常: http://$hostIp`:$BackendPort"
    } catch {
        Write-Warn "❌ 后端服务异常"
    }
}

# ============================================================================
# 显示安装摘要
# ============================================================================
function Show-Summary {
    $hostIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.PrefixOrigin -eq "Dhcp" -or $_.PrefixOrigin -eq "Manual" } | Select-Object -First 1).IPAddress
    if ([string]::IsNullOrEmpty($hostIp)) {
        $hostIp = "localhost"
    }
    
    Write-Host ""
    Write-Info "=========================================="
    Write-Info "  安装完成！"
    Write-Info "=========================================="
    Write-Host ""
    Write-Info "访问地址:"
    Write-Info "  后端: http://$hostIp`:$BackendPort"
    Write-Info "  健康检查: http://$hostIp`:$BackendPort/actuator/health"
    Write-Host ""
    Write-Info "前端部署:"
    Write-Info "  前端目录: $(Join-Path $DeployDir 'frontend')"
    Write-Info "  启动命令（Python）: python -m http.server $FrontendPort --directory `"$(Join-Path $DeployDir 'frontend')`""
    Write-Info "  启动命令（Node.js）: npx http-server `"$(Join-Path $DeployDir 'frontend')`" -p $FrontendPort"
    Write-Host ""
    Write-Info "管理命令:"
    Write-Info "  查看后端日志: Get-Content `"$(Join-Path $DeployDir 'logs\application.log')`" -Tail 50 -Wait"
    Write-Info "  停止后端: Stop-Process -Id (Get-Content `"$(Join-Path $DeployDir 'backend.pid')`")"
    Write-Host ""
    Write-Info "配置信息:"
    Write-Info "  部署目录: $DeployDir"
    Write-Info "  数据库类型: $DbType"
    Write-Info "  数据库地址: $DbHost`:$DbPort"
    Write-Info "  向量库类型: $VectorStore"
    Write-Info "=========================================="
}

# ============================================================================
# 主流程
# ============================================================================
function Main {
    Write-Host ""
    Write-Info "=========================================="
    Write-Info "  DataAgent Windows 安装脚本"
    Write-Info "=========================================="
    Write-Host ""
    
    # 初始化参数
    Initialize-Parameters
    
    # 验证 output 目录
    Test-OutputDirectory
    
    # 检查环境
    Test-JavaEnvironment
    Test-DatabaseConnection
    
    # 配置防火墙
    Set-FirewallRules
    
    # 停止服务
    Stop-Services
    
    # 备份旧版本
    Backup-OldVersion
    
    # 部署
    Deploy-Backend
    Deploy-Frontend
    
    # 启动服务
    Start-Backend
    
    # 验证
    Test-Services
    
    # 显示摘要
    Show-Summary
}

# 执行主流程
Main
