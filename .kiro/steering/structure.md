# 项目结构

## 模块组织

SOFAJRaft 是一个多模块 Maven 项目，采用分层架构设计：

### 核心模块

#### jraft-core
RAFT 共识算法的核心实现，包含：

- **核心包** (`com.alipay.sofa.jraft.core`)
  - `NodeImpl`：Raft 节点实现
  - `FSMCallerImpl`：状态机调用器
  - `Replicator`：日志复制器
  - `BallotBox`：投票箱
  - `ReadOnlyServiceImpl`：只读服务

- **实体包** (`com.alipay.sofa.jraft.entity`)
  - `LogEntry`：日志条目
  - `LogId`：日志标识
  - `PeerId`：节点标识
  - `Task`：任务定义

- **存储包** (`com.alipay.sofa.jraft.storage`)
  - `LogStorage`：日志存储接口
  - `SnapshotStorage`：快照存储
  - `RaftMetaStorage`：元数据存储
  - `impl/`：RocksDB 等具体实现

- **RPC 包** (`com.alipay.sofa.jraft.rpc`)
  - `RaftClientService`：客户端服务
  - `RaftServerService`：服务端服务
  - `impl/`：基于 Bolt 的 RPC 实现

- **工具包** (`com.alipay.sofa.jraft.util`)
  - `ThreadPoolUtil`：线程池工具
  - `Recyclers`：对象回收
  - `SegmentList`：分段列表
  - `concurrent/`：并发工具

#### jraft-test
测试工具和通用测试基础设施：
- 测试辅助类
- Mock 工具
- 测试基类

#### jraft-example
示例应用程序和使用模式：
- **counter**：计数器示例（最常用的入门示例）
- **benchmark**：性能基准测试

#### jraft-rheakv
基于 RAFT 构建的分布式 KV 存储：
- **rheakv-core**：RheaKV 核心实现
- **rheakv-pd**：Placement Driver（调度器）

#### jraft-extension
扩展实现：
- **rpc-grpc-impl**：基于 gRPC 的 RPC 实现
- **bdb-log-storage-impl**：基于 BerkeleyDB 的日志存储

## 目录结构

```
sofa-jraft/
├── jraft-core/              # 核心模块
│   ├── src/main/java/       # 源代码
│   ├── src/main/resources/  # 资源文件（proto 定义等）
│   └── src/test/java/       # 测试代码
├── jraft-test/              # 测试工具
├── jraft-example/           # 示例代码
│   ├── src/main/java/       # 示例实现
│   └── config/              # 配置文件
├── jraft-rheakv/            # 分布式 KV 存储
│   ├── rheakv-core/         # KV 核心
│   └── rheakv-pd/           # 调度器
├── jraft-extension/         # 扩展模块
│   ├── rpc-grpc-impl/       # gRPC 实现
│   └── bdb-log-storage-impl/# BDB 存储
├── tools/                   # 工具脚本
│   └── codestyle/           # 代码风格配置
│       ├── formatter.xml    # Eclipse 格式化配置
│       └── HEADER           # 许可证头模板
├── rfcs/                    # RFC 设计文档
├── tmp/                     # 临时文件（运行时数据）
└── pom.xml                  # 根 POM 文件
```

## 包命名规范

所有包都在 `com.alipay.sofa.jraft` 命名空间下：

- `com.alipay.sofa.jraft.core.*` - 核心实现
- `com.alipay.sofa.jraft.entity.*` - 数据实体
- `com.alipay.sofa.jraft.storage.*` - 存储层
- `com.alipay.sofa.jraft.rpc.*` - RPC 通信
- `com.alipay.sofa.jraft.util.*` - 工具类
- `com.alipay.sofa.jraft.option.*` - 配置选项
- `com.alipay.sofa.jraft.error.*` - 异常定义
- `com.alipay.sofa.jraft.closure.*` - 回调闭包
- `com.alipay.sofa.jraft.conf.*` - 配置管理

## 资源文件

### Proto 定义
位于 `jraft-core/src/main/resources/`：
- `raft.proto` - RAFT 协议消息
- `rpc.proto` - RPC 消息
- `cli.proto` - CLI 命令消息
- `log.proto` - 日志消息
- `local_storage.proto` - 本地存储

### 配置文件
- `log4j2.xml` - 日志配置（各模块的 `src/main/resources/` 或 `src/test/resources/`）
- `*.yaml` - 示例配置（`jraft-example/config/`）

## 测试组织

测试代码镜像主代码结构：
- 单元测试：`src/test/java/com/alipay/sofa/jraft/`
- 测试资源：`src/test/resources/`
- 测试命名：`*Test.java` 或 `*TestSuite.java`
