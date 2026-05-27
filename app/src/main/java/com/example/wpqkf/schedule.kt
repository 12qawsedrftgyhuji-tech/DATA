package com.example.wpqkf

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class schedule : AppCompatActivity() {
    private lateinit var mBtnSchedule3: Button
    private lateinit var mBtnRecord3: Button
    private lateinit var mBtnHome3: Button
    private lateinit var mBtnAi3: Button
    private lateinit var mBtnProfile3: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_schedule)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mBtnSchedule3 = findViewById(R.id.schedulesc)
        mBtnRecord3 = findViewById(R.id.recordsc)
        mBtnHome3 = findViewById(R.id.homesc)
        mBtnAi3 = findViewById(R.id.aisc)
        mBtnProfile3 = findViewById(R.id.profilesc)

        mBtnSchedule3.setTextColor(Color.BLACK)

        mBtnHome3.setOnClickListener {
            val intent = Intent(this@schedule, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord3.setOnClickListener {
            val intent = Intent(this@schedule, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnAi3.setOnClickListener {
            val intent = Intent(this@schedule, ai::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile3.setOnClickListener {
            val intent = Intent(this@schedule, profile::class.java)
            startActivity(intent)
            finish()
        }
    }
}