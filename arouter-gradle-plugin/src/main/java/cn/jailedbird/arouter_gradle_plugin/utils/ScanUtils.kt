package cn.jailedbird.arouter_gradle_plugin.utils

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.jar.JarFile

/**
 * Scan all class in the package: com/alibaba/android/arouter/
 * find out all routers, interceptors and providers
 */
@Suppress("SpellCheckingInspection")
object ScanUtils {

    fun scanJar(jarFile: File, targetList: List<ScanSetting>) {
        if (!jarFile.exists()) {
            return
        }
        JarFile(jarFile).use { file ->
            val enumeration = file.entries()
            while (enumeration.hasMoreElements()) {
                val jarEntry = enumeration.nextElement()
                if (jarEntry.isDirectory || !shouldProcessClass(jarEntry.name)) {
                    continue
                }
                file.getInputStream(jarEntry).use { inputStream ->
                    scanClass(
                        inputStream = inputStream,
                        targetList = targetList,
                        autoClose = false,
                        sourceDescription = "${jarFile.absolutePath}!/${jarEntry.name}"
                    )
                }
            }
        }
    }

    @Suppress("unused")
    /**
     * Exclude scan jar, you can custmize yourself
     * */
    fun shouldProcessPreDexJar(path: String): Boolean {
        return !path.contains("com.android.support") && !path.contains("/android/m2repository")
    }

    fun shouldProcessClass(entryName: String): Boolean {
        return entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME) && entryName.endsWith(".class")
    }

    @Suppress("unused")
    fun scanClass(file: File, targetList: List<ScanSetting>, autoClose: Boolean = true) {
        scanClass(FileInputStream(file), targetList, autoClose, file.absolutePath)
    }

    fun scanClass(
        inputStream: InputStream,
        targetList: List<ScanSetting>,
        autoClose: Boolean = true,
        sourceDescription: String = "unknown"
    ) {
        val classBytes = try {
            inputStream.readBytes()
        } finally {
            if (autoClose) {
                inputStream.close()
            }
        }
        if (!isValidClassBytes(classBytes)) {
            println("[Warning] Skip invalid ARouter route bytecode: $sourceDescription")
            return
        }
        try {
            val cr = ClassReader(classBytes)
            val cw = ClassWriter(cr, 0)
            val cv = ScanClassVisitor(Opcodes.ASM9, cw, targetList)
            cr.accept(cv, ClassReader.EXPAND_FRAMES)
        } catch (e: Exception) {
            println("[Warning] Skip unreadable ARouter route class: $sourceDescription, error=$e")
        }
    }

    private fun isValidClassBytes(classBytes: ByteArray): Boolean {
        return classBytes.size >= 4 &&
            classBytes[0] == 0xCA.toByte() &&
            classBytes[1] == 0xFE.toByte() &&
            classBytes[2] == 0xBA.toByte() &&
            classBytes[3] == 0xBE.toByte()
    }

    class ScanClassVisitor(
        api: Int, cv: ClassVisitor, private val targetRegisterList: List<ScanSetting>
    ) : ClassVisitor(api, cv) {
        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            targetRegisterList.forEach { ext ->
                interfaces?.forEach { itName ->
                    if (itName == ext.interfaceName) {
                        // fix repeated inject init code when Multi-channel packaging
                        if (name != null && !ext.classList.contains(name)) {
                            ext.classList.add(name)
                        }
                    }
                }
            }
        }
    }
}
