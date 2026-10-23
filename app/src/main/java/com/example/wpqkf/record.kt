package com.example.wpqkf

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VideoRecordItem(
    val file: File,
    val formattedName: String,
    val sizeText: String,
    val sizeInBytes: Long,
    val recordedAt: Long,
    val durationText: String,
    val thumbnail: Bitmap?
)

class VideoRecordAdapter(
    private val onPlayClick: (VideoRecordItem) -> Unit,
    private val onDeleteClick: (VideoRecordItem) -> Unit
) : ListAdapter<VideoRecordItem, VideoRecordAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VideoRecordItem>() {
            override fun areItemsTheSame(a: VideoRecordItem, b: VideoRecordItem): Boolean =
                a.file.absolutePath == b.file.absolutePath

            override fun areContentsTheSame(a: VideoRecordItem, b: VideoRecordItem): Boolean =
                a.recordedAt == b.recordedAt && a.sizeInBytes == b.sizeInBytes && a.durationText == b.durationText
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivVideoIcon: ImageView = view.findViewById(R.id.ivVideoIcon)
        val tvVideoName: TextView = view.findViewById(R.id.tvVideoName)
        val tvVideoSize: TextView = view.findViewById(R.id.tvVideoSize)
        val btnPlayVideo: ImageButton = view.findViewById(R.id.btnPlayVideo)
        val btnDeleteVideo: ImageButton = view.findViewById(R.id.btnDeleteVideo)
        val layoutItem: View = view.findViewById(R.id.layoutItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvVideoName.text = item.formattedName
        holder.tvVideoSize.text = "${item.durationText} · ${item.sizeText}"

        if (item.thumbnail != null) {
            holder.ivVideoIcon.setImageBitmap(item.thumbnail)
        } else {
            holder.ivVideoIcon.setImageResource(android.R.drawable.ic_media_play)
        }

        holder.layoutItem.setOnClickListener { onPlayClick(item) }
        holder.btnPlayVideo.setOnClickListener { onPlayClick(item) }
        holder.btnDeleteVideo.setOnClickListener { onDeleteClick(item) }
    }
}

class record : AppCompatActivity() {

    private lateinit var mBtnSchedule2: ImageButton
    private lateinit var mBtnRecord2: ImageButton
    private lateinit var mBtnHome2: ImageButton
    private lateinit var mBtnAi2: ImageButton
    private lateinit var mBtnProfile2: ImageButton

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvStorageUsage: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: VideoRecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_record)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvStorageUsage = findViewById(R.id.tvStorageUsage)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerViewRecord)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = VideoRecordAdapter(
            onPlayClick = { item -> playVideo(item.file) },
            onDeleteClick = { item -> confirmDeleteVideo(item) }
        )
        recyclerView.adapter = adapter

        setupNavigationButtons()
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun loadRecordings() {
        lifecycleScope.launch {
            val (items, totalBytes) = withContext(Dispatchers.IO) {
                val recordDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ai_recordings")
                if (!recordDir.exists()) recordDir.mkdirs()

                val allFiles = recordDir.listFiles()
                Log.d("RecordTab", "경로: ${recordDir.absolutePath}, 존재=${recordDir.exists()}")
                Log.d("RecordTab", "전체 파일 수=${allFiles?.size}")
                allFiles?.forEach {
                    Log.d("RecordTab", "파일: ${it.name}, 크기=${it.length()}, 확장자=${it.extension}")
                }

                val videoFiles = recordDir.listFiles { file ->
                    file.isFile && file.extension.equals("mp4", ignoreCase = true) && file.length() > 0
                }?.sortedByDescending { parseRecordTime(it) } ?: emptyList()

                var bytesSum = 0L
                val list = videoFiles.map { file ->
                    val bytes = file.length()
                    bytesSum += bytes

                    val recordedAt = parseRecordTime(file)
                    val formattedName = formatFileName(recordedAt)
                    val sizeText = formatFileSize(bytes)
                    val durationText = readDuration(file)
                    val thumbnail = readThumbnail(file)

                    VideoRecordItem(file, formattedName, sizeText, bytes, recordedAt, durationText, thumbnail)
                }

                list to bytesSum
            }

            Log.d("RecordTab", "표시할 최종 항목 수=${items.size}")

            val limitGb = VideoStorageManager.MAX_STORAGE_BYTES / (1024.0 * 1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)

            tvStorageUsage.text = String.format(
                Locale.getDefault(),
                "저장 용량: %.1f MB / %.0f GB (초과 시 오래된 영상부터 자동 삭제)",
                totalMb, limitGb
            )

            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE

            adapter.submitList(items)
        }
    }

    private fun parseRecordTime(file: File): Long {
        val raw = file.nameWithoutExtension.removePrefix("REC_")
        return try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).parse(raw)?.time
                ?: file.lastModified()
        } catch (e: Exception) {
            file.lastModified()
        }
    }

    private fun formatFileName(timeMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy년 MM월 dd일 HH시 mm분 녹화", Locale.KOREA)
        return sdf.format(Date(timeMillis))
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1.0) {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        } else {
            val kb = bytes.toDouble() / 1024
            String.format(Locale.getDefault(), "%.1f KB", kb)
        }
    }

    private fun readDuration(file: File): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val sec = ms / 1000
            String.format(Locale.getDefault(), "%d분 %02d초", sec / 60, sec % 60)
        } catch (e: Exception) {
            "-"
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun readThumbnail(file: File): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(160, 160), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun playVideo(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "영상을 재생할 수 없습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteVideo(item: VideoRecordItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("영상 삭제")
            .setMessage("선택한 녹화 영상을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                if (item.file.delete()) {
                    Toast.makeText(this, "영상이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    loadRecordings()
                } else {
                    Toast.makeText(this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun navigateTo(target: Class<*>) {
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }

    private fun setupNavigationButtons() {
        mBtnSchedule2 = findViewById(R.id.schedule)
        mBtnRecord2 = findViewById(R.id.record)
        mBtnHome2 = findViewById(R.id.home)
        mBtnAi2 = findViewById(R.id.ai)
        mBtnProfile2 = findViewById(R.id.profile)

        mBtnHome2.setOnClickListener { navigateTo(MainActivity::class.java) }
        mBtnSchedule2.setOnClickListener { navigateTo(schedule::class.java) }
        mBtnAi2.setOnClickListener { navigateTo(ai::class.java) }
        mBtnProfile2.setOnClickListener { navigateTo(profile::class.java) }
    }
}
