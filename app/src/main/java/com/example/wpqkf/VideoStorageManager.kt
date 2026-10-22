package com.example.wpqkf

import java.io.File

class VideoStorageManager(private val storageDir: File) {
    // 10 Gigabytes in Bytes
    private val maxStorageBytes: Long = 10L * 1024L * 1024L * 1024L

    /**
     * 저장소 용량을 확인하고 10GB를 초과하면 가장 오래된 파일부터 삭제합니다.
     */
    fun checkAndCleanStorage() {
        if (!storageDir.exists()) storageDir.mkdirs()

        val files = storageDir.listFiles() ?: return
        var currentTotalSize = files.sumOf { it.length() }

        if (currentTotalSize > maxStorageBytes) {
            // 가장 오래된 파일 순서대로 정렬 (lastModified 기준)
            val sortedFiles = files.sortedBy { it.lastModified() }

            for (file in sortedFiles) {
                val fileSize = file.length()
                if (file.delete()) {
                    currentTotalSize -= fileSize
                    // 10GB 이하(안전 마진 90%)로 내려오면 삭제 중단
                    if (currentTotalSize <= maxStorageBytes * 0.9) {
                        break
                    }
                }
            }
        }
    }
}
