# SOFAJRaft 产品概述

SOFAJRaft 是一个基于 RAFT 一致性算法的生产级高性能 Java 实现，专为高负载、低延迟场景设计。

## 核心特性

- **RAFT 共识算法**：完整实现 Leader 选举、日志复制、快照和日志压缩
- **MULTI-RAFT-GROUP**：支持多 Raft 组，适用于分布式系统
- **高性能**：流水线复制、线性一致读（ReadIndex/LeaseRead）
- **容错性**：支持对称/非对称网络分区，少数派故障不影响系统可用性
- **集群管理**：动态增删节点、Leader 转移、优先级选举
- **嵌入式 KV 存储**：内置分布式 KV 存储实现（RheaKV）
- **生产验证**：通过 Jepsen 一致性验证测试

## 项目来源

移植自百度的 braft（C++ 实现），并进行了优化和改进。

## 许可证

Apache License 2.0
