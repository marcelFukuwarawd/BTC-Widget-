package com.meuapp.btcwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_update_now).setOnClickListener {
            val intent = Intent(this, BitcoinWidgetProvider::class.java).apply {
                action = BitcoinWidgetProvider.ACTION_MANUAL_REFRESH
            }
            sendBroadcast(intent)
            Toast.makeText(this, "Atualizando preço do Bitcoin...", Toast.LENGTH_SHORT).show()
        }
    }
}
