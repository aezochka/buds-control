import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Подпись релиза. Keystore в репозитории больше НЕ хранится: параметры
// приходят из окружения (CI передаёт Secrets) или из local.properties
// (локальная сборка; файл в .gitignore, в нём ключи keystore.file,
// keystore.password, keystore.alias, keystore.keyPassword).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf(File::isFile)?.inputStream()?.use { load(it) }
}

fun signParam(envName: String, localName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(localName)?.takeIf { it.isNotBlank() }

val keystoreFile = signParam("KEYSTORE_FILE", "keystore.file")
    ?.let(::File)?.takeIf(File::isFile)
val canSignRelease = keystoreFile != null &&
    !signParam("KEYSTORE_PASSWORD", "keystore.password").isNullOrBlank() &&
    !signParam("KEY_ALIAS", "keystore.alias").isNullOrBlank() &&
    !signParam("KEY_PASSWORD", "keystore.keyPassword").isNullOrBlank()

android {
    namespace = "dev.aezochka.budscontrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.aezochka.budscontrol"
        minSdk = 26
        targetSdk = 36
        // CI передаёт номер через -PversionCode (1000 + run_number),
        // локальная сборка берёт значение отсюда.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 36
        versionName = (project.findProperty("versionName") as String?) ?: "8.0"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signParam("KEYSTORE_PASSWORD", "keystore.password")
                keyAlias = signParam("KEY_ALIAS", "keystore.alias")
                keyPassword = signParam("KEY_PASSWORD", "keystore.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Если ключ не настроен (локальная сборка без local.properties),
            // релиз подписывается debug-ключом, чтобы сборка не падала.
            // CI ключ передаёт всегда.
            signingConfig = if (canSignRelease) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
        // debug остаётся на стандартном debug-ключе Android.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                    "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
    // buildConfig нужен для записи версии в лог краша.
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
