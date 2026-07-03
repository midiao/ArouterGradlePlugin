## Context

当前实现使用 `ScopedArtifact.CLASSES + Scope.ALL` 将所有 classes 聚合到 `TransformAllClassesTask` 中，再在任务末尾统一修改 `LogisticsCenter.class`。Phase 1 已经加入了 route fingerprint 与注入结果复用，但路由扫描仍然是全量的。

最初设想的 Phase 2 是把“路由收集”拆成独立 task，再让 transform task 消费产物；但在 AGP 当前的 artifact 接线模型下，对同一 variant 的 `ScopedArtifact.CLASSES` 同时执行 `toGet(...)` 和 `toTransform(...)` 会形成 task cycle。因此，本次选择一个可运行且可验证收益的折中方案：**保留单个 transform task，但在其内部把 route scan 本身做成增量缓存。**

## Goals / Non-Goals

**Goals:**
- 为每个 variant 输出稳定的路由索引、扫描状态与指纹。
- 在普通业务 class 变化时，只增量更新受影响的 route entries。
- 在路由集合和原始 `LogisticsCenter.class` 未变化时复用上次注入结果。
- 保持现有 transform 链路、demo 和插件接入方式不变。

**Non-Goals:**
- 不在本次改造中切换到 Instrumentation API。
- 不在本次改造中消除 `Scope.ALL` 的聚合复制输出。
- 不改变 ARouter 注册点和现有 `loadRouterMap()` 注入语义。

## Decisions

- 在 `TransformAllClassesTask` 内新增 `routeMetadataOutput`、`routeIndexOutput`、`routeScanStateOutput`、`routeFingerprintOutput`、`lastAppliedFingerprintOutput`、`cachedInjectedClassOutput` 六类文件输出。
- 使用 `route-scan-state.txt` 持久化“输入文件 -> 该输入贡献的 route entries”映射；目录输入以 class 文件绝对路径为 key，jar 输入以 jar 绝对路径为 key。
- 使用桥接后的 `ConfigurableFileCollection` 作为 Gradle 的增量输入，让 `InputChanges` 可以只返回本轮发生变化的 class / jar。
- 目录输入只在路径命中 `com/alibaba/android/arouter/routes/` 时才读取并扫描；jar 输入在文件变化时整包重扫。
- 使用稳定排序后的路由类集合生成 `route-index.json`，再结合原始 `LogisticsCenter.class` hash 和注入器版本生成 `route-fingerprint.txt`。
- 当 `onlyInjectWhenRouteChanged=true` 且缓存命中时，直接复用缓存的 `LogisticsCenter.injected.class`。

## Risks / Trade-offs

- 当前 transform 仍会执行全量复制与聚合输出，因此性能收益主要来自“路由扫描增量化 + ASM 注入复用”，而不是完整的增量 transform。
- `route-scan-state.txt` 依赖输入路径稳定性；clean 构建或 task 实现变化后会自动退回一次全量扫描，这是可接受的。
- jar 级别的增量粒度仍然是“整个 jar 重新扫描”，但相较于每次重扫所有 jar 已经更可控。
- 这是向后续“Instrumentation API + 更细粒度输入收缩”迁移的中间态，不是最终架构。
