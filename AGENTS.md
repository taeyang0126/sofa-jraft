# AGENTS.md - SOFAJRaft 编码规范

本文档为 SOFAJRaft 代码库中的代理开发提供必要的构建、测试和编码风格指南。

## 最高优先级
- 在回复时请叫我【lei】
- 使用中文回复

## 构建和测试命令

### 构建
```bash
mvn clean install          # 完整构建所有模块
mvn compile               # 仅编译
mvn java-formatter:format # 格式化代码（编译时自动运行）
mvn license:format        # 应用许可证头
```

### 测试
```bash
mvn test                                        # 运行所有测试
mvn test -Dtest=ClassName                       # 运行特定测试类
mvn test -Dtest=ClassName#methodName            # 运行特定测试方法
mvn jacoco:report -Djacoco.skip=false          # 生成覆盖率报告
```

**环境要求**: JDK 8+, Maven 3.2.5+

**测试框架**: JUnit 4.13.1, 配合 PowerMock 1.6.0 和 Mockito 1.9.5

## 项目结构

多模块 Maven 项目：
- `jraft-core`: RAFT 共识算法核心实现
- `jraft-test`: 测试工具和通用测试基础设施
- `jraft-example`: 示例应用程序和使用模式
- `jraft-rheakv`: 基于 RAFT 构建的分布式 KV 存储 (RheaKV)
- `jraft-extension`: 额外实现（RPC、日志存储变体）

## 代码风格指南

### 格式化
- **缩进**: 4 个空格（不使用制表符）
- **行长度**: 120 个字符
- **配置文件**: "Alipay Convention"（Eclipse 格式化器位于 `tools/codestyle/formatter.xml`）
- **大括号**: 行尾样式（左大括号在同一行）
- **空行**: 方法前 1 个空行，第一个类主体声明前无空行

### 导入顺序
标准 Java 导入顺序（组之间无空行）：
1. `java.*` 包
2. 第三方库（`org.*`, `com.*` 外部）
3. 内部 `com.alipay.sofa.jraft.*` 包

静态导入应谨慎使用，并放置在常规导入之后。

### 命名约定
- **类/接口**: PascalCase（首字母大写的驼峰命名，例如 `NodeImpl`, `RaftException`, `LogStorage`）
- **方法**: camelCase（首字母小写的驼峰命名，例如 `apply`, `shutdown`, `getConfiguration`）
- **变量**: camelCase（例如 `currentLeader`, `nextIndex`, `logEntry`）
- **常量**: UPPER_CASE 下划线分隔（例如 `MAX_BATCH_SIZE`, `DEFAULT_TIMEOUT`）
- **私有字段**: 构造函数中使用 camelCase 并加上 `this.` 前缀（例如 `this.config = config`）

### 错误处理
- **异常**: 主要使用非受检异常（继承自 `RuntimeException` 或 `Throwable`）
- **状态对象**: RAFT 相关错误使用 `com.alipay.sofa.jraft.Status` 对象
- **日志**: 重新抛出前必须记录错误
- **模式**:
  ```java
  try {
      // 外部调用
  } catch (Throwable t) {
      LOG.error("Operation failed for reason: {}", t.getMessage(), t);
      throw new IllegalStateException("Operation failed", t);
  }
  ```

### 空值处理
- 对外部输入和关键路径使用显式空值检查
- 构造函数/必填方法参数使用 `Objects.requireNonNull()`
- 最小化 `Optional` 使用 - 优先使用空值检查
- 模式:
  ```java
  if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
  }
  Objects.requireNonNull(param, "param cannot be null");
  ```

### 日志
- **框架**: SLF4J 配合 LoggerFactory
- **声明**: `private static final Logger LOG = LoggerFactory.getLogger(ClassName.class);`
- **级别**:
  - `LOG.info`: 正常操作消息
  - `LOG.warn`: 可恢复的问题或弃用警告
  - `LOG.error`: 失败和异常
- **参数化日志**: 始终使用参数化格式（而非字符串拼接）
  ```java
  LOG.info("Node {} joined cluster with {}", nodeId, configuration);
  LOG.error("Failed to apply log entry: {}", entryId, exception);
  ```

### 注释和 Javadoc
- **许可证头**: `src/main/java` 和 `src/test/java` 中的所有 `.java` 文件必须包含 Apache 2.0 许可证头（参见 `tools/codestyle/HEADER`）
- **类 Javadoc**: 包含作者标签和简要描述
- **方法 Javadoc**: 标准格式，包含 `@param` 和 `@return` 标签
- **行内代码**: 使用 `{@code}` 引用行内代码
- **注意**: 最小化行内注释 - 代码应自解释

### 类型安全
- **禁止类型抑制**: 未经合理说明不得使用 `@SuppressWarnings("unchecked")`
- **泛型**: 尽可能使用显式泛型类型
- **类型转换**: 最小化转换，优先使用类型安全模式

## 文件头

所有 Java 源文件必须以 Apache 2.0 许可证头开头。license-maven-plugin 将在 `generate-sources` 阶段自动格式化文件头。

## 代码质量

- 未配置静态分析工具（checkstyle, spotbugs, PMD）
- 代码质量通过审查和格式化维护
- 不确定时遵循类似文件中的现有模式

## 常见模式

### 线程安全
- 对高争用数据结构使用 `ReadWriteLock`
- 对简单计数器/标志位使用 `Atomic*` 类
- 在 Javadoc 中记录线程安全保证

### 资源管理
- 对 Closeable 对象使用 try-with-resources
- 为自定义资源类实现 `Closeable`
- 确保在关闭方法中正确清理资源

### 配置
- 使用 `*Options` 类进行配置（例如 `NodeOptions`, `RaftOptions`）
- 在选项构建器中提供合理的默认值
- 在构造函数/工厂方法中验证配置
