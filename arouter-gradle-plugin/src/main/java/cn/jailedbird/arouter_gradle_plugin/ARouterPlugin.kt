package cn.jailedbird.arouter_gradle_plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.plugins.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class ARouterPlugin : Plugin<Project> {
    companion object {
        const val EXTENSION_CONFIG_NAME = "arouter_config"
    }

    override fun apply(project: Project) {
        if (project.plugins.hasPlugin(AppPlugin::class.java)) {
            project.extensions.create(EXTENSION_CONFIG_NAME, ARouterConfig::class.java)
            println("Init ARouterGradlePlugin")
            val androidComponents =
                project.extensions.getByType(AndroidComponentsExtension::class.java)

            androidComponents.onVariants { variant ->
                val config = project.extensions.getByType(ARouterConfig::class.java)

                if (config.disableTransform) {
                    println("Skip ARouter Transform! (disableTransform=true) variant=${variant.name}")
                    return@onVariants
                }

                if (config.disableTransformWhenDebugBuild && variant.name.contains("debug", ignoreCase = true)) {
                    println("Skip ARouter Transform When Debug Build! variant=${variant.name}")
                    return@onVariants
                }

                val arouterBuildDir = project.layout.buildDirectory.dir("intermediates/arouter/${variant.name}")
                val taskProviderTransformAllClassesTask =
                    project.tasks.register(
                        "${variant.name}TransformAllClassesTask",
                        TransformAllClassesTask::class.java
                    ) { task ->
                        task.variantName.set(variant.name)
                        task.onlyInjectWhenRouteChanged.set(config.onlyInjectWhenRouteChanged)
                        task.logRouteFingerprint.set(config.logRouteFingerprint)
                        task.incrementalDirectories.from(task.allDirectories)
                        task.incrementalJars.from(task.allJars)
                        task.routeMetadataOutput.set(arouterBuildDir.map { it.file("route-metadata.txt") })
                        task.routeIndexOutput.set(arouterBuildDir.map { it.file("route-index.json") })
                        task.routeFingerprintOutput.set(arouterBuildDir.map { it.file("route-fingerprint.txt") })
                        task.lastAppliedFingerprintOutput.set(arouterBuildDir.map { it.file("last-applied-fingerprint.txt") })
                        task.cachedInjectedClassOutput.set(arouterBuildDir.map { it.file("LogisticsCenter.injected.class") })
                        task.routeScanStateOutput.set(arouterBuildDir.map { it.file("route-scan-state.txt") })
                    }

                variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                    .use(taskProviderTransformAllClassesTask)
                    .toTransform(
                        ScopedArtifact.CLASSES,
                        TransformAllClassesTask::allJars,
                        TransformAllClassesTask::allDirectories,
                        TransformAllClassesTask::output
                    )
            }
        }
    }
}
