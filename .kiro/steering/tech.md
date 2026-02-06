# 技术栈

## 构建系统

- **构建工具**：Maven 3.2.5+
- **Java 版本**：JDK 8+（源码和目标均为 1.8）
- **编码**：UTF-8

## 核心依赖

### 通信和序列化
- **SOFABolt** (1.6.7)：高性能 RPC 通信框架
- **Protobuf** (3.25.5)：消息序列化
- **Protostuff** (1.6.0)：高性能序列化
- **Hessian** (3.3.6)：序列化协议

### 存储
- **RocksDB** (8.8.1)：嵌入式 KV 存储引擎

### 并发和性能
- **Disruptor** (3.3.7)：高性能并发框架
- **JCTools** (2.1.1)：并发工具
- **Affinity** (3.1.7)：线程亲和性
- **Metrics** (4.0.2)：性能指标统计

### 日志
- **SLF4J** (1.7.21)：日志门面
- **Log4j2** (2.17.1)：日志实现

### 测试
- **JUnit** (4.13.1)：单元测试框架
- **Mockito** (1.9.5)：Mock 框架
- **PowerMock** (1.6.0)：增强 Mock 能力
- **JMH** (1.20)：性能基准测试

## 常用命令

### 构建
```bash
# 完整构建所有模块
mvn clean install

# 仅编译
mvn compile

# 格式化代码（编译时自动运行）
mvn java-formatter:format

# 应用许可证头
mvn license:format
```

### 测试
```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=ClassName

# 运行特定测试方法
mvn test -Dtest=ClassName#methodName

# 生成覆盖率报告
mvn jacoco:report -Djacoco.skip=false
```

### 打包
```bash
# 生成发布包
mvn assembly:assembly
```

## Maven 插件

- **maven-compiler-plugin**：Java 编译
- **maven-java-formatter-plugin**：代码格式化（使用 `tools/codestyle/formatter.xml`）
- **license-maven-plugin**：许可证头管理
- **sortpom-maven-plugin**：POM 文件排序
- **jacoco-maven-plugin**：代码覆盖率
- **maven-surefire-plugin**：测试执行

## Java 9+ 兼容性

项目支持 Java 9+，使用特殊的 profile 配置反射访问权限：
```bash
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/java.util.concurrent=ALL-UNNAMED
```
