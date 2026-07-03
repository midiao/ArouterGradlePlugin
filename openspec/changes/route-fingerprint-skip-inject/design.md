## Context

Phase 2 的默认实现仍然依赖 `ScopedArtifact.CLASSES + Scope.ALL` 上的 `TransformAllClassesTask`：它已经具备 route metadata、route scan state、route fingerprint 与缓存注入结果，但 task 本身仍然要参与整包 classes 聚合输出。这样一来，普通业务 class 变化时虽然不一定重新 ASM 注入，却仍然要继续经过一条较重的 transform 链路。

在 Phase 3 里，最初尝试过将 `CollectRouteMetadataTask` 挂到 `variant.artifacts.forScope(ScopedArtifacts.Scope.ALL).toGet(ScopedArtifact.CLASSES, ...)`，再用 `variant.instrumentation.transformClassesWith(...)` 定向修改 `LogisticsCenter`。真实构建验证表明，这条方案仍会形成 cycle：

- `debugCollectRouteMetadataTask`
- `transformDebugClassesWithAsm`

原因是 `toGet(ScopedArtifact.CLASSES)` 读取到的仍是当前 variant 的 CLASSES provider，而默认 AGP ASM task `transformDebugClassesWithAsm` 同样会回写这条 provider 链。因此，Phase 3 的关键不是“换成 Instrumentation API 就结束”，而是**必须同时把 route 收集的输入从 `ScopedArtifact.CLASSES` 上拆下来**。

## Goals / Non-Goals

**Goals:**
- 默认配置下不再通过自定义聚合 transform 处理全部 classes。
- 为每个 variant 独立生成稳定的 route metadata / scan state / fingerprint 产物。
- 在普通业务 class 变化时，route 收集任务增量更新并保持 metadata 稳定。
- 通过 AGP Instrumentation API 仅修改 `com.alibaba.android.arouter.core.LogisticsCenter`。
- 继续保留旧配置的兼容路径，避免破坏 `onlyInjectWhenRouteChanged = false` 的历史语义。

**Non-Goals:**
- 不修改 ARouter 的注册点和 `loadRouterMap()` 语义。
- 不在本次改造里实现跨模块/跨插件的统一 route metadata 服务。
- 不尝试消除所有与编译任务路径命名相关的 AGP/Kotlin 插件差异。

## Decisions

- 新增 `CollectRouteMetadataTask`，不再通过 `toGet(ScopedArtifact.CLASSES)` 读取输入，而是直接使用：
  - `build/tmp/kotlin-classes/<variant>`
  - `build/intermediates/javac/<variant>/classes`
  - `variant.compileClasspath`
- `CollectRouteMetadataTask` 使用 `InputChanges` 增量更新 `route-scan-state.txt`，并持续输出：
  - `route-metadata.txt`
  - `route-index.json`
  - `route-fingerprint.txt`
  - `route-scan-state.txt`
- 默认配置 `onlyInjectWhenRouteChanged = true` 时，插件注册 `LogisticsCenterTransformFactory`：
  - `variant.instrumentation.transformClassesWith(LogisticsCenterTransformFactory::class.java, InstrumentationScope.ALL)`
  - `variant.instrumentation.setAsmFramesComputationMode(COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)`
- `LogisticsCenterTransformFactory.isInstrumentable(...)` 只返回 `com.alibaba.android.arouter.core.LogisticsCenter`，确保 ASM visitor 不会作用于其他 class。
- Phase 2 的 `TransformAllClassesTask` 被保留为兜底路径，仅在 `onlyInjectWhenRouteChanged = false` 时注册，用于兼容“始终重新生成注入结果”的旧行为。
- `route-fingerprint.txt` 在默认 Phase 3 路径下只用于表达 route metadata 的稳定哈希，不再承担缓存 `LogisticsCenter.injected.class` 的职责。

## Risks / Trade-offs

- `CollectRouteMetadataTask` 读取项目编译输出目录时依赖 AGP/Kotlin 当前的输出路径约定；未来 AGP 升级若调整路径，插件需要跟进。
- 默认路径虽然已经摆脱了 `Scope.ALL` 的自定义聚合 transform，但 route 收集任务仍然需要感知项目编译输出和 classpath 变化，因此并不是“零成本”。
- `variant.compileClasspath` 的外部依赖输入粒度主要仍然是 jar 级别；依赖 jar 变化时仍会整包重扫该 jar 内的 route classes。
- 保留旧 transform 兜底路径意味着仓库内会并存两套执行模型，但这样可以最大限度降低兼容性风险。

## Validation Notes

Phase 3 已通过以下真实构建验证：
- `:app:assembleDebug` 成功，`debugCollectRouteMetadataTask` 与 `transformDebugClassesWithAsm` 可同时工作且不再形成 cycle。
- 对非路由业务类做一次临时改动后再次构建，`debugCollectRouteMetadataTask` 日志命中 `incremental=true`。
- 同一次验证中，`route-metadata.txt` 与 `route-fingerprint.txt` 哈希保持不变，说明 route 收集已从普通业务类变化中收缩出来。
