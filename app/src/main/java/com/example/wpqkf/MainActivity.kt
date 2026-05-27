package com.example.wpqkf

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mBtnSchedule: Button
    private lateinit var mBtnRecord: Button
    private lateinit var mBtnHome: Button
    private lateinit var mBtnAi: Button
    private lateinit var mBtnProfile: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mBtnSchedule = findViewById(R.id.scheduleho)
        mBtnRecord = findViewById(R.id.recordho)
        mBtnHome = findViewById(R.id.homeho)
        mBtnAi = findViewById(R.id.aiho)
        mBtnProfile = findViewById(R.id.profileho)

        mBtnHome.setTextColor(Color.BLACK)

        mBtnSchedule.setOnClickListener {
            val intent = Intent(this@MainActivity, schedule::class.java)
            startActivity(intent)
            finish()
        }
        mBtnRecord.setOnClickListener {
            val intent = Intent(this@MainActivity, record::class.java)
            startActivity(intent)
            finish()
        }
        mBtnAi.setOnClickListener {
            val intent = Intent(this@MainActivity, ai::class.java)
            startActivity(intent)
            finish()
        }
        mBtnProfile.setOnClickListener {
            val intent = Intent(this@MainActivity, profile::class.java)
            startActivity(intent)
            finish()
        }
    }
}
