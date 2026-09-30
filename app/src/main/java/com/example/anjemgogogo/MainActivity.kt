package com.example.anjemgogogo

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ikon status bar terang (putih) karena latar header berwarna biru,
        // ikon navigation bar gelap karena latar bawah putih.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        val statusBarBg = findViewById<View>(R.id.statusBarBg)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Status bar diisi warna biru lewat view khusus
            statusBarBg.layoutParams = statusBarBg.layoutParams.apply { height = systemBars.top }
            // Bagian bawah mengikuti keyboard bila sedang muncul (supaya kolom chat tidak tertutup)
            v.setPadding(systemBars.left, 0, systemBars.right, maxOf(systemBars.bottom, ime.bottom))
            insets
        }
    }
}
