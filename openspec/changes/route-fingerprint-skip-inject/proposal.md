## Why

当前插件在任意 classes 输入发生变化时都会重新执行聚合 transform。Phase 1 已经解决了“重复 ASM 注入”问题，但路由扫描本身仍然是全量的；在日常 debug 修改里，很多变更只是普通业务 class 改动，不应该重新扫描全部 ARouter 路由输入。

## What Changes

- 为 `TransformAllClassesTask` 增加稳定的 `route-metadata.txt`、`route-index.json`、`route-scan-state.txt` 输出。
- 依据路由索引、原始 `LogisticsCenter.class` 和注入器版本生成 `route-fingerprint.txt`。
- 缓存上次生成的 `LogisticsCenter.injected.class`，在路由未变化时复用该结果并跳过再次 ASM 注入。
- 在 transform 任务内部引入基于 `InputChanges` 的增量 route scan cache，仅重扫变更过的 route class / jar。
- 在 demo 和 README 中展示新行为、产物位置与当前能力边界。

## Capabilities

- **New Capabilities**
  - `route-fingerprint-injection`
- **Modified Capabilities**
  - 无

## Impact

- 影响 `arouter-gradle-plugin` 中的 transform 任务、插件配置与辅助工具类。
- 新增 `app/build/intermediates/arouter/<variant>/` 产物用于调试、缓存和增量 route scan。
- 不改变现有插件 id、基础接入方式和 ARouter 注入行为。
