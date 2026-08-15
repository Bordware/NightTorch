import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    // AGP 9.x has built-in Kotlin support, so there is no standalone
    // org.jetbrains.kotlin.android plugin to apply here.
    alias(libs.plugins.android.application)
    // Still required with AGP 9's built-in Kotlin: AGP fails configuration if
    // buildFeatures.compose is on without the Compose Compiler Gradle plugin.
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.bordware.nighttorch"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bordware.nighttorch"
        // minSdk 26: java.time.LocalTime is available natively from API 26, which
        // avoids core library desugaring. See docs/architecture.md.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Credentials come from keystore.properties, which is gitignored, and the
            // keystore itself never enters the repository. Losing that file means the app can
            // never be updated on Play under the same listing again, so it belongs in a
            // password manager or an offline backup — not here.
            //
            // When the file is absent — a fresh clone, or CI — the release build is left
            // unsigned rather than failing. That keeps `./gradlew build` working for
            // contributors who have no business holding the release key.
            val keystoreProperties = rootProject.file("keystore.properties")
            if (keystoreProperties.exists()) {
                val properties = Properties()
                keystoreProperties.inputStream().use(properties::load)
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
                .takeIf { rootProject.file("keystore.properties").exists() }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    // Pinned forward: see the note in libs.versions.toml.
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}