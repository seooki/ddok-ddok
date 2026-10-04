import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 서명 정보는 저장소에 넣지 않는 keystore.properties에서 읽는다. 파일이 없으면 release는 서명 없이 빌드된다.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.seooki.ddokddok"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.seooki.ddokddok"
        minSdk = 31
        // 37로 올리기 전에 기본 방식(ACQUIRE_CAUSES_WAKEUP)이 TURN_SCREEN_ON 권한 없이도 동작하는지 확인해야 한다.
        // AOSP는 이 권한 요구를 targetSdk 기준으로 켜도록 예고해 두었다.
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 개인정보 가드: 병합 매니페스트에 INTERNET 권한이 들어오면 빌드를 실패시킨다.
// "알림 정보는 폰 밖으로 나가지 않는다"는 약속을 의존성 추가로 조용히 깨지 않게 assemble 때마다 검증한다.
androidComponents {
    onVariants { variant ->
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
        val checkTask = tasks.register("check${variantName}NoInternetPermission") {
            group = "verification"
            description = "병합 매니페스트에 INTERNET 권한이 없는지 검증"
            inputs.file(mergedManifest)
            doLast {
                val manifest = mergedManifest.get().asFile.readText()
                if (manifest.contains("android.permission.INTERNET")) {
                    throw GradleException("INTERNET 권한이 $variantName 병합 매니페스트에 있습니다. 의존성을 확인하세요.")
                }
            }
        }
        tasks.matching { it.name == "assemble$variantName" || it.name == "bundle$variantName" }
            .configureEach { dependsOn(checkTask) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
