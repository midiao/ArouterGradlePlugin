@file:Suppress("SpellCheckingInspection")

package cn.jailedbird.arouter_gradle_plugin

import cn.jailedbird.arouter_gradle_plugin.utils.RouteMetadataUtils
import cn.jailedbird.arouter_gradle_plugin.utils.ScanUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

abstract class CollectRouteMetadataTask : DefaultTask() {

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val projectClassInputs: ConfigurableFileCollection

    @get:Incremental
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputFiles
    abstract val classpathInputs: ConfigurableFileCollection

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val logRouteFingerprint: Property<Boolean>

    @get:OutputFile
    abstract val routeMetadataOutput: RegularFileProperty

    @get:OutputFile
    abstract val routeIndexOutput: RegularFileProperty

    @get:OutputFile
    abstract val routeFingerprintOutput: RegularFileProperty

    @get:OutputFile
    abstract val routeScanStateOutput: RegularFileProperty

    @TaskAction
    fun taskAction(inputChanges: InputChanges) {
        println("Arouter route metadata collection task start: variant=${variantName.get()}")
        val scanStateFile = routeScanStateOutput.asFile.get()
        val incrementalScan = inputChanges.isIncremental && scanStateFile.exists()
        val scanState = if (incrementalScan) {
            RouteMetadataUtils.readScanState(scanStateFile)
        } else {
            linkedMapOf()
        }

        if (incrementalScan) {
            applyProjectClassChanges(scanState, inputChanges)
            applyClasspathChanges(scanState, inputChanges)
        } else {
            fullScan(scanState)
        }

        val targetList = RouteMetadataUtils.targetListFromScanState(scanState)
        val routeMetadataText = RouteMetadataUtils.buildRouteMetadataText(targetList)
        val routeIndexJson = RouteMetadataUtils.buildRouteIndexJson(variantName.get(), targetList)
        val routeFingerprint = RouteMetadataUtils.computeRouteFingerprint(routeIndexJson)

        RouteMetadataUtils.writeTextIfChanged(routeMetadataOutput.asFile.get(), routeMetadataText)
        RouteMetadataUtils.writeTextIfChanged(routeIndexOutput.asFile.get(), routeIndexJson)
        RouteMetadataUtils.writeTextIfChanged(routeFingerprintOutput.asFile.get(), routeFingerprint)
        RouteMetadataUtils.writeScanState(scanStateFile, scanState)

        println(
            "ARouter route collection(${variantName.get()}) finished, routeCount=${RouteMetadataUtils.totalRouteCount(targetList)}, incremental=$incrementalScan"
        )
        if (logRouteFingerprint.getOrElse(false)) {
            println("ARouter route fingerprint(${variantName.get()})=$routeFingerprint")
            println("ARouter route index output=${routeIndexOutput.asFile.get().absolutePath}")
        }
    }

    private fun fullScan(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>
    ) {
        scanState.clear()
        scanStateFromRoots(projectClassInputs.files, scanState)
        scanStateFromRoots(classpathInputs.files, scanState)
    }

    private fun scanStateFromRoots(
        roots: Set<File>,
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>
    ) {
        roots.forEach { root ->
            if (!root.exists()) {
                return@forEach
            }
            if (root.isDirectory) {
                val directoryPath = if (root.absolutePath.endsWith(File.separatorChar)) {
                    root.absolutePath
                } else {
                    root.absolutePath + File.separatorChar
                }
                root.walkTopDown().forEach walkFiles@{ file ->
                    if (!file.isFile) {
                        return@walkFiles
                    }
                    val entryName = file.absolutePath.substringAfter(directoryPath).replace(File.separatorChar, '/')
                    if (!ScanUtils.shouldProcessClass(entryName)) {
                        return@walkFiles
                    }
                    RouteMetadataUtils.replaceSourceScanState(
                        scanState,
                        directorySourceKey(file),
                        scanClassFile(file)
                    )
                }
            } else if (root.isFile) {
                updateFileSourceState(scanState, root, root.name)
            }
        }
    }

    private fun applyProjectClassChanges(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        inputChanges: InputChanges
    ) {
        inputChanges.getFileChanges(projectClassInputs).forEach { change ->
            applyChangedFile(scanState, change.file, change.normalizedPath)
        }
    }

    private fun applyClasspathChanges(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        inputChanges: InputChanges
    ) {
        inputChanges.getFileChanges(classpathInputs).forEach { change ->
            if (change.changeType == ChangeType.REMOVED || !change.file.exists()) {
                removeFileSourceState(scanState, change.file)
                return@forEach
            }
            applyChangedFile(scanState, change.file, change.normalizedPath)
        }
    }

    private fun applyChangedFile(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        file: File,
        normalizedPath: String,
    ) {
        if (!file.exists()) {
            removeFileSourceState(scanState, file)
            return
        }
        if (!file.isFile) {
            return
        }
        updateFileSourceState(scanState, file, normalizedPath.replace(File.separatorChar, '/'))
    }

    private fun updateFileSourceState(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        file: File,
        normalizedPath: String,
    ) {
        when {
            file.extension.equals("jar", ignoreCase = true) -> {
                RouteMetadataUtils.replaceSourceScanState(scanState, jarSourceKey(file), scanJarFile(file))
            }

            ScanUtils.shouldProcessClass(normalizedPath) -> {
                RouteMetadataUtils.replaceSourceScanState(scanState, directorySourceKey(file), scanClassFile(file))
            }

            else -> {
                scanState.remove(directorySourceKey(file))
            }
        }
    }

    private fun removeFileSourceState(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        file: File,
    ) {
        scanState.remove(directorySourceKey(file))
        scanState.remove(jarSourceKey(file))
    }

    private fun scanClassFile(file: File): LinkedHashMap<String, MutableSet<String>> {
        val targetList = RouteMetadataUtils.createTargetList()
        file.inputStream().use { input ->
            ScanUtils.scanClass(input, targetList, false)
        }
        return RouteMetadataUtils.createSourceRouteState(targetList)
    }

    private fun scanJarFile(file: File): LinkedHashMap<String, MutableSet<String>> {
        val targetList = RouteMetadataUtils.createTargetList()
        ScanUtils.scanJar(file, targetList)
        return RouteMetadataUtils.createSourceRouteState(targetList)
    }

    private fun directorySourceKey(file: File): String {
        return "dir:${file.absolutePath}"
    }

    private fun jarSourceKey(file: File): String {
        return "jar:${file.absolutePath}"
    }
}
