@file:Suppress("SpellCheckingInspection")

package com.dahuatech.arouter_gradle_plugin

import com.dahuatech.arouter_gradle_plugin.utils.InjectUtils
import com.dahuatech.arouter_gradle_plugin.utils.RouteMetadataUtils
import com.dahuatech.arouter_gradle_plugin.utils.ScanSetting
import com.dahuatech.arouter_gradle_plugin.utils.ScanUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipException

abstract class TransformAllClassesTask : DefaultTask() {

    @get:Internal
    abstract val allDirectories: ListProperty<Directory>

    @get:Internal
    abstract val allJars: ListProperty<RegularFile>

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val incrementalDirectories: ConfigurableFileCollection

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val incrementalJars: ConfigurableFileCollection

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val onlyInjectWhenRouteChanged: Property<Boolean>

    @get:Input
    abstract val logRouteFingerprint: Property<Boolean>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val routeMetadataOutput: RegularFileProperty

    @get:OutputFile
    abstract val routeIndexOutput: RegularFileProperty

    @get:OutputFile
    abstract val routeFingerprintOutput: RegularFileProperty

    @get:OutputFile
    abstract val lastAppliedFingerprintOutput: RegularFileProperty

    @get:OutputFile
    abstract val cachedInjectedClassOutput: RegularFileProperty

    @get:OutputFile
    abstract val routeScanStateOutput: RegularFileProperty

    @TaskAction
    fun taskAction(inputChanges: InputChanges) {
        println("Welcome to use ArouterGradlePlugin for AGP8: https://github.com/JailedBird/ArouterGradlePlugin")
        println("ArouterGradlePlugin task start:")
        val leftSlash = File.separator == "/"
        val start = System.currentTimeMillis()
        val routeMetadata = collectRouteMetadata(inputChanges)
        debugCollection(routeMetadata.targetList)

        output.asFile.get().parentFile?.mkdirs()
        JarOutputStream(output.asFile.get().outputStream()).use { jarOutput ->
            allDirectories.get().forEach { directory ->
                val directoryPath =
                    if (directory.asFile.absolutePath.endsWith(File.separatorChar)) {
                        directory.asFile.absolutePath
                    } else {
                        directory.asFile.absolutePath + File.separatorChar
                    }
                directory.asFile.walk().forEach { file ->
                    if (file.isFile) {
                        val entryName = if (leftSlash) {
                            file.path.substringAfter(directoryPath)
                        } else {
                            file.path.substringAfter(directoryPath).replace(File.separatorChar, '/')
                        }
                        if (entryName.isNotEmpty()) {
                            file.inputStream().use { input ->
                                jarOutput.saveEntry(entryName, input)
                            }
                        }
                    }
                }
            }

            var originInject: ByteArray? = null

            val jars = allJars.get().map { it.asFile }
            for (sourceJar in jars) {
                val jar = JarFile(sourceJar)
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    try {
                        if (entry.isDirectory || entry.name.isEmpty()) {
                            continue
                        }
                        if (entry.name != ScanSetting.GENERATE_TO_CLASS_FILE_NAME) {
                            jar.getInputStream(entry).use { input ->
                                jarOutput.saveEntry(entry.name, input)
                            }
                        } else {
                            jar.getInputStream(entry).use { inputs ->
                                originInject = IOUtils.toByteArray(inputs)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is ZipException && e.message?.contains("META-INF/MANIFEST.MF") == true) {
                            // Skip META-INF/MANIFEST.MF
                        } else {
                            println("[Warning] Merge [jar:entry] ${jar.name}:${entry.name}, error is $e ")
                        }
                    }
                }
                jar.close()
            }

            val injectSource = originInject ?: error("Can not find ARouter inject point, Do you import ARouter?")
            val routeFingerprint = RouteMetadataUtils.computeFingerprint(routeMetadata.routeIndexJson, injectSource)
            RouteMetadataUtils.writeTextIfChanged(routeFingerprintOutput.asFile.get(), routeFingerprint)
            if (logRouteFingerprint.getOrElse(false)) {
                println("ARouter route fingerprint(${variantName.get()})=$routeFingerprint")
                println("ARouter route index output=${routeIndexOutput.asFile.get().absolutePath}")
            }
            val resultByteArray = buildInjectedBytecode(injectSource, routeMetadata.targetList, routeFingerprint)
            jarOutput.saveEntry(
                ScanSetting.GENERATE_TO_CLASS_FILE_NAME,
                ByteArrayInputStream(resultByteArray)
            )
        }
        println("ARouter plugin inject time spend ${System.currentTimeMillis() - start} ms")
    }

    private fun collectRouteMetadata(inputChanges: InputChanges): CollectedRouteMetadata {
        val scanStateFile = routeScanStateOutput.asFile.get()
        val incrementalScan = inputChanges.isIncremental && scanStateFile.exists()
        val scanState = if (incrementalScan) {
            RouteMetadataUtils.readScanState(scanStateFile)
        } else {
            linkedMapOf()
        }

        if (incrementalScan) {
            applyDirectoryChanges(scanState, inputChanges)
            applyJarChanges(scanState, inputChanges)
        } else {
            fullScan(scanState)
        }

        val targetList = RouteMetadataUtils.targetListFromScanState(scanState)
        val routeMetadataText = RouteMetadataUtils.buildRouteMetadataText(targetList)
        val routeIndexJson = RouteMetadataUtils.buildRouteIndexJson(variantName.get(), targetList)
        RouteMetadataUtils.writeTextIfChanged(routeMetadataOutput.asFile.get(), routeMetadataText)
        RouteMetadataUtils.writeTextIfChanged(routeIndexOutput.asFile.get(), routeIndexJson)
        RouteMetadataUtils.writeScanState(scanStateFile, scanState)
        println(
            "ARouter route collection(${variantName.get()}) finished, routeCount=${RouteMetadataUtils.totalRouteCount(targetList)}, incremental=$incrementalScan"
        )
        return CollectedRouteMetadata(targetList, routeIndexJson)
    }

    private fun fullScan(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>
    ) {
        scanState.clear()
        allDirectories.get().forEach { directory ->
            val root = directory.asFile
            if (!root.exists()) {
                return@forEach
            }
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
        }

        allJars.get().forEach { jar ->
            val jarFile = jar.asFile
            if (!jarFile.exists()) {
                return@forEach
            }
            RouteMetadataUtils.replaceSourceScanState(
                scanState,
                jarSourceKey(jarFile),
                scanJarFile(jarFile)
            )
        }
    }

    private fun applyDirectoryChanges(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        inputChanges: InputChanges
    ) {
        inputChanges.getFileChanges(incrementalDirectories).forEach { change ->
            val sourceKey = directorySourceKey(change.file)
            val normalizedPath = change.normalizedPath.replace(File.separatorChar, '/')
            if (change.changeType == ChangeType.REMOVED || !ScanUtils.shouldProcessClass(normalizedPath)) {
                scanState.remove(sourceKey)
                return@forEach
            }
            if (!change.file.isFile) {
                scanState.remove(sourceKey)
                return@forEach
            }
            RouteMetadataUtils.replaceSourceScanState(scanState, sourceKey, scanClassFile(change.file))
        }
    }

    private fun applyJarChanges(
        scanState: MutableMap<String, LinkedHashMap<String, MutableSet<String>>>,
        inputChanges: InputChanges
    ) {
        inputChanges.getFileChanges(incrementalJars).forEach { change ->
            val sourceKey = jarSourceKey(change.file)
            if (change.changeType == ChangeType.REMOVED || !change.file.exists()) {
                scanState.remove(sourceKey)
                return@forEach
            }
            RouteMetadataUtils.replaceSourceScanState(scanState, sourceKey, scanJarFile(change.file))
        }
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

    private fun buildInjectedBytecode(
        originInject: ByteArray,
        targetList: List<ScanSetting>,
        routeFingerprint: String
    ): ByteArray {
        val lastAppliedFile = lastAppliedFingerprintOutput.asFile.get()
        val cachedInjectedClassFile = cachedInjectedClassOutput.asFile.get()
        val canReusePreviousInjection =
            onlyInjectWhenRouteChanged.getOrElse(true) &&
                cachedInjectedClassFile.exists() &&
                RouteMetadataUtils.readTextIfExists(lastAppliedFile) == routeFingerprint

        if (canReusePreviousInjection) {
            println("ARouter route unchanged, skip inject and reuse cached LogisticsCenter.class")
            return cachedInjectedClassFile.readBytes()
        }

        println("Start inject byte code")
        val resultByteArray = InjectUtils.referHackWhenInit(
            ByteArrayInputStream(originInject), targetList
        )
        RouteMetadataUtils.writeBytesIfChanged(cachedInjectedClassFile, resultByteArray)
        RouteMetadataUtils.writeTextIfChanged(lastAppliedFile, routeFingerprint)
        println("Inject byte code successful")
        return resultByteArray
    }

    private fun JarOutputStream.saveEntry(entryName: String, inputStream: InputStream) {
        this.putNextEntry(JarEntry(entryName))
        IOUtils.copy(inputStream, this)
        this.closeEntry()
    }

    private fun debugCollection(list: List<ScanSetting>) {
        println("Collect result:")
        list.forEach { item ->
            println("[${item.interfaceName}]")
            item.classList.forEach {
                println("\t $it")
            }
        }
    }

    private fun directorySourceKey(file: File): String {
        return "dir:${file.absolutePath}"
    }

    private fun jarSourceKey(file: File): String {
        return "jar:${file.absolutePath}"
    }

    private data class CollectedRouteMetadata(
        val targetList: List<ScanSetting>,
        val routeIndexJson: String,
    )
}
