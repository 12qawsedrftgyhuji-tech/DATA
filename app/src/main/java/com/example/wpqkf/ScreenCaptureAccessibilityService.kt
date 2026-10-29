package com.example.wpqkf

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent

class ScreenCaptureAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ScreenCaptureAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("A11yCapture", "onServiceConnected: 연결됨")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("A11yCapture", "onUnbind: 연결 해제됨")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d("A11yCapture", "onDestroy")
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun captureScreen(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                val soft = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                hardware?.recycle()
                result.hardwareBuffer.close()
                callback(soft)
            }

            override fun onFailure(errorCode: Int) {
                Log.e("A11yCapture", "스크린샷 실패: $errorCode")
                callback(null)
            }
        })
    }
}
