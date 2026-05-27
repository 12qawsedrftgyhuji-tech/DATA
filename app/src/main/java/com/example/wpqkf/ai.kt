package com.example.wpqkf

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ai : AppCompatActivity() {
    private lateinit var mBtnSchedule4: Button
    private lateinit var mBtnRecord4: Button
    private lateinit var mBtnHome4: Button
    private lateinit var mBtnAi4: Button
    private lateinit var mBtnProfile4: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mBtnSchedule4 = findViewById(R.id.scheduleai)
        mBtnRecord4 = findViewById(R.id.recordai)
        mBtnHome4 = findViewById(R.id.homeai)
        mBtnAi4 = findViewById(R.id.aiai)
        mBtnProfile4 = findViewById(R.id.profileai)

        mBtnAi4.setTextColor(Color.BLACK)

        mBtnHome4.setOnClickListener {
            val intent = Intent(this@ai, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        mBtnSchedule4.setOnClickListener {
            val intent = Intent(this@ai, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord4.setOnClickListener {
            val intent = Intent(this@ai, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile4.setOnClickListener {
            val intent = Intent(this@ai, profile::class.java)
            startActivity(intent)
            finish()
        }
    }
}