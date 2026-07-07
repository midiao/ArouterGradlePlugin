@file:Suppress("SpellCheckingInspection")

package cn.jailedbird.arouter_gradle_plugin

import cn.jailedbird.arouter_gradle_plugin.utils.InjectUtils
import cn.jailedbird.arouter_gradle_plugin.utils.RouteMetadataUtils
import cn.jailedbird.arouter_gradle_plugin.utils.ScanSetting
import org.apache.commons.io.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipException

abstract class InjectLogisticsCenterTask : DefaultTask() {

    @get:Internal
    abstract val allDirectories: ListProperty<Directory>

    @get:Internal
    abstract val allJars: ListProperty<RegularFile>

    // 参与增量与输入变更判断，确保业务类变化会触发任务重跑
    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val incrementalDirectories: org.gradle.api.file.ConfigurableFileCollection

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val incrementalJars: org.gradle.api.file.ConfigurableFileCollection

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val logRouteFingerprint: Property<Boolean>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val routeMetadataInput: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val routeIndexInput: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val routeFingerprintOutput: RegularFileProperty

    @get:OutputFile
    abstract val lastAppliedFingerprintOutput: RegularFileProperty

    @get:OutputFile
    abstract val cachedInjectedClassOutput: RegularFileProperty

    @TaskAction
    fun taskAction() {
        println("Welcome to use ArouterGradlePlugin for AGP8: https://github.com/JailedBird/ArouterGradlePlugin")
        println("Arouter LogisticsCenter inject task start: variant=${variantName.get()}")
        val start = System.currentTimeMillis()
        val routeMetadataText = RouteMetadataUtils.readTextIfExists(routeMetadataInput.asFile.get()).orEmpty()
        val routeIndexJson = RouteMetadataUtils.readTextIfExists(routeIndexInput.asFile.get()).orEmpty()
        val targetList = RouteMetadataUtils.parseRouteMetadataText(routeMetadataText)

        output.asFile.get().parentFile?.mkdirs()
        JarOutputStream(output.asFile.get().outputStream()).use { jarOutput ->
            copyAllClasses(jarOutput)

            val injectSource = findLogisticsCenterBytes()
                ?: error(
                    "Can not find ARouter inject point(LogisticsCenter.class) in scoped class artifacts, Do you import ARouter API?"
                )
            val routeFingerprint = RouteMetadataUtils.computeFingerprint(routeIndexJson, injectSource)
            RouteMetadataUtils.writeTextIfChanged(routeFingerprintOutput.asFile.get(), routeFingerprint)
            if (logRouteFingerprint.getOrElse(false)) {
                println("ARouter route fingerprint(${variantName.get()})=$routeFingerprint")
                println("ARouter route index input=${routeIndexInput.asFile.get().absolutePath}")
            }
            val resultByteArray = buildInjectedBytecode(injectSource, targetList, routeFingerprint)
            jarOutput.saveEntry(
                ScanSetting.GENERATE_TO_CLASS_FILE_NAME,
                ByteArrayInputStream(resultByteArray)
            )
        }
        println("ARouter LogisticsCenter inject spend ${System.currentTimeMillis() - start} ms")
    }

    private fun copyAllClasses(jarOutput: JarOutputStream) {
        val leftSlash = File.separator == "/"
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

        allJars.get().forEach { sourceJar ->
            JarFile(sourceJar.asFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    try {
                        if (entry.isDirectory || entry.name.isEmpty()) {
                            continue
                        }
                        if (entry.name == ScanSetting.GENERATE_TO_CLASS_FILE_NAME) {
                            continue
                        }
                        jar.getInputStream(entry).use { input ->
                            jarOutput.saveEntry(entry.name, input)
                        }
                    } catch (e: Exception) {
                        if (e is ZipException && e.message?.contains("META-INF/MANIFEST.MF") == true) {
                            // Skip META-INF/MANIFEST.MF
                        } else {
                            println("[Warning] Merge [jar:entry] ${jar.name}:${entry.name}, error is $e ")
                        }
                    }
                }
            }
        }
    }

    private fun findLogisticsCenterBytes(): ByteArray? {
        allJars.get().forEach { sourceJar ->
            JarFile(sourceJar.asFile).use { jar ->
                val entry = jar.getJarEntry(ScanSetting.GENERATE_TO_CLASS_FILE_NAME) ?: return@use
                jar.getInputStream(entry).use { input ->
                    return IOUtils.toByteArray(input)
                }
            }
        }
        return null
    }

    private fun buildInjectedBytecode(
        originInject: ByteArray,
        targetList: List<cn.jailedbird.arouter_gradle_plugin.utils.ScanSetting>,
        routeFingerprint: String
    ): ByteArray {
        val lastAppliedFile = lastAppliedFingerprintOutput.asFile.get()
        val cachedInjectedClassFile = cachedInjectedClassOutput.asFile.get()
        val canReusePreviousInjection =
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
}
