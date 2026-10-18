package com.example.mlbbserverpicker

import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {

    // Иерархический список: Страны -> Отдельные сервера
    private val countries = listOf(
        CountryRegion(
            id = "ukraine",
            countryName = "Украина",
            flagEmoji = "🇺🇦",
            servers = listOf(
                ServerNode("ua_1", "Сервер Киев #1", "185.180.222.1", listOf("185.180.222.0/24")),
                ServerNode("ua_2", "Сервер Харьков #2", "185.180.223.1", listOf("185.180.223.0/24"))
            )
        ),
        CountryRegion(
            id = "slovakia",
            countryName = "Словакия",
            flagEmoji = "🇸🇰",
            servers = listOf(
                ServerNode("sk_1", "Сервер Братислава #1", "161.117.225.1", listOf("161.117.225.0/24"))
            )
        ),
        CountryRegion(
            id = "italy",
            countryName = "Италия",
            flagEmoji = "🇮🇹",
            servers = listOf(
                ServerNode("it_1", "Сервер Милан #1", "161.117.59.1", listOf("161.117.59.0/24"))
            )
        ),
        CountryRegion(
            id = "sea",
            countryName = "Азия (SEA)",
            flagEmoji = "🌏",
            servers = listOf(
                ServerNode("sea_1", "Сервер Сингапур #1", "47.74.0.1", listOf("47.74.0.0/16"))
            )
        ),
        CountryRegion(
            id = "na",
            countryName = "Северная Америка",
            flagEmoji = "🇺🇸",
            servers = listOf(
                ServerNode("na_1", "Сервер США #1", "47.89.0.1", listOf("47.89.0.0/16"))
            )
        )
    )

    private var selectedSubnetsToKeep = ArrayList<String>()
    private val VPN_REQUEST_CODE = 1001

    private lateinit var tvStatus: TextView
    private lateinit var llCountriesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        llCountriesContainer = findViewById(R.id.llCountriesContainer)

        findViewById<Button>(R.id.btnCheckPing).setOnClickListener { checkAllPings() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { stopServerLock() }

        renderCountryList()
        checkAllPings()
    }

    /**
     * Динамическое построение списка стран и их серверов
     */
    private fun renderCountryList() {
        llCountriesContainer.removeAllViews()

        for (country in countries) {
            val countryHeader = Button(this).apply {
                text = "${country.flagEmoji} ${country.countryName} ${if (country.isExpanded) "▲" else "▼"}"
                setBackgroundColor(Color.parseColor("#252525"))
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(24, 24, 24, 24)
            }

            val serversLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (country.isExpanded) View.VISIBLE else View.GONE
                setPadding(16, 8, 16, 24)
            }

            countryHeader.setOnClickListener {
                country.isExpanded = !country.isExpanded
                serversLayout.visibility = if (country.isExpanded) View.VISIBLE else View.GONE
                countryHeader.text = "${country.flagEmoji} ${country.countryName} ${if (country.isExpanded) "▲" else "▼"}"
            }

            for (server in country.servers) {
                val serverRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(12, 12, 12, 12)
                }

                val tvServerInfo = TextView(this).apply {
                    val pingStr = if (server.pingMs >= 0) "${server.pingMs} мс" else "Замер..."
                    text = "• ${server.name}\n  Пинг: $pingStr"
                    setTextColor(Color.LTGRAY)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnSelectServer = Button(this).apply {
                    text = "Выбрать"
                    setBackgroundColor(Color.parseColor("#1E88E5"))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        activateVpnForSubnets(server.subnets)
                    }
                }

                serverRow.addView(tvServerInfo)
                serverRow.addView(btnSelectServer)
                serversLayout.addView(serverRow)
            }

            val btnSelectAllCountry = Button(this).apply {
                text = "Фиксировать всю страну: ${country.countryName}"
                setBackgroundColor(Color.parseColor("#388E3C"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    val countrySubnets = country.servers.flatMap { it.subnets }
                    activateVpnForSubnets(countrySubnets)
                }
            }
            serversLayout.addView(btnSelectAllCountry)

            llCountriesContainer.addView(countryHeader)
            llCountriesContainer.addView(serversLayout)
        }
    }

    /**
     * Поочередное измерение пинга для всех узлов
     */
    private fun checkAllPings() {
        tvStatus.text = "Измерение пинга..."
        GlobalScope.launch(Dispatchers.IO) {
            for (country in countries) {
                for (server in country.servers) {
                    server.pingMs = pingSingleIp(server.ip)
                }
            }
            withContext(Dispatchers.Main) {
                renderCountryList()
                tvStatus.text = "Пинг обновлён!"
            }
        }
    }

    /**
     * Замер задержки до IP-адреса
     */
    private fun pingSingleIp(ip: String): Long {
        try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 2 $ip")
            val reader = process.inputStream.bufferedReader()
            for (line in reader.lineSequence()) {
                if (line.contains("time=")) {
                    val timeStr = line.substringAfter("time=").substringBefore(" ms").trim()
                    val pingValue = timeStr.toFloat().toLong()
                    process.destroy()
                    return pingValue
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            // Резервный вызов
        }

        val portsToTry = listOf(443, 80, 30130)
        for (port in portsToTry) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 1200)
                socket.close()
                return System.currentTimeMillis() - startTime
            } catch (e: Exception) {
                // Игнорируем и пробуем следующий порт
            }
        }
        return -1L
    }

    /**
     * Формирование списка заблокированных подсетей
     */
    private fun activateVpnForSubnets(allowedSubnets: List<String>) {
        val allSubnets = countries.flatMap { c -> c.servers.flatMap { s -> s.subnets } }
        val subnetsToBlock = ArrayList(allSubnets.filter { !allowedSubnets.contains(it) })

        selectedSubnetsToKeep = subnetsToBlock

        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, MlbbVpnService::class.java).apply {
                putStringArrayListExtra("BLOCKED_SUBNETS", selectedSubnetsToKeep)
            }
            startService(serviceIntent)
            tvStatus.text = "VPN активен! Выбранный регион зафиксирован."
        }
    }

    /**
     * Отключение VPN
     */
    private fun stopServerLock() {
        val intent = Intent(this, MlbbVpnService::class.java).apply {
            action = "ACTION_STOP"
        }
        startService(intent)
        tvStatus.text = "Блокировки сброшены."
    }
}
