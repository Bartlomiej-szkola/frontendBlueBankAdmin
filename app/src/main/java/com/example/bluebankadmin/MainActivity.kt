package com.example.bluebankadmin

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.random.Random

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private val client = OkHttpClient()
    private var nfcAdapter: NfcAdapter? = null

    // Zmienna przechowująca numer konta klienta, któremu właśnie wydajemy kartę
    private var currentAccountNumber: String? = null

    private lateinit var tvAccountInfo: TextView
    private lateinit var tvNfcStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val etName = findViewById<EditText>(R.id.etName)
        val etSurname = findViewById<EditText>(R.id.etSurname)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val btnCreateClient = findViewById<Button>(R.id.btnCreateClient)
        tvAccountInfo = findViewById(R.id.tvAccountInfo)
        tvNfcStatus = findViewById(R.id.tvNfcStatus)

        if (nfcAdapter == null) {
            Toast.makeText(this, "Brak modułu NFC!", Toast.LENGTH_LONG).show()
        }

        btnCreateClient.setOnClickListener {
            val name = etName.text.toString().trim()
            val surname = etSurname.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isNotEmpty() && surname.isNotEmpty() && phone.isNotEmpty()) {
                createBankClient(name, surname, phone)
            } else {
                Toast.makeText(this, "Wypełnij wszystkie dane", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 1. ZAPISYWANIE KLIENTA DO BAZY
    private fun createBankClient(name: String, surname: String, phone: String) {
        // Generujemy losowy 8-cyfrowy numer konta
        val newAccountNumber = Random.nextInt(10000000, 99999999).toString()
        val defaultPin = "1234" // PIN startowy (klient zmieni go w swojej apce)

        val json = JSONObject().apply {
            put("accountNumber", newAccountNumber)
            put("ownerName", name)
            put("ownerSurname", surname)
            put("phoneNumber", phone)
            put("mobileAppPin", defaultPin)
            put("balance", 100.0) // Startowe 100 zł w prezencie od banku ;)
        }.toString()

        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${AppConfig.BANK_API_URL}/account")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Błąd serwera", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        currentAccountNumber = newAccountNumber
                        tvAccountInfo.text = "Konto: $newAccountNumber\nPIN startowy: $defaultPin"
                        tvNfcStatus.text = "ZBLIŻ CZYSTĄ KARTĘ DO TELEFONU"
                        tvNfcStatus.setTextColor(android.graphics.Color.BLUE)
                    } else {
                        Toast.makeText(this@MainActivity, "Błąd tworzenia konta", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 2. CZYTANIE KARTY (Włączenie NFC)
    override fun onResume() {
        super.onResume()
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        nfcAdapter?.enableReaderMode(this, this, flags, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    // Ten event odpala się przy przyłożeniu karty
    override fun onTagDiscovered(tag: Tag) {
        if (currentAccountNumber == null) return // Jeśli nie założyliśmy konta, ignorujemy kartę

        // Zobacz jak prosta w Kotlinie jest konwersja bajtów na HEX!
        val cardUid = tag.id.joinToString("") { "%02X".format(it) }

        runOnUiThread {
            tvNfcStatus.text = "Odczytano: $cardUid. Łączenie z bankiem..."
        }

        linkCardToAccount(currentAccountNumber!!, cardUid)
    }

    // 3. PRZYPISANIE KARTY DO KONTA W BAZIE
    private fun linkCardToAccount(accountNumber: String, cardUid: String) {
        val url = "${AppConfig.BANK_API_URL}/card?accountNumber=$accountNumber&cardUid=$cardUid"

        // Puste Body dla metody POST z parametrami
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvNfcStatus.text = "Błąd połączenia. Spróbuj ponownie." }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        tvNfcStatus.text = "SUKCES! KARTA WYDANA."
                        tvNfcStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        // Blokujemy kolejne czytanie, dopóki administrator nie założy nowego konta
                        currentAccountNumber = null
                    } else {
                        tvNfcStatus.text = "Karta jest już używana w innej placówce!"
                        tvNfcStatus.setTextColor(android.graphics.Color.RED)
                    }
                }
            }
        })
    }
}