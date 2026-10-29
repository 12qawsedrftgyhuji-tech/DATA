package com.example.wpqkf

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, "c0fd3ed94485564b5e79fbb2f18aadb7")
    }
}