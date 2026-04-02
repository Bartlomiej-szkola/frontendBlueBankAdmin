package com.example.bluebankadmin

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ClientDetailsActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private val client = OkHttpClient()
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var accountNumber: String

    private lateinit var tvAccountTitle: TextView
    private lateinit var etEditName: EditText
    private lateinit var etEditSurname: EditText
    private lateinit var etEditPhone: EditText
    private lateinit var layoutCardsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_details)

        accountNumber = intent.getStringExtra("ACCOUNT_NUMBER") ?: return
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        tvAccountTitle = findViewById(R.id.tvAccountTitle)
        etEditName = findViewById(R.id.etEditName)
        etEditSurname = findViewById(R.id.etEditSurname)
        etEditPhone = findViewById(R.id.etEditPhone)
        layoutCardsContainer = findViewById(R.id.layoutCardsContainer)

        findViewById<Button>(R.id.btnSaveChanges).setOnClickListener {
            saveClientChanges()
        }

        loadClientData()
    }

    // 1. POBIERANIE DANYCH I LISTY KART
    private fun loadClientData() {
        val request = Request.Builder().url("${AppConfig.BANK_API}/account/$accountNumber").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    runOnUiThread {
                        tvAccountTitle.text = "Konto: $accountNumber"
                        etEditName.setText(json.getString("ownerName"))
                        etEditSurname.setText(json.getString("ownerSurname"))
                        etEditPhone.setText(json.getString("phoneNumber"))

                        // Generowanie widoku kart
                        layoutCardsContainer.removeAllViews()
                        val cardsArray = json.getJSONArray("cards")
                        for (i in 0 until cardsArray.length()) {
                            val cardObj = cardsArray.getJSONObject(i)
                            addCardViewToLayout(cardObj.getString("cardUid"), cardObj.getBoolean("active"))
                        }
                    }
                }
            }
        })
    }

    // Generuje mały widok dla każdej przypisanej karty (ze Switch-em)
    private fun addCardViewToLayout(cardUid: String, isActive: Boolean) {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val tvCardUid = TextView(this).apply {
            text = "Karta: $cardUid"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switchActive = Switch(this).apply {
            isChecked = isActive
            text = if (isActive) "AKTYWNA" else "ZABLOKOWANA"
            setOnCheckedChangeListener { _, isChecked ->
                text = if (isChecked) "AKTYWNA" else "ZABLOKOWANA"
                changeCardStatus(cardUid, isChecked)
            }
        }

        cardLayout.addView(tvCardUid)
        cardLayout.addView(switchActive)
        layoutCardsContainer.addView(cardLayout)
    }

    // 2. BLOKOWANIE / ODBLOKOWANIE KARTY W BAZIE
    private fun changeCardStatus(cardUid: String, isActive: Boolean) {
        val request = Request.Builder()
            .url("${AppConfig.ADMIN_API}/card/$cardUid/status?isActive=$isActive")
            .patch(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@ClientDetailsActivity, "Zaktualizowano status karty!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // 3. ZAPISYWANIE EDYTOWANYCH DANYCH (Imię, Nazwisko, Telefon)
    private fun saveClientChanges() {
        val url = "${AppConfig.ADMIN_API}/account/$accountNumber" +
                "?name=${etEditName.text}&surname=${etEditSurname.text}&phone=${etEditPhone.text}"

        val request = Request.Builder()
            .url(url)
            .put(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.code == 409) {
                        Toast.makeText(this@ClientDetailsActivity, "BŁĄD: Ktoś inny ma ten telefon!", Toast.LENGTH_LONG).show()
                    } else if (response.isSuccessful) {
                        Toast.makeText(this@ClientDetailsActivity, "Zapisano zmiany!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 4. DODAWANIE KOLEJNYCH KART W LOCIE (Obsługa NFC)
    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        val cardUid = tag.id.joinToString("") { "%02X".format(it) }

        val request = Request.Builder()
            .url("${AppConfig.BANK_API}/card?accountNumber=$accountNumber&cardUid=$cardUid")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ClientDetailsActivity, "DODANO KARTĘ: $cardUid", Toast.LENGTH_SHORT).show()
                        loadClientData() // Odświeżamy listę kart na ekranie!
                    } else {
                        Toast.makeText(this@ClientDetailsActivity, "Ta karta jest już w systemie!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}