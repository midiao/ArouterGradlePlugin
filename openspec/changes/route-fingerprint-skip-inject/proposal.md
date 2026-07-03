## Why

Phase 2 已经把“全量 route scan + 重复 ASM 注入”压缩成了“增量 route scan + 条件注入复用”，但默认路径仍然挂在 `ScopedArtifact.CLASSES + Scope.ALL` 的聚合 transform 上。结果是：普通业务 class 变化时，ARouter 任务虽然不一定重新插桩，却仍然会重新进入整包 classes 聚合输出链路。

Phase 3 的目标是继续把默认路径收缩成**真正面向目标类的增量构建**：独立生成 route metadata，再借助 AGP Instrumentation API 只修改 `com.alibaba.android.arouter.core.LogisticsCenter`，而不是继续维护一个会复制全部 classes 的自定义 transform task。

## What Changes

- 新增独立的 `CollectRouteMetadataTask`，直接消费项目编译输出目录和 `variant.compileClasspath`，增量维护 `route-metadata.txt`、`route-index.json`、`route-fingerprint.txt`、`route-scan-state.txt`。
- 默认配置 `onlyInjectWhenRouteChanged = true` 时，改用 `variant.instrumentation.transformClassesWith(...)` + `InstrumentationScope.ALL`，仅对 `LogisticsCenter` 创建 ASM visitor。
- 将原有注入逻辑抽到可复用的 visitor 工具中，让旧 transform 路径和新的 Instrumentation 路径共享字节码注入实现。
- 保留 `onlyInjectWhenRouteChanged = false` 的旧 `TransformAllClassesTask` 兜底路径，兼容“始终重新生成注入结果”的历史行为。
- 更新 README 和 OpenSpec，明确记录 `toGet(ScopedArtifact.CLASSES)` 与 `transformClassesWithAsm` 会形成 cycle，Phase 3 因此改用编译输出目录 + classpath 作为 route 收集输入。

## Capabilities

- **New Capabilities**
  - `route-fingerprint-injection`
- **Modified Capabilities**
  - 无

## Impact

- 影响 `arouter-gradle-plugin` 的插件注册逻辑、route 收集任务与 ASM 注入实现。
- 默认构建路径从自定义聚合 transform 切换到 AGP Instrumentation API，进一步减少非路由改动时的 ARouter 插件开销。
- 不改变插件 id、宿主接入方式和最终 `LogisticsCenter.loadRouterMap()` 的注册语义。
