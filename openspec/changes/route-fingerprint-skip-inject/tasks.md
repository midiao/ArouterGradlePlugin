## 1. Phase 1：指纹与注入复用

- [x] 1.1 创建路由索引与指纹工具类
- [x] 1.2 为 transform 任务增加 `route-index`、`route-fingerprint` 与缓存输出
- [x] 1.3 在路由未变化时复用缓存的 `LogisticsCenter` 注入结果

## 2. Phase 2：增量 route scan cache

- [x] 2.1 在 `TransformAllClassesTask` 内加入 `route-metadata` 与 `route-scan-state` 产物
- [x] 2.2 将 route scan 改为基于 `InputChanges` 的增量更新
- [x] 2.3 仅在命中 route 包路径或变更 jar 时重扫对应输入
- [x] 2.4 验证非路由类变更时日志命中 `incremental=true`
- [x] 2.5 验证非路由类变更时继续命中 `skip inject and reuse cached LogisticsCenter.class`

## 3. Phase 3：独立 route 收集 + Instrumentation API

- [x] 3.1 新增 `CollectRouteMetadataTask`，独立输出 `route-metadata` / `route-index` / `route-fingerprint` / `route-scan-state`
- [x] 3.2 验证 `toGet(ScopedArtifact.CLASSES)` 与 `transformDebugClassesWithAsm` 会形成 cycle，并调整实现方案
- [x] 3.3 将 route 收集输入改为项目编译输出目录和 `variant.compileClasspath`
- [x] 3.4 接入 `variant.instrumentation.transformClassesWith(...)`，仅对 `LogisticsCenter` 创建 ASM visitor
- [x] 3.5 保留 `onlyInjectWhenRouteChanged = false` 的旧 transform 兜底路径
- [x] 3.6 验证 `:app:assembleDebug` 成功且默认路径不再形成 cycle
- [x] 3.7 验证非路由业务类变更时 `debugCollectRouteMetadataTask` 命中 `incremental=true` 且 route metadata 哈希保持不变

## 4. Documentation and verification

- [x] 4.1 更新 README 说明 Phase3 的默认路径、产物与兼容回退行为
- [x] 4.2 更新 OpenSpec proposal / design / spec 与真实落地实现保持一致
- [ ] 4.3 在最终代码状态下再次执行 `:app:assembleDebug`
- [ ] 4.4 执行 `openspec validate route-fingerprint-skip-inject --json`
