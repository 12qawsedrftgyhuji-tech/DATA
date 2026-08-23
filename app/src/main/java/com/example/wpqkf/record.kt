package com.example.wpqkf

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class record : AppCompatActivity() {
    private lateinit var mBtnSchedule2: ImageButton
    private lateinit var mBtnRecord2: ImageButton
    private lateinit var mBtnHome2: ImageButton
    private lateinit var mBtnAi2: ImageButton
    private lateinit var mBtnProfile2: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_record)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mBtnSchedule2 = findViewById(R.id.schedule)
        mBtnRecord2 = findViewById(R.id.record)
        mBtnHome2 = findViewById(R.id.home)
        mBtnAi2 = findViewById(R.id.ai)
        mBtnProfile2 = findViewById(R.id.profile)

        mBtnHome2.setOnClickListener {
            val intent = Intent(this@record, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnSchedule2.setOnClickListener {
            val intent = Intent(this@record, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnAi2.setOnClickListener {
            val intent = Intent(this@record, ai::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile2.setOnClickListener {
            val intent = Intent(this@record, profile::class.java)
            startActivity(intent)
            finish()
        }
    }
}