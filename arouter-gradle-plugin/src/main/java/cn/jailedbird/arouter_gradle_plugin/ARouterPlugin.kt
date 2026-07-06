package cn.jailedbird.arouter_gradle_plugin

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.plugins.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

@Suppress("unused")
class ARouterPlugin : Plugin<Project> {
    companion object {
        const val EXTENSION_CONFIG_NAME = "arouter_config"
        private val ARTIFACT_TYPE_ATTRIBUTE = Attribute.of("artifactType", String::class.java)
        private const val ANDROID_CLASSES_JAR_ARTIFACT_TYPE = "android-classes-jar"
        private const val JAR_ARTIFACT_TYPE = "jar"
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

                val arouterBuildDir = project.layout.buildDirectory.dir("intermediates/arouter/${variant.name}")
                val capitalizedVariantName = variant.name.replaceFirstChar { it.uppercaseChar() }
                val runtimeClasspathConfiguration =
                    project.configurations.findByName("${variant.name}RuntimeClasspath")

                if (config.onlyInjectWhenRouteChanged) {
                    val collectRouteMetadataTask =
                        project.tasks.register(
                            "${variant.name}CollectRouteMetadataTask",
                            CollectRouteMetadataTask::class.java
                        ) { task ->
                            task.variantName.set(variant.name)
                            task.logRouteFingerprint.set(config.logRouteFingerprint)
                            task.projectClassInputs.from(
                                project.layout.buildDirectory.dir("tmp/kotlin-classes/${variant.name}"),
                                project.layout.buildDirectory.dir("intermediates/javac/${variant.name}/classes")
                            )
                            task.classpathInputs.from(variant.compileClasspath)
                            runtimeClasspathConfiguration?.let { runtimeClasspath ->
                                task.classpathInputs.from(
                                    runtimeClasspath.incoming.artifactView { view ->
                                        view.attributes.attribute(
                                            ARTIFACT_TYPE_ATTRIBUTE,
                                            ANDROID_CLASSES_JAR_ARTIFACT_TYPE
                                        )
                                    }.files,
                                    runtimeClasspath.incoming.artifactView { view ->
                                        view.attributes.attribute(
                                            ARTIFACT_TYPE_ATTRIBUTE,
                                            JAR_ARTIFACT_TYPE
                                        )
                                    }.files
                                )
                            } ?: println(
                                "ARouter route collection(${variant.name}) runtime classpath not found, fallback to compileClasspath only"
                            )
                            task.dependsOn(
                                project.tasks.matching {
                                    it.name in setOf(
                                        "compile${capitalizedVariantName}Kotlin",
                                        "compile${capitalizedVariantName}JavaWithJavac",
                                        "ksp${capitalizedVariantName}Kotlin",
                                    )
                                }
                            )
                            task.routeMetadataOutput.set(arouterBuildDir.map { it.file("route-metadata.txt") })
                            task.routeIndexOutput.set(arouterBuildDir.map { it.file("route-index.json") })
                            task.routeFingerprintOutput.set(arouterBuildDir.map { it.file("route-fingerprint.txt") })
                            task.routeScanStateOutput.set(arouterBuildDir.map { it.file("route-scan-state.txt") })
                        }

                    val injectLogisticsCenterTask =
                        project.tasks.register(
                            "${variant.name}InjectLogisticsCenterTask",
                            InjectLogisticsCenterTask::class.java
                        ) { task ->
                            task.variantName.set(variant.name)
                            task.logRouteFingerprint.set(config.logRouteFingerprint)
                            task.routeMetadataInput.set(collectRouteMetadataTask.flatMap { it.routeMetadataOutput })
                            task.routeIndexInput.set(collectRouteMetadataTask.flatMap { it.routeIndexOutput })
                            task.routeFingerprintOutput.set(arouterBuildDir.map { it.file("inject-fingerprint.txt") })
                            task.lastAppliedFingerprintOutput.set(arouterBuildDir.map { it.file("last-applied-fingerprint.txt") })
                            task.cachedInjectedClassOutput.set(arouterBuildDir.map { it.file("LogisticsCenter.injected.class") })
                            task.dependsOn(collectRouteMetadataTask)
                        }

                    variant.artifacts.forScope(ScopedArtifacts.Scope.ALL)
                        .use(injectLogisticsCenterTask)
                        .toTransform(
                            ScopedArtifact.CLASSES,
                            InjectLogisticsCenterTask::allJars,
                            InjectLogisticsCenterTask::allDirectories,
                            InjectLogisticsCenterTask::output
                        )
                } else {
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
}
