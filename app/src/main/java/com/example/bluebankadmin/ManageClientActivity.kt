package com.example.bluebankadmin

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ManageClientActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private val client = OkHttpClient()
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var tvScanStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_client)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        tvScanStatus = findViewById(R.id.tvScanStatus)

        val etSearchAccount = findViewById<TextInputEditText>(R.id.etSearchAccount)
        val btnSearch = findViewById<Button>(R.id.btnSearch)

        btnSearch.setOnClickListener {
            val accNum = etSearchAccount.text.toString().trim()
            if (accNum.isNotEmpty()) {
                openClientDetails(accNum)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    // Odczyt NFC -> Szukanie po karcie
    override fun onTagDiscovered(tag: Tag) {
        val cardUid = tag.id.joinToString("") { "%02X".format(it) }
        runOnUiThread { tvScanStatus.text = "Szukanie karty: $cardUid..." }

        val request = Request.Builder()
            .url("${AppConfig.ADMIN_API}/card/$cardUid/client")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvScanStatus.text = "Błąd połączenia z serwerem!" }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val accNum = json.getString("accountNumber")

                    runOnUiThread {
                        tvScanStatus.text = "Znaleziono!"
                        openClientDetails(accNum)
                    }
                } else {
                    runOnUiThread { tvScanStatus.text = "Nie znaleziono klienta dla tej karty!" }
                }
            }
        })
    }

    private fun openClientDetails(accountNumber: String) {
        val intent = Intent(this, ClientDetailsActivity::class.java)
        intent.putExtra("ACCOUNT_NUMBER", accountNumber)
        startActivity(intent)
    }
}