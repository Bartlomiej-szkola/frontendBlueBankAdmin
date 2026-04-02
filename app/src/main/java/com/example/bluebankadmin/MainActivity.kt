package com.example.bluebankadmin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialCardView>(R.id.cardAddClient).setOnClickListener {
            startActivity(Intent(this, AddClientActivity::class.java))
        }

//        findViewById<MaterialCardView>(R.id.cardManageClient).setOnClickListener {
//            startActivity(Intent(this, ManageClientActivity::class.java))
//        }

//        findViewById<MaterialCardView>(R.id.cardScanNfc).setOnClickListener {
//            // Tymczasowo przekierowuje do menadżera, by pokazać popup NFC
//            val intent = Intent(this, ManageClientActivity::class.java)
//            intent.putExtra("SCAN_MODE", true)
//            startActivity(intent)
//        }
    }
}