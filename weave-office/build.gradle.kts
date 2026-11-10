import java.net.URI
import java.util.zip.ZipFile

plugins {
    id("com.android.library")
}

val weaveApkName = "weave-office-0.1.2-arm64-v8a.apk"
val weaveNativeDir = layout.buildDirectory.dir("generated/weave-native")
val prepareWeaveNative by tasks.registering {
    outputs.dir(weaveNativeDir)
    doLast {
        val output = weaveNativeDir.get().asFile.resolve("arm64-v8a")
        output.mkdirs()
        val required = listOf(
            "libboa_engine-cdcfbec451bd73f0.so",
            "libocrs-7303c3c1a1422eb6.so",
            "librten-90275ad490bfc1f6.so",
            "libweave_office_android.so",
        )
        if (required.all { File(output, it).isFile }) return@doLast

        val cache = gradle.gradleUserHomeDir.resolve("caches/weave-office/$weaveApkName")
        cache.parentFile.mkdirs()
        if (!cache.isFile) {
            val nearby = rootProject.layout.projectDirectory.asFile.parentFile.resolve(weaveApkName)
            if (nearby.isFile) {
                nearby.copyTo(cache, overwrite = true)
            } else {
                URI(
                    "https://github.com/frozencaviar/Weave-Office-Android/releases/download/" +
                        "android-v0.1.2/$weaveApkName",
                ).toURL().openStream().use { input ->
                    cache.outputStream().use { outputStream -> input.copyTo(outputStream) }
                }
            }
        }
        ZipFile(cache).use { zip ->
            required.forEach { name ->
                val entry = zip.getEntry("lib/arm64-v8a/$name")
                    ?: error("Missing $name in $weaveApkName")
                zip.getInputStream(entry).use { input ->
                    File(output, name).outputStream().use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }
            }
        }
    }
}

android {
    namespace = "io.github.frozencaviar.weaveoffice"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets["main"].jniLibs.srcDir(weaveNativeDir)
}

tasks.named("preBuild").configure { dependsOn(prepareWeaveNative) }

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.games:games-activity:4.4.0")
}
