package com.example.wpqkf

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 카카오 개발자 콘솔에서 발급받은 '네이티브 앱 키' 입력
        KakaoSdk.init(this, "c0fd3ed94485564b5e79fbb2f18aadb7")
    }
}