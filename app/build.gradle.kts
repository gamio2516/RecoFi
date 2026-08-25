import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val recoFiKeystorePropertiesFile = rootProject.file("keystore.properties")
val recoFiKeystoreProperties = Properties().apply {
    if (recoFiKeystorePropertiesFile.isFile) recoFiKeystorePropertiesFile.inputStream().use(::load)
}

val copyDebugApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("outputs"))
    rename { "CardMemo-debug.apk" }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApk)
}

android {
    namespace = "jp.knaka.cardmemo"
    compileSdk = 36

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = "jp.knaka.cardmemo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    signingConfigs {
        if (recoFiKeystorePropertiesFile.isFile) {
            create("recoFiDebug") {
                val keyPath = recoFiKeystoreProperties.getProperty("storeFile")?.takeIf(String::isNotBlank) ?: error("keystore.properties: storeFile is required")
                storeFile = rootProject.file(keyPath).also { require(it.isFile) { "RecoFi keystore not found: $it" } }
                storePassword = recoFiKeystoreProperties.getProperty("storePassword")?.takeIf(String::isNotBlank) ?: error("keystore.properties: storePassword is required")
                keyAlias = recoFiKeystoreProperties.getProperty("keyAlias")?.takeIf(String::isNotBlank) ?: error("keystore.properties: keyAlias is required")
                keyPassword = recoFiKeystoreProperties.getProperty("keyPassword")?.takeIf(String::isNotBlank) ?: error("keystore.properties: keyPassword is required")
            }
        } else logger.warn("RecoFi dedicated debug keystore is not configured. Do not deploy to a real device without scripts/safe-deploy.ps1.")
    }

    buildTypes {
        debug {
            if (recoFiKeystorePropertiesFile.isFile) signingConfig = signingConfigs.getByName("recoFiDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.work:work-runtime:2.11.2")
    kapt("androidx.room:room-compiler:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
