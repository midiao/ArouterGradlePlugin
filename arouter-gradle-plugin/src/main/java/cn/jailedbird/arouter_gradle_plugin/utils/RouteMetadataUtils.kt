@file:Suppress("SpellCheckingInspection")

package cn.jailedbird.arouter_gradle_plugin.utils

import java.io.File
import java.security.MessageDigest

object RouteMetadataUtils {
    const val INJECTOR_VERSION = "route-fingerprint-v2"

    private val routeInterfaceSimpleNames = listOf(
        "IRouteRoot",
        "IInterceptorGroup",
        "IProviderGroup",
    )

    fun createTargetList(): List<ScanSetting> {
        return routeInterfaceSimpleNames.map(::ScanSetting)
    }

    fun sortRouteEntries(targetList: List<ScanSetting>) {
        targetList.forEach { setting ->
            val normalized = setting.classList.distinct().sorted()
            setting.classList.clear()
            setting.classList.addAll(normalized)
        }
    }

    fun buildRouteIndexJson(variantName: String, targetList: List<ScanSetting>): String {
        val entries = targetList.associateBy(::interfaceSimpleName)
        val routeRoot = entries["IRouteRoot"]?.classList.orEmpty()
        val interceptorGroup = entries["IInterceptorGroup"]?.classList.orEmpty()
        val providerGroup = entries["IProviderGroup"]?.classList.orEmpty()
        return buildString {
            appendLine("{")
            appendLine("  \"variant\": \"${escape(variantName)}\",")
            appendLine("  \"injectorVersion\": \"${escape(INJECTOR_VERSION)}\",")
            appendLine("  \"entries\": {")
            appendLine("    \"IRouteRoot\": ${toJsonArray(routeRoot)},")
            appendLine("    \"IInterceptorGroup\": ${toJsonArray(interceptorGroup)},")
            appendLine("    \"IProviderGroup\": ${toJsonArray(providerGroup)}")
            appendLine("  }")
            append('}')
        }
    }

    fun buildRouteMetadataText(targetList: List<ScanSetting>): String {
        val lines = mutableListOf<String>()
        targetList.sortedBy(::interfaceSimpleName).forEach { setting ->
            val interfaceName = interfaceSimpleName(setting)
            setting.classList.sorted().forEach { className ->
                lines += "$interfaceName\t$className"
            }
        }
        return if (lines.isEmpty()) "" else lines.joinToString(separator = "\n", postfix = "\n")
    }

    fun parseRouteMetadataText(content: String): List<ScanSetting> {
        val targetList = createTargetList()
        val targetMap = targetList.associateBy(::interfaceSimpleName)
        content.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2) {
                    return@forEach
                }
                targetMap[parts[0]]?.classList?.add(parts[1])
            }
        sortRouteEntries(targetList)
        return targetList
    }

    fun createSourceRouteState(targetList: List<ScanSetting>): LinkedHashMap<String, MutableSet<String>> {
        val state = linkedMapOf<String, MutableSet<String>>()
        targetList.forEach { setting ->
            val classes = setting.classList.distinct().sorted()
            if (classes.isNotEmpty()) {
                state[interfaceSimpleName(setting)] = classes.toMutableSet()
            }
        }
        return state
    }

    fun targetListFromScanState(
        scanState: Map<String, Map<String, Set<String>>>
    ): List<ScanSetting> {
        val targetList = createTargetList()
        val targetMap = targetList.associateBy(::interfaceSimpleName)
        scanState.values.forEach { sourceState ->
            sourceState.forEach { (interfaceName, classes) ->
                targetMap[interfaceName]?.classList?.addAll(classes)
            }
        }
        sortRouteEntries(targetList)
        return targetList
    }

    fun readScanState(file: File): LinkedHashMap<String, LinkedHashMap<String, MutableSet<String>>> {
        val state = linkedMapOf<String, LinkedHashMap<String, MutableSet<String>>>()
        if (!file.exists()) {
            return state
        }
        file.forEachLine { line ->
            if (line.isBlank()) {
                return@forEachLine
            }
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) {
                return@forEachLine
            }
            val sourceKey = parts[0]
            val interfaceName = parts[1]
            val className = parts[2]
            val sourceState = state.getOrPut(sourceKey) { linkedMapOf() }
            val classes = sourceState.getOrPut(interfaceName) { linkedSetOf() }
            classes += className
        }
        return state
    }

    fun writeScanState(
        file: File,
        scanState: Map<String, Map<String, Set<String>>>
    ) {
        val lines = mutableListOf<String>()
        scanState.toSortedMap().forEach { (sourceKey, sourceState) ->
            routeInterfaceSimpleNames.forEach { interfaceName ->
                sourceState[interfaceName].orEmpty().sorted().forEach { className ->
                    lines += listOf(sourceKey, interfaceName, className).joinToString(separator = "\t")
                }
            }
        }
        writeTextIfChanged(
            file,
            if (lines.isEmpty()) "" else lines.joinToString(separator = "\n", postfix = "\n")
        )
    }

    fun replaceSourceScanState(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        sourceKey: String,
        sourceState: LinkedHashMap<String, MutableSet<String>>
    ) {
        if (sourceState.values.all { it.isEmpty() }) {
            scanState.remove(sourceKey)
            return
        }
        scanState[sourceKey] = linkedMapOf<String, MutableSet<String>>().apply {
            routeInterfaceSimpleNames.forEach { interfaceName ->
                val classes = sourceState[interfaceName].orEmpty().toSortedSet()
                if (classes.isNotEmpty()) {
                    put(interfaceName, classes.toMutableSet())
                }
            }
        }
    }

    fun computeFingerprint(routeIndexJson: String, originInjectBytes: ByteArray): String {
        val source = buildString {
            appendLine(INJECTOR_VERSION)
            appendLine(sha256(originInjectBytes))
            append(routeIndexJson)
        }
        return sha256(source.toByteArray())
    }

    fun computeRouteFingerprint(routeIndexJson: String): String {
        val source = buildString {
            appendLine(INJECTOR_VERSION)
            append(routeIndexJson)
        }
        return sha256(source.toByteArray())
    }

    fun totalRouteCount(targetList: List<ScanSetting>): Int {
        return targetList.sumOf { it.classList.size }
    }

    fun writeTextIfChanged(file: File, content: String) {
        ensureParent(file)
        if (file.exists() && file.readText() == content) {
            return
        }
        file.writeText(content)
    }

    fun writeBytesIfChanged(file: File, content: ByteArray) {
        ensureParent(file)
        if (file.exists() && file.readBytes().contentEquals(content)) {
            return
        }
        file.writeBytes(content)
    }

    fun readTextIfExists(file: File): String? {
        return if (file.exists()) file.readText() else null
    }

    private fun ensureParent(file: File) {
        file.parentFile?.mkdirs()
    }

    private fun interfaceSimpleName(scanSetting: ScanSetting): String {
        return scanSetting.interfaceName.substringAfterLast('/')
    }

    private fun toJsonArray(items: List<String>): String {
        return items.joinToString(prefix = "[", postfix = "]") { "\"${escape(it.replace('/', '.'))}\"" }
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun sha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        return digest.joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
    }
}
