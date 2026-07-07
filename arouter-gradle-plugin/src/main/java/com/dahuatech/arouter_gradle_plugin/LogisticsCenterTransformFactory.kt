@file:Suppress("SpellCheckingInspection")

package com.dahuatech.arouter_gradle_plugin

import com.dahuatech.arouter_gradle_plugin.utils.InjectUtils
import com.dahuatech.arouter_gradle_plugin.utils.RouteMetadataUtils
import com.dahuatech.arouter_gradle_plugin.utils.ScanSetting
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.objectweb.asm.ClassVisitor

abstract class LogisticsCenterTransformFactory : AsmClassVisitorFactory<LogisticsCenterTransformFactory.Parameters> {

    interface Parameters : InstrumentationParameters {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val routeMetadataFile: RegularFileProperty

        @get:Input
        val variantName: Property<String>
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className == ScanSetting.GENERATE_TO_CLASS_NAME.replace('/', '.')
    }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        val metadataFile = parameters.get().routeMetadataFile.asFile.get()
        val targetList = RouteMetadataUtils.parseRouteMetadataText(
            if (metadataFile.exists()) metadataFile.readText() else ""
        )
        return InjectUtils.createClassVisitor(nextClassVisitor, targetList)
    }
}
