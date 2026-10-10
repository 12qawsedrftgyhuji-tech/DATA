import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
android {
    namespace = "com.example.wpqkf"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.wpqkf"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}
val cameraxVersion = "1.3.4"
dependencies {
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    // Firebase BoM (Firebase 라이브러리들의 버전을 통일하여 관리)
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")//구글 플레이: implementation("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1")
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))

    // Firebase 관련 (BoM 사용으로 버전 명시 생략 가능)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation("com.google.firebase:firebase-storage-ktx:21.0.1")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-vertexai:16.0.0-beta02")

    // Room 데이터베이스
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // AndroidX & Lifecycle
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // gemini 대화용
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Google Identity & Credentials
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Glide 이미지 로더
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 테스트 라이브러리
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}