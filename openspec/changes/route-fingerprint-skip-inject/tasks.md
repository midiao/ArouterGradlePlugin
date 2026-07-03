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

## 3. Documentation and verification

- [x] 3.1 更新 README 说明 Phase2 的产物、能力边界与收益点
- [x] 3.2 更新 OpenSpec proposal / design / spec 与真实落地实现保持一致
- [x] 3.3 构建验证最终代码状态可成功执行 `:app:assembleDebug`
