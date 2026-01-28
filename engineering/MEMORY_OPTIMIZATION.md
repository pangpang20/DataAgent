# Windows 编译内存优化指南

## 问题说明
在 Windows 系统上编译 DataAgent 项目时，可能会遇到内存不足的错误，例如：
- `There is insufficient memory for the Java Runtime Environment to continue`
- `Native memory allocation (malloc) failed to allocate X bytes`
- `页面文件太小，无法完成操作`

## 解决方案

### 方案1：增加系统虚拟内存（推荐）

1. 打开"系统属性" -> "高级" -> "性能" -> "设置"
2. 选择"高级"选项卡 -> "虚拟内存" -> "更改"
3. 取消勾选"自动管理所有驱动器的分页文件大小"
4. 选择系统驱动器(C:)，选择"自定义大小"
5. 设置初始大小：4096MB，最大大小：8192MB 或更大
6. 点击"设置" -> "确定"，重启计算机

### 方案2：使用内存优化的 Maven 配置

创建 `.mvn/maven.config` 文件，内容如下：
```
-Xmx2g -Xms512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC
```

### 方案3：分模块编译

如果内存仍然不足，可以分模块进行编译：

```cmd
# 仅编译后端
cd data-agent-management
..\mvnw clean compile -DskipTests

# 然后再打包
..\mvnw package -DskipTests
```

### 方案4：使用外部编译环境

考虑使用云服务器或更高配置的机器进行编译，然后将编译好的文件传输到目标环境。

## 编译脚本优化

为了应对内存限制，可以使用以下优化参数：

```cmd
set MAVEN_OPTS=-Xmx2g -Xms512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC
mvnw clean compile -DskipTests -T 1
```

- `-T 1` 表示单线程编译，减少内存峰值使用
- `-DskipTests` 跳过测试，减少编译时间与内存使用
- 使用 Serial GC 减少内存开销