---
name: build-dataagent
description: 本地编译 DataAgent前后端项目，支持详细的编译步骤和部署。当用户要求编译 DataAgent、打包项目、本地构建或执行 mvnw编译时使用。
---

# DataAgent 本地编译技能

##概

本技能用于本地编译 DataAgent 项目的前后端，提供详细的手动编译步骤和自动化脚本支持。

##环境要求

- **Java**: 17 或更高版本
- **Node.js**: 16 或更高版本  
- **Yarn**: 包管理器
- **Maven**: 3.6.3+（项目包含 mvnw）
- **操作系统**: Windows/Linux/macOS

## 本地手动编译步骤

### 1.后端编译

使用 Maven Wrapper进后端编译：

```bash
# Windows系统
cd data-agent-management
./mvnw.cmd package -DskipTests

# Linux/macOS系统
cd data-agent-management
./mvnw package -DskipTests
```

编译输出位置：`data-agent-management/target/spring-ai-audaque-data-agent-management-*.jar`

### 2.前端编译

```bash
#进前端目录
cd data-agent-frontend

# 编译 Widget组件
export BUILD_WIDGET='true'
#安装依赖
yarn install

#编译构建
yarn build
```

编译输出位置：`data-agent-frontend/dist/`

## 自动化编译脚本

### 使用 build-dataagent.sh脚

```bash
#进工程目录
cd engineering

# 仅编译打包
./build-dataagent.sh [output_dir]

#编译并部署
./build-dataagent.sh --deploy \
  --deploy-dir /opt/dataagent \
  --db-type mysql \
  --db-user root \
  --db-password your_password
```

###参数说明

| 参数            | 说明                    | 默认值         |
| --------------- | ----------------------- | -------------- |
| `--deploy`      | 启用部署模式            | -              |
| `--deploy-dir`  | 部署目录                | /opt/dataagent |
| `--package-dir` | 打包文件目录            | ./output       |
| `--db-type`     | 数据库类型 mysql/dameng | mysql          |
| `--db-host`     | 数据库主机              | 127.0.0.1      |
| `--db-port`     | 数据库端口              | 3306/5236      |
| `--db-user`     | 数据库用户              | root           |
| `--db-password` | 数据库密码              | -              |

##编译输出结构

编译完成后生成的目录结构：

```
output/
├── backend/
│  └── dataagent-backend.jar    #后端 JAR包
├── frontend/                     # 前端静态文件
│   ├── index.html
│  └── assets/
├── config/
│   ├── application.yml.template  #配置模板
│   ├── nginx-dataagent.conf.template
│  └── sql/
│       ├── mysql/
│       └── dameng/
├── scripts/
│   ├── install_milvus.sh        # Milvus安装脚本
│   └── build-dataagent.sh       #构建脚本
├── VERSION.txt                   #版本信息
└── INSTALL.txt                   # 安装说明
```

## 部署管理命令

```bash
# 查看服务状态
sudo systemctl status dataagent

# 查看实时日志
sudo journalctl -u dataagent -f

# 重启服务
sudo systemctl restart dataagent

#停服务
sudo systemctl stop dataagent
```

## Windows 开发环境配置

### 使用 Git Bash

```bash
# 设置编码
export MAVEN_OPTS="-Dfile.encoding=UTF-8"

#编译后端
cd data-agent-management
./mvnw.cmd package -DskipTests

#编译前端
cd ../data-agent-frontend
yarn install
yarn build
```

### 使用 PowerShell

```powershell
#编译后端
Set-Location data-agent-management
.\mvnw.cmd package -DskipTests

#编译前端
Set-Location ..\data-agent-frontend
yarn install
yarn build
```

##常问题解决

### 1. 编译错误处理

```bash
#清理编译缓存
./mvnw clean

# 重新编译
./mvnw compile

#跳测试打包
./mvnw package -DskipTests
```

### 2. 依赖问题

```bash
#前端依赖重新安装
cd data-agent-frontend
rm -rf node_modules
yarn install
```

### 3.权限问题

```bash
# 设置脚本执行权限
chmod +x engineering/build-dataagent.sh
chmod +x engineering/install_milvus.sh
```

## 验证编译结果

```bash
#检查后端 JAR
ls -la data-agent-management/target/*.jar

#检查前端构建
ls -la data-agent-frontend/dist/

#验证文件大小
du -h data-agent-management/target/*.jar
du -sh data-agent-frontend/dist/
```