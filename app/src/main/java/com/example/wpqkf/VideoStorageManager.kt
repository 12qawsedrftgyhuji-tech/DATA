package com.example.wpqkf

import java.io.File

class VideoStorageManager(private val storageDir: File) {

    companion object {
        // 10 Gigabytes in Bytes (한 곳에서 일관되게 관리)
        const val MAX_STORAGE_BYTES = 10L * 1024L * 1024L * 1024L
    }

    /**
     * 저장소 용량을 확인하고 10GB를 초과하면 가장 오래된 파일부터 순차적으로 삭제함.
     */
    fun checkAndCleanStorage() {
        if (!storageDir.exists()) storageDir.mkdirs()

        // 손상되거나 녹화 중인 .tmp 파일이 아닌 유효한 .mp4 파일만 대상으로 지정함.
        val files = storageDir.listFiles { f ->
            f.isFile && f.extension.equals("mp4", true)
        } ?: return

        var currentTotalSize = files.sumOf { it.length() }

        if (currentTotalSize > MAX_STORAGE_BYTES) {
            // 가장 오래된 파일 순서대로 정렬 (lastModified 기준)
            val sortedFiles = files.sortedBy { it.lastModified() }

            for (file in sortedFiles) {
                val fileSize = file.length()
                if (file.delete()) {
                    currentTotalSize -= fileSize
                    // 10GB 이하(안전 마진 90%)로 내려오면 삭제 중단함.
                    if (currentTotalSize <= (MAX_STORAGE_BYTES * 0.9).toLong()) {
                        break
                    }
                }
            }
        }
    }
}
