package com.example.bluebankadmin

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AddClientActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private val client = OkHttpClient()
    private var nfcAdapter: NfcAdapter? = null
    private var newAccountNumber: String? = null

    private lateinit var layoutResult: LinearLayout
    private lateinit var tvCredentials: TextView
    private lateinit var tvNfcStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etSurname = findViewById<TextInputEditText>(R.id.etSurname)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val btnCreate = findViewById<Button>(R.id.btnCreate)

        layoutResult = findViewById(R.id.layoutResult)
        tvCredentials = findViewById(R.id.tvCredentials)
        tvNfcStatus = findViewById(R.id.tvNfcStatus)

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val surname = etSurname.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isEmpty() || surname.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Wypełnij dane", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createClient(name, surname, phone)
        }
    }

    private fun createClient(name: String, surname: String, phone: String) {
        val json = JSONObject().apply {
            put("ownerName", name)
            put("ownerSurname", surname)
            put("phoneNumber", phone)
        }.toString()

        val request = Request.Builder()
            .url("${AppConfig.ADMIN_API}/account")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@AddClientActivity, "Błąd sieci", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                runOnUiThread {
                    if (response.code == 409) {
                        // OBSŁUGA DUPLIKATU NUMERU
                        Toast.makeText(this@AddClientActivity, "Ten numer telefonu jest już w bazie!", Toast.LENGTH_LONG).show()
                    } else if (response.isSuccessful) {
                        val jsonResp = JSONObject(bodyStr)
                        newAccountNumber = jsonResp.getString("accountNumber")
                        val pin = jsonResp.getString("mobileAppPin")

                        layoutResult.visibility = View.VISIBLE
                        tvCredentials.text = "Nr Konta: $newAccountNumber\nPIN Startowy: $pin"
                        Toast.makeText(this@AddClientActivity, "Użytkownik utworzony!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // --- OBSŁUGA NFC (Wiele kart pod rząd) ---
    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        if (newAccountNumber == null) return

        val cardUid = tag.id.joinToString("") { "%02X".format(it) }
        runOnUiThread {
            tvNfcStatus.text = "Odczytano kartę: $cardUid. Ustalanie PIN-u..."
            checkIfCardIsFreeAndShowPin(newAccountNumber!!, cardUid)
        }
    }

    private fun checkIfCardIsFreeAndShowPin(accountNumber: String, cardUid: String) {
        // Odpytujemy serwer czy ktoś już ma tę kartę
        val request = okhttp3.Request.Builder()
            .url("${AppConfig.ADMIN_API}/card/$cardUid/client")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread { Toast.makeText(this@AddClientActivity, "Błąd sieci", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        // Kod 200 OK - serwer znalazł klienta do tej karty -> zajęta!
                        tvNfcStatus.text = "BŁĄD: Karta $cardUid jest już zajęta przez innego klienta!"
                        tvNfcStatus.setTextColor(android.graphics.Color.RED)
                    } else {
                        // Kod 404 NOT FOUND - nikt jej nie ma, jest wolna!
                        showPinDialog(accountNumber, cardUid)
                    }
                }
            }
        })
    }

    private fun showPinDialog(accountNum: String, cardUid: String) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(4)) // Maks 4 cyfry
            hint = "Wpisz 4-cyfrowy PIN"
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            textSize = 24f
            setPadding(0, 40, 0, 40)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Wydawanie nowej karty")
            .setMessage("Karta NFC: $cardUid\nNadaj 4-cyfrowy PIN dla tej karty:")
            .setView(input)
            .setCancelable(false) // Nie można kliknąć obok
            .setPositiveButton("ZAPISZ") { _, _ ->
                val pin = input.text.toString()
                if (pin.length == 4) {
                    linkCardToAccount(accountNum, cardUid, pin)
                } else {
                    Toast.makeText(this, "PIN musi mieć dokładnie 4 cyfry!", Toast.LENGTH_LONG).show()
                    tvNfcStatus.text = "Błąd: Zły PIN. Zbliż kartę ponownie."
                }
            }
            .setNegativeButton("ANULUJ") { dialog, _ ->
                dialog.dismiss()
                tvNfcStatus.text = "Anulowano. Zbliż kartę ponownie, aby ponowić."
            }
            .show()
    }

    private fun linkCardToAccount(accountNum: String, cardUid: String, pin: String) {
        tvNfcStatus.text = "Zapisywanie w banku..."

        // Dodaliśmy parametr &pin=$pin do URL!
        val url = "${AppConfig.BANK_API}/card?accountNumber=$accountNum&cardUid=$cardUid&pin=$pin"

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody())
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        tvNfcStatus.text = "KARTA $cardUid DODANA!\nMożesz zbliżyć kolejną kartę."
                        tvNfcStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    } else {
                        tvNfcStatus.text = "Błąd: Karta jest już używana!"
                        tvNfcStatus.setTextColor(android.graphics.Color.RED)
                    }
                }
            }
        })
    }
}