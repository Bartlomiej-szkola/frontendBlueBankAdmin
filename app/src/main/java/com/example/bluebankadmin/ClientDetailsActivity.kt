package com.example.bluebankadmin

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
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
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            elevation = 4f
        }

        // Pierwszy wiersz: UID karty i włącznik
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvCardUid = TextView(this).apply {
            text = "Karta: $cardUid"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val switchActive = Switch(this).apply {
            isChecked = isActive
            text = if (isActive) "Aktywna" else "Zablokowana"
            setOnCheckedChangeListener { _, isChecked ->
                text = if (isChecked) "Aktywna" else "Zablokowana"
                changeCardStatus(cardUid, isChecked)
            }
        }

        topRow.addView(tvCardUid)
        topRow.addView(switchActive)

        // Drugi wiersz: Przycisk resetu/zmiany PINu
        val btnChangePin = Button(this).apply {
            text = "Zresetuj/Zmień PIN karty"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END
                topMargin = 16
            }
            setOnClickListener {
                showChangePinDialog(cardUid)
            }
        }

        cardLayout.addView(topRow)
        cardLayout.addView(btnChangePin)
        layoutCardsContainer.addView(cardLayout)
    }

    // Okienko do ZMIANY PINU (Resetu)
    private fun showChangePinDialog(cardUid: String) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "Nowe 4 cyfry PIN"
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Zmiana PIN-u karty")
            .setMessage("Wpisz nowy 4-cyfrowy PIN dla karty: $cardUid")
            .setView(input)
            .setPositiveButton("ZMIEŃ") { _, _ ->
                val newPin = input.text.toString()
                if (newPin.length == 4) {
                    updateCardPinOnServer(cardUid, newPin)
                } else {
                    Toast.makeText(this, "PIN musi mieć 4 cyfry!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ANULUJ", null)
            .show()
    }

    private fun updateCardPinOnServer(cardUid: String, newPin: String) {
        val request = okhttp3.Request.Builder()
            .url("${AppConfig.ADMIN_API}/card/$cardUid/pin?newPin=$newPin")
            .patch(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ClientDetailsActivity, "PIN został zmieniony!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ClientDetailsActivity, "Błąd zmiany PIN-u!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
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

        runOnUiThread {
            checkIfCardIsFreeAndShowPin(accountNumber, cardUid)
        }
    }

    private fun checkIfCardIsFreeAndShowPin(accountNumber: String, cardUid: String) {
        // Odpytujemy serwer czy ktoś już ma tę kartę
        val request = okhttp3.Request.Builder()
            .url("${AppConfig.ADMIN_API}/card/$cardUid/client")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread { Toast.makeText(this@ClientDetailsActivity, "Błąd sieci", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        // Kod 200 OK - serwer znalazł klienta do tej karty -> zajęta!
                        Toast.makeText(this@ClientDetailsActivity, "BŁĄD: Karta $cardUid jest już zajęta przez innego klienta!", Toast.LENGTH_LONG).show()
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
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            hint = "Wpisz 4 cyfry"
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            textSize = 24f
            setPadding(0, 40, 0, 40)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Dodawanie kolejnej karty")
            .setMessage("Nadaj PIN dla nowej karty NFC:\n$cardUid")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("ZAPISZ") { _, _ ->
                val pin = input.text.toString()
                if (pin.length == 4) {
                    linkCardToAccount(accountNum, cardUid, pin)
                } else {
                    Toast.makeText(this, "PIN musi mieć 4 cyfry! Zbliż kartę ponownie.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("ANULUJ", null)
            .show()
    }

    private fun linkCardToAccount(accountNum: String, cardUid: String, pin: String) {
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
                        Toast.makeText(this@ClientDetailsActivity, "DODANO KARTĘ: $cardUid", Toast.LENGTH_SHORT).show()
                        loadClientData() // Odświeżamy listę kart na ekranie (widzimy nową kartę z suwakiem!)
                    } else {
                        Toast.makeText(this@ClientDetailsActivity, "Błąd: Karta jest już przypisana!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}