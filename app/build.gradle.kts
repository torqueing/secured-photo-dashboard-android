import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// API base URL: the `api_base_url` Terraform output. Read from the API_BASE_URL
// environment variable (GitHub Actions secret) or `api.base.url` in the untracked
// local.properties, falling back to the dev environment so a fresh clone runs.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val defaultApiBaseUrl = "https://app.securecaptures.com/v1"
val apiBaseUrl: String = (
    System.getenv("API_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty("api.base.url")?.takeIf { it.isNotBlank() }
        ?: defaultApiBaseUrl
)
    .trim().trimEnd('/')

android {
    namespace = "com.photodashboard.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.securedphotodashboard.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        // Version-catalogue freshness is not a build failure.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation(libs.tink.android)
    implementation(libs.androidx.biometric)
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)

    testImplementation(libs.junit)
    testImplementation(libs.metadata.extractor)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room migration tests read the exported schemas.
android.sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
