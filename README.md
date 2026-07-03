# ArouterGradlePlugin

[![GitHub stars](https://img.shields.io/github/stars/JailedBird/ArouterGradlePlugin.svg)](https://github.com/JailedBird/ArouterGradlePlugin/stargazers) [![GitHub forks](https://img.shields.io/github/forks/JailedBird/ArouterGradlePlugin.svg)](https://github.com/JailedBird/ArouterGradlePlugin/network/members) [![GitHub issues](https://img.shields.io/github/issues/JailedBird/ArouterGradlePlugin.svg)](https://github.com/JailedBird/ArouterGradlePlugin/issues) [![GitHub license](https://img.shields.io/github/license/JailedBird/ArouterGradlePlugin.svg)](https://github.com/JailedBird/ArouterGradlePlugin/blob/master/LICENSE)

## 简介

本插件可实现AGP7.4+和AGP8下[ARouter](https://github.com/alibaba/ARouter)框架自动化插桩，使用方法和`com.alibaba:arouter-register` 完全一致，无缝替换；



## 更新说明

2026/01/12，更新 支持Java21  [`1.0.3-java21`]

- 说明：感谢 [jjjjjjava](https://github.com/jjjjjjava)  提交对 Java21的PR [https://github.com/JailedBird/ArouterGradlePlugin/pull/16](https://github.com/JailedBird/ArouterGradlePlugin/pull/16) 修复，升级对应ASM版本 & Java支持版本，有需求的朋友可以先试试 1.0.3-java21 版本；

  

2025/03/24，更新

- 说明：[issue 5](https://github.com/JailedBird/ArouterGradlePlugin/issues/5) 修复，解决多个Transform插件编译错误，并在demo工程中更新AGP8.1示例；
- 根因：宿主工程中低版本AGP存在bug，导致多个issue5中的多插件输出目录相同，产生写入冲突；
- 解决：宿主工程升级到AGP8.1+, 多个transform的产物生成目录会根据taskName自动区分，完成修复；
- 特别说明：仅需更新宿主工程AGP，无需更新此插件；



最新版本1.0.2, 修复和优化

- debug阶段支持禁用插桩
- ASM5->ASM7
- 新增混淆规则配置 （需要手动配置）

为了不影响篇幅，请到最底部查阅详细内容；



## 导入方法

插件发布在 [ArouterPlugin](https://plugins.gradle.org/plugin/io.github.JailedBird.ARouterPlugin) ，点开即可查阅最全面的插件导入方式；

**Koltin**

Using the [plugins DSL](https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block):

```kotlin
plugins {
    id("io.github.JailedBird.ARouterPlugin") version "1.0.2"
}
```

Using [legacy plugin application](https://docs.gradle.org/current/userguide/plugins.html#sec:old_plugin_application):

```kotlin
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("io.github.JailedBird:arouter-gradle-plugin:1.0.2")
    }
}

apply(plugin = "io.github.JailedBird.ARouterPlugin")
```

**Grovvy**

Using the [plugins DSL](https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block):

```groovy
plugins {
  id "io.github.JailedBird.ARouterPlugin" version "1.0.2"
}
```

Using [legacy plugin application](https://docs.gradle.org/current/userguide/plugins.html#sec:old_plugin_application):

```groovy
buildscript {
  repositories {
    gradlePluginPortal()
  }
  dependencies {
    classpath "io.github.JailedBird:arouter-gradle-plugin:1.0.2"
  }
}

apply plugin: "io.github.JailedBird.ARouterPlugin"
```



## 插桩代码

遍历注解处理器生成的路由信息，然后在loadRouterMap函数中插桩

```
override fun visitInsn(opcode: Int) {
    // generate code before return
    if (opcode in Opcodes.IRETURN..Opcodes.RETURN) {
        targetList?.forEach { scanSetting ->
            scanSetting.classList.forEach { name ->
                val className = name.replace("/", ".")
                mv.visitLdcInsn(className)// 类名
                
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    ScanSetting.GENERATE_TO_CLASS_NAME,
                    ScanSetting.REGISTER_METHOD_NAME,
                    "(Ljava/lang/String;)V",
                    false
                )
            }
        }
    }
    super.visitInsn(opcode)
}
```

插桩后字节码如下：

```
.method public static loadRouterMap()V
    .registers 1
    
    .line 63
    const/4 v0, 0x0
    sput-boolean v0, Lcom/alibaba/android/arouter/core/LogisticsCenter;->registerByPlugin:Z

    .line 68
    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Root$$app"
    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V
    
    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Root$$arouterapi"
    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V
    
    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Providers$$app"
    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V
    
    const-string v0, "com.alibaba.android.arouter.routes.ARouter$$Providers$$arouterapi"
    invoke-static {v0}, Lcom/alibaba/android/arouter/core/LogisticsCenter;->register(Ljava/lang/String;)V

    return-void
.end method
```



## 更新说明

### 1.0.2 更新内容

1、 解决[issue7](https://github.com/JailedBird/ArouterGradlePlugin/issues/7) debug阶段编译慢的问题；宿主模块启用如下配置，可避免在debug的变体下进行插桩，此时会通过Arouter原生遍历dex寻找路由文件，首次启动慢，之后会保存在SP中，速度应该还可以接受；缺点是路由表表更，需要清除应用数据才能生效；默认关闭，推荐开启（毕竟大项目编译非常慢）；

```
arouter_config {
    disableTransformWhenDebugBuild = true
}
```

补充：当前版本新增了一个更轻量的复用策略，在不关闭 transform 的前提下，也可以开启如下配置：

```
arouter_config {
    onlyInjectWhenRouteChanged = true
    logRouteFingerprint = false
}
```

行为说明：
- 插件会在 `build/intermediates/arouter/<variant>/` 下输出 `route-metadata.txt`、`route-index.json`、`route-fingerprint.txt`、`route-scan-state.txt` 和缓存的 `LogisticsCenter.injected.class`；
- 当 transform 任务因为普通业务 class 变化而被重新执行时，会基于 `route-scan-state.txt` 只增量处理变更过的 route class / jar，而不是重新扫描所有路由输入；
- 如果本次计算出的路由集合和 `LogisticsCenter` 原始字节码都没有变化，会直接复用上次生成的 `LogisticsCenter.class`，跳过再次 ASM 注入；
- 当前仍保留 `Scope.ALL` 的聚合复制输出流程，所以这不是完整的增量 transform，但已经把“全量扫描路由 + 重复注入”压缩成了“增量路由扫描 + 条件注入复用”。


2、 字节码插桩从ASM5升级到ASM7，解决 [issue6](https://github.com/JailedBird/ArouterGradlePlugin/issues/6) 其实我暂时没弄懂这个原理，只是改了插桩API的这个ASM版本参数；

3、 重要：demo添加混淆规则！ 分支 [bugfix/agp8.2.2_runtime_error](https://github.com/JailedBird/ArouterGradlePlugin/tree/bugfix/agp8.2.2_runtime_error)  实验发现AGP 8.2.2启用混淆minify后，貌似 会把 TypeWrapper的匿名内部类从泛型ParameterizedType混淆为Class，导致类型转换报错；

```
public class TypeWrapper<T> {
    protected final Type type;

    protected TypeWrapper() {
        Type superClass = getClass().getGenericSuperclass();

        type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }
}
```

导致对象注入时候，类型解析抛出异常；

```
    if(serializationService != null) {
      val res = substitute.intent?.extras?.getString("list")
      if(!res.isNullOrEmpty()) {
        val typeWrapper = object : TypeWrapper<MutableList<String>>(){}
        serializationService?.parseObject<MutableList<String>>(res, typeWrapper.type)?.let{
            	substitute.list = it
            }
      }
    }
```

添加如下混淆即可：【没出现问题，不加也行】

```
# 避免继承自TypeWrapper的匿名内部类(获取泛型T对应的Type)被混淆 导致Type获取失败
# Caused by: java.lang.ClassCastException: java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType
# at com.alibaba.android.arouter.facade.model.TypeWrapper.<init>(TypeWrapper.java:19)
# 这种情况下 就是匿名内部类被混淆导致 getClass().getGenericSuperclass() 从ParameterizedType变为Class从而导致Type类型转换异常
-keep class ** extends com.alibaba.android.arouter.facade.model.TypeWrapper { *; }
```





## 参考文献

- https://docs.gradle.org/

- https://github.com/android/gradle-recipes

- [你的插件想适配Transform Action? 可能还早了点](https://juejin.cn/post/7190196880469393463)

- [Transform API 废弃了，路由插件怎么办？](https://juejin.cn/post/7222091234100330554)

## 最后

相关文档见[issue1](https://github.com/JailedBird/ArouterGradlePlugin/issues/1)，如果对您有帮助，请点亮star支持作者😘
