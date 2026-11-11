plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.prempoddar.oaf"
    compileSdk = 35

    defaultConfig {
        // OAF upstream supports Android 10 (API 29) and uses platform APIs unavailable below it.
        minSdk = 29
        consumerProguardFiles("proguard-rules.pro")
    }

    lint {
        // Compose lint reports this even when produceState assigns `value` in its producer.
        disable += "ProduceStateDoesNotAssignValue"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/module-info.class",
                "module-info.class",
            )
        }
    }
}

dependencies {
    implementation(libs.material.color.utilities)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.subject)
    implementation(libs.mlkit.text)
    implementation(libs.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.androidx.palette)
    implementation(libs.pdfbox)
    implementation(libs.poi)
    implementation(libs.poi.scratchpad)
    implementation(libs.commons.compress)
    implementation(libs.commons.net)
    implementation(libs.arsclib)
    implementation(libs.apksig)
    implementation(libs.jsch)
    implementation(libs.smbj) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    implementation(libs.okhttp)
    implementation(libs.xz)
    implementation(libs.junrar)
    implementation(libs.zip4j)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.androidsvg)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
