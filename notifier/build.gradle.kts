plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.seooki.ddokddok.notifier"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.seooki.ddokddok.notifier"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
