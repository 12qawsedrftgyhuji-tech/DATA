// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.gms.google.services) apply false
}

// 빌드 결과물을 지우는 clean 태스크 추가
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}