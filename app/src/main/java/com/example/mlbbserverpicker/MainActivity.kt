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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {

    // Иерархический список стран с несколькими проверочными IP для каждого сервера
    private val countries = listOf(
        CountryRegion(
            id = "ukraine",
            countryName = "Украина",
            flagEmoji = "🇺🇦",
            servers = listOf(
                ServerNode("ua_1", "Сервер Киев #1", listOf("185.180.222.1", "185.180.222.2"), listOf("185.180.222.0/24")),
                ServerNode("ua_2", "Сервер Харьков #2", listOf("185.180.223.1", "185.180.223.2"), listOf("185.180.223.0/24"))
            )
        ),
        CountryRegion(
            id = "slovakia",
            countryName = "Словакия",
            flagEmoji = "🇸🇰",
            servers = listOf(
                ServerNode(
                    "sk_1", 
                    "Сервер Братислава #1", 
                    listOf("161.117.225.1", "161.117.224.1", "8.209.0.1", "185.230.223.1"), 
                    listOf("161.117.225.0/24", "161.117.224.0/24", "8.209.0.0/16", "185.230.223.0/24")
                )
            )
        ),
        CountryRegion(
            id = "italy",
            countryName = "Италия",
            flagEmoji = "🇮🇹",
            servers = listOf(
                ServerNode(
                    "it_1", 
                    "Сервер Милан #1", 
                    listOf("161.117.59.1", "161.117.58.1", "47.241.0.1", "8.209.1.1"), 
                    listOf("161.117.59.0/24", "161.117.58.0/24", "47.241.0.0/16")
                )
            )
        ),
        CountryRegion(
            id = "sea",
            countryName = "Азия (SEA)",
            flagEmoji = "🌏",
            servers = listOf(
                ServerNode(
                    "sea_1", 
                    "Сервер Сингапур #1", 
                    listOf("47.74.0.1", "47.74.1.1", "47.74.2.1"), 
                    listOf("47.74.0.0/16")
                )
            )
        ),
        CountryRegion(
            id = "na",
            countryName = "Северная Америка",
            flagEmoji = "🇺🇸",
            servers = listOf(
                ServerNode(
                    "na_1", 
                    "Сервер США #1", 
                    listOf("47.89.0.1", "47.89.1.1", "149.28.0.1", "104.238.168.1"), 
                    listOf("47.89.0.0/16", "149.28.0.0/16")
                )
            )
        )
    )

    private var selectedSubnetsToKeep = ArrayList<String>()
    private val VPN_REQUEST_CODE = 1001

    private lateinit var tvStatus: TextView
    private lateinit var llCountriesContainer: LinearLayout
    private var isPinging = false

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
     * Построение списка стран в интерфейсе
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
                    val pingText = when {
                        isPinging -> "Замер..."
                        server.pingMs >= 0 -> "${server.pingMs} мс"
                        else -> "Недоступен"
                    }
                    text = "• ${server.name}\n  Пинг: $pingText"
                    setTextColor(
                        when {
                            isPinging -> Color.YELLOW
                            server.pingMs >= 0 -> Color.LTGRAY
                            else -> Color.parseColor("#FF6B6B")
                        }
                    )
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
     * Параллельное измерение пинга
     */
    private fun checkAllPings() {
        if (isPinging) return
        isPinging = true
        tvStatus.text = "Измерение пинга..."
        renderCountryList()

        GlobalScope.launch(Dispatchers.IO) {
            val allServers = countries.flatMap { it.servers }

            val tasks = allServers.map { server ->
                async {
                    server.pingMs = pingServerNode(server.ips)
                }
            }

            tasks.awaitAll()

            isPinging = false
            withContext(Dispatchers.Main) {
                renderCountryList()
                tvStatus.text = "Пинг обновлён!"
            }
        }
    }

    /**
     * Проверка списка IP-адресов сервера до первого успешного отклика
     */
    private fun pingServerNode(ips: List<String>): Long {
        for (ip in ips) {
            val ping = pingSingleIp(ip)
            if (ping >= 0) return ping
        }
        return -1L
    }

    /**
     * Комплексный замер задержки до единичного IP
     */
    private fun pingSingleIp(ip: String): Long {
        // 1. Попытка через системный ICMP Ping
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
            // Переход к TCP
        }

        // 2. Попытка через TCP-сокеты (порты 443, 80, 30130)
        val portsToTry = listOf(443, 80, 30130)
        for (port in portsToTry) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 2000)
                socket.close()
                return System.currentTimeMillis() - startTime
            } catch (e: Exception) {
                // Игнорируем и пробуем следующий порт
            }
        }
        return -1L
    }

    /**
     * Активация локального VPN
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
