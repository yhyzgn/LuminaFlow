plugins {
    alias(libs.plugins.android.application)
//    alias(libs.plugins.kotlin.android)
//    alias(libs.plugins.kotlin.kapt)      // ← 正确使用 alias
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.lumina.flow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumina.flow"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.0"
    }

    // ==================== 修复 OkHttp 5.3.2 重复文件 ====================
    packaging {
        resources {
            pickFirsts.add("META-INF/kotlin-project-structure-metadata.json")
            // 如果以后还有其他 META-INF 冲突，可以继续添加 pickFirsts
            pickFirsts.add("META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/LICENSE*")
            pickFirsts.add("META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)   // 推荐添加，包含更多图标

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)           // kapt 配置

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)                    // kapt 配置
    implementation(libs.hilt.navigation.compose)

    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.okhttp.jvm)
    implementation(libs.snakeyaml)
}