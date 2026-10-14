// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.google.devtools.ksp") version "2.3.6" apply false
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}

// 빌드 결과물을 지우는 clean 태스크 추가
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}