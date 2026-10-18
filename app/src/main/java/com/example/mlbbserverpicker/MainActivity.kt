package com.example.mlbbserverpicker

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {

    // Расширенный список серверов с несколькими проверочными IP на каждый регион
    private val servers = listOf(
        MlbbServer(
            "ukraine", 
            "Украина / В. Европа", 
            listOf("185.180.222.1", "185.180.222.2", "185.180.223.1"), 
            listOf("185.180.222.0/24", "185.180.223.0/24")
        ),
        MlbbServer(
            "eu_central", 
            "Украина - Словакия - Италия", 
            listOf("161.117.59.1", "161.117.58.1", "161.117.60.1"), 
            listOf("161.117.59.0/24", "161.117.58.0/24", "161.117.60.0/24")
        ),
        MlbbServer(
            "sea", 
            "Азия (SEA)", 
            listOf("47.74.0.1", "47.74.1.1", "47.74.2.1"), 
            listOf("47.74.0.0/16")
        ),
        MlbbServer(
            "na", 
            "Северная Америка (NA)", 
            listOf("47.89.0.1", "47.89.1.1", "47.89.2.1"), 
            listOf("47.89.0.0/16")
        )
    )

    private var selectedServerId: String? = null
    private val VPN_REQUEST_CODE = 1001

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        // Обработчики нажатий кнопок
        findViewById<Button>(R.id.btnCheckPing).setOnClickListener { checkAllPings() }
        findViewById<Button>(R.id.btnUkraine).setOnClickListener { selectServer("ukraine") }
        findViewById<Button>(R.id.btnEuCentral).setOnClickListener { selectServer("eu_central") }
        findViewById<Button>(R.id.btnReset).setOnClickListener { stopServerLock() }

        // Замер пинга при старте
        checkAllPings()
    }

    /**
     * Запуск проверки задержки для каждого сервера
     */
    private fun checkAllPings() {
        tvStatus.text = "Измерение пинга..."
        GlobalScope.launch(Dispatchers.IO) {
            val results = StringBuilder()
            for (server in servers) {
                val ping = measureServerPing(server.testIps)
                server.pingMs = ping
                val pingText = if (ping >= 0) "$ping мс" else "Недоступен"
                results.append("${server.name}: $pingText\n")
            }
            withContext(Dispatchers.Main) {
                tvStatus.text = results.toString()
            }
        }
    }

    /**
     * Проверка списка IP-адресов региона до первого успешного ответа
     */
    private fun measureServerPing(ips: List<String>): Long {
        for (ip in ips) {
            val ping = pingSingleIp(ip)
            if (ping >= 0) return ping
        }
        return -1L
    }

    /**
     * Двухуровневая проверка конкретного IP:
     * 1. ICMP ping
     * 2. Резервные TCP сокеты (порты 443 и 80)
     */
    private fun pingSingleIp(ip: String): Long {
        // Способ 1: ICMP Ping через системную консоль
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
            // Переход к TCP резерву при ошибке утилиты
        }

        // Способ 2: Резервная проверка через TCP-сокет на портах 443 и 80
        val portsToTry = listOf(443, 80)
        for (port in portsToTry) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 1200)
                socket.close()
                return System.currentTimeMillis() - startTime
            } catch (e: Exception) {
                // Пробуем следующий порт
            }
        }

        return -1L
    }

    /**
     * Включение блокировки невыбранных регионов
     */
    private fun selectServer(serverId: String) {
        selectedServerId = serverId
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
            val targetServer = servers.find { it.id == selectedServerId } ?: return
            
            val blockedSubnets = ArrayList<String>()
            for (server in servers) {
                if (server.id != selectedServerId) {
                    blockedSubnets.addAll(server.subnets)
                }
            }

            val serviceIntent = Intent(this, MlbbVpnService::class.java).apply {
                putStringArrayListExtra("BLOCKED_SUBNETS", blockedSubnets)
            }
            startService(serviceIntent)
            
            tvStatus.text = "Зафиксирован сервер: ${targetServer.name}\nОстальные регионы заблокированы!"
        }
    }

    /**
     * Сброс всех фильтров
     */
    private fun stopServerLock() {
        val intent = Intent(this, MlbbVpnService::class.java).apply {
            action = "ACTION_STOP"
        }
        startService(intent)
        tvStatus.text = "Блокировки сброшены."
    }
}
