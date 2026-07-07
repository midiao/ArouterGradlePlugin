package com.dahuatech.arouter_gradle_plugin

open class ARouterConfig {
    /** 完全禁用 Transform（所有构建类型都不执行） */
    var disableTransform: Boolean = false

    /** 仅当路由集合或 LogisticsCenter 变化时才重新生成注入结果 */
    var onlyInjectWhenRouteChanged: Boolean = true

    /** 输出 route-index 与 fingerprint 日志，方便调试构建命中情况 */
    var logRouteFingerprint: Boolean = false
}
