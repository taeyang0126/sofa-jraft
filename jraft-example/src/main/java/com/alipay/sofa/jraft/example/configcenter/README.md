# 分布式配置中心示例

本目录包含基于 SOFAJRaft 实现的分布式配置中心示例程序，展示如何使用 Raft 协议构建强一致性的配置管理服务。

## 目录结构

```
configcenter/
├── ConfigServer.java              # 配置中心服务端
├── ConfigServerExample.java       # 服务端启动示例
├── AdminClient.java               # 管理客户端（用于配置的增删改）
├── AdminClientExample.java        # 管理客户端使用示例
├── AppClient.java                 # 应用客户端（用于读取配置）
├── AppClientExample.java          # 应用客户端使用示例
├── ConfigStateMachine.java        # Raft 状态机实现
├── HttpServer.java                # HTTP 服务器
├── MemoryConfigStorage.java       # 内存存储实现
└── ...                            # 其他支持类
```

## 快速开始

### 1. 启动配置中心服务端

#### 单节点模式（开发测试）

```bash
# 编译项目
mvn clean install -DskipTests

# 启动单节点服务器
java -cp jraft-example/target/jraft-example-1.4.0.jar \
  com.alipay.sofa.jraft.example.configcenter.ConfigServerExample single
```

服务器将在 `http://127.0.0.1:8080` 上启动。

#### 集群模式（生产环境）

在三个不同的终端中分别启动三个节点：

```bash
# 终端 1 - 启动节点 1
java -cp jraft-example/target/jraft-example-1.4.0.jar \
  com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 1

# 终端 2 - 启动节点 2
java -cp jraft-example/target/jraft-example-1.4.0.jar \
  com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 2

# 终端 3 - 启动节点 3
java -cp jraft-example/target/jraft-example-1.4.0.jar \
  com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 3
```

三个节点将分别在以下地址启动：
- 节点 1: `http://127.0.0.1:8080`
- 节点 2: `http://127.0.0.1:8180`
- 节点 3: `http://127.0.0.1:8280`

### 2. 使用管理客户端

管理客户端用于配置的创建、更新、删除和回滚操作。

```bash
# 运行管理客户端示例
java -cp jraft-example/target/jraft-example-1.4.0.jar \
  com.alipay.sofa.jraft.example.configcenter.AdminClientExample
```

示例包含：
- **示例 1**: 基本 CRUD 操作（创建、读取、更新、删除）
- **示例 2**: 版本管理和配置回滚
- **示例 3**: 异步操作
- **示例 4**: 交互式配置管理（需要取消注释）

### 3. 使用应用客户端

应用客户端用于读取配置和监听配置变更。

```bash
# 运行应用客户端示例
java -cp jraft-example/target/jraft-example-1.4.0.jar \
  com.alipay.sofa.jraft.example.configcenter.AppClientExample
```

示例包含：
- **示例 1**: 基本配置读取（使用 ReadIndex 保证一致性）
- **示例 2**: 版本历史查询
- **示例 3**: 配置变更监听
- **示例 4**: 多配置监听
- **示例 5**: 真实应用场景模拟

## 核心功能

### 1. 配置管理

- **创建/更新配置**: 支持多属性配置项
- **删除配置**: 软删除，保留历史版本
- **查询配置**: 一致性读取（ReadIndex）
- **列出配置**: 按命名空间列出所有配置

### 2. 版本管理

- **自动版本控制**: 每次更新自动增加版本号
- **历史查询**: 查看配置的所有历史版本
- **版本回滚**: 回滚到指定历史版本

### 3. 命名空间

- **隔离性**: 不同命名空间的配置相互隔离
- **多租户**: 支持多个应用或环境使用同一集群

### 4. 配置监听

- **轮询机制**: 定期检查配置变更
- **变更通知**: 配置变更时触发回调
- **多配置监听**: 同时监听多个配置项

### 5. 高可用性

- **Raft 共识**: 使用 Raft 协议保证数据一致性
- **自动故障转移**: Leader 故障时自动选举新 Leader
- **数据持久化**: 配置数据持久化到磁盘

## API 示例

### 管理客户端 API

```java
// 创建客户端
AdminClientOptions options = new AdminClientOptions();
options.setServerUrl("http://127.0.0.1:8080");
AdminClient client = new AdminClient(options);

// 创建配置
Map<String, String> properties = new HashMap<>();
properties.put("db.host", "localhost");
properties.put("db.port", "3306");
ConfigResponse response = client.putConfig("production", "database", properties);

// 删除配置
response = client.deleteConfig("production", "database");

// 列出配置
response = client.listConfigs("production");

// 回滚配置
response = client.rollbackConfig("production", "database", 1);

// 异步操作
client.putConfigAsync("production", "cache", properties, new ConfigCallback() {
    @Override
    public void onSuccess(ConfigResponse response) {
        System.out.println("Success!");
    }
    
    @Override
    public void onError(String errorMsg) {
        System.out.println("Error: " + errorMsg);
    }
});

// 关闭客户端
client.shutdown();
```

### 应用客户端 API

```java
// 创建客户端
AppClientOptions options = new AppClientOptions();
options.setServerUrl("http://127.0.0.1:8080");
options.setPollingIntervalMs(30000); // 30秒轮询间隔
AppClient client = new AppClient(options);

// 读取配置（一致性读）
ConfigResponse response = client.getConfig("production", "database");
if (response.isSuccess()) {
    ConfigEntry entry = response.getConfigEntry();
    String host = entry.getProperty("db.host");
    String port = entry.getProperty("db.port");
}

// 查询特定版本
response = client.getConfigByVersion("production", "database", 1);

// 查询历史版本
response = client.getConfigHistory("production", "database");

// 监听配置变更
client.startPolling("production", "database", new ConfigChangeListener() {
    @Override
    public void onConfigChanged(String namespace, String key, 
                               ConfigEntry oldEntry, ConfigEntry newEntry) {
        System.out.println("Configuration changed!");
        // 重新加载配置
    }
    
    @Override
    public void onError(String namespace, String key, String errorMsg) {
        System.out.println("Error: " + errorMsg);
    }
});

// 停止监听
client.stopPolling("production", "database");

// 关闭客户端
client.shutdown();
```

## HTTP API

配置中心提供 RESTful HTTP API：

### 创建/更新配置

```bash
curl -X PUT http://127.0.0.1:8080/config/production/database \
  -H "Content-Type: application/json" \
  -d '{"db.host":"localhost","db.port":"3306"}'
```

### 查询配置

```bash
curl http://127.0.0.1:8080/config/production/database?readIndex=true
```

### 删除配置

```bash
curl -X DELETE http://127.0.0.1:8080/config/production/database
```

### 列出配置

```bash
curl http://127.0.0.1:8080/config/production
```

### 查询历史版本

```bash
curl http://127.0.0.1:8080/config/production/database/history
```

### 查询特定版本

```bash
curl http://127.0.0.1:8080/config/production/database/version/1
```

### 回滚配置

```bash
curl -X POST http://127.0.0.1:8080/config/production/database/rollback \
  -H "Content-Type: application/json" \
  -d '{"version":1}'
```

## 配置选项

### 服务端选项 (ConfigServerOptions)

- `dataPath`: 数据存储路径（Raft 日志、快照等）
- `groupId`: Raft 组 ID
- `serverId`: 服务器地址（格式：host:port）
- `initialServerList`: 初始集群成员列表（逗号分隔）
- `port`: HTTP 服务器端口

### 管理客户端选项 (AdminClientOptions)

- `serverUrl`: 服务器 URL
- `timeoutMs`: 请求超时时间（毫秒）
- `maxRetries`: 最大重试次数

### 应用客户端选项 (AppClientOptions)

- `serverUrl`: 服务器 URL
- `timeoutMs`: 请求超时时间（毫秒）
- `pollingIntervalMs`: 轮询间隔（毫秒）

## 架构说明

### 核心组件

1. **ConfigServer**: 配置中心服务端，集成 Raft 节点和 HTTP 服务器
2. **ConfigStateMachine**: Raft 状态机，处理配置操作的日志应用
3. **HttpServer**: 基于 Vert.x 的 HTTP 服务器，处理客户端请求
4. **MemoryConfigStorage**: 内存存储层，管理配置数据
5. **AdminClient**: 管理客户端，用于配置的写操作
6. **AppClient**: 应用客户端，用于配置的读操作和监听

### 数据流

```
写操作流程:
AdminClient -> HTTP Server -> Raft Leader -> State Machine -> Storage
                                    |
                                    v
                              Raft Followers

读操作流程:
AppClient -> HTTP Server -> ReadIndex -> State Machine -> Storage
```

### 一致性保证

- **写操作**: 通过 Raft 协议保证强一致性，只有在多数派节点确认后才返回成功
- **读操作**: 使用 ReadIndex 机制保证线性一致性读取
- **快照**: 定期生成快照，减少日志大小，加快节点恢复

## 故障处理

### Leader 故障

当 Leader 节点故障时：
1. 集群自动检测 Leader 失联
2. 触发新的 Leader 选举
3. 新 Leader 当选后继续提供服务
4. 客户端自动重试请求到新 Leader

### 网络分区

当发生网络分区时：
1. 只有多数派分区可以继续处理写请求
2. 少数派分区拒绝写请求
3. 网络恢复后，少数派节点同步数据

### 数据恢复

节点重启后：
1. 从持久化的 Raft 日志恢复数据
2. 如果日志过多，从最新快照恢复
3. 追赶最新的日志条目
4. 恢复完成后重新加入集群

## 性能优化

1. **批量操作**: 使用异步 API 进行批量配置更新
2. **本地缓存**: 应用客户端可以缓存配置，减少网络请求
3. **轮询间隔**: 根据配置变更频率调整轮询间隔
4. **快照频率**: 根据写入量调整快照生成频率

## 注意事项

1. **端口冲突**: 确保配置的端口未被占用
2. **数据目录**: 确保数据目录有足够的磁盘空间
3. **集群规模**: 建议使用奇数个节点（3、5、7）
4. **网络延迟**: 集群节点间网络延迟应尽可能低
5. **时钟同步**: 确保集群节点时钟基本同步

## 故障排查

### 服务器无法启动

- 检查端口是否被占用
- 检查数据目录权限
- 检查 initialServerList 配置是否正确

### 客户端连接失败

- 检查服务器是否正常运行
- 检查网络连接
- 检查防火墙设置

### 配置更新失败

- 检查是否连接到 Leader 节点
- 检查集群是否有多数派节点在线
- 检查日志查看详细错误信息

## 扩展开发

### 自定义存储

实现 `ConfigStorage` 接口，替换默认的内存存储：

```java
public class MyConfigStorage implements ConfigStorage {
    // 实现接口方法
}
```

### 自定义序列化

修改 `ConfigEntry`、`ConfigRequest`、`ConfigResponse` 的序列化方式。

### 添加认证

在 `HttpServer` 中添加认证中间件。

### 监控指标

添加 Prometheus 或其他监控指标收集。

## 参考资料

- [SOFAJRaft 官方文档](https://www.sofastack.tech/projects/sofa-jraft/overview/)
- [Raft 论文](https://raft.github.io/raft.pdf)
- [Vert.x 文档](https://vertx.io/docs/)

## 许可证

Apache License 2.0
