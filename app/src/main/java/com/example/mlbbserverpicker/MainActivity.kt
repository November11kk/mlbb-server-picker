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

    // Сервера: Украина и маршрут Украина - Словакия - Италия
    private val servers = listOf(
        MlbbServer("ukraine", "Украина / В. Европа", "185.180.222.1", listOf("185.180.222.0/24")),
        MlbbServer("eu_central", "Украина - Словакия - Италия", "161.117.59.1", listOf("161.117.59.0/24")),
        MlbbServer("sea", "Азия (SEA)", "47.74.0.1", listOf("47.74.0.0/16")),
        MlbbServer("na", "Северная Америка (NA)", "47.89.0.1", listOf("47.89.0.0/16"))
    )

    private var selectedServerId: String? = null
    private val VPN_REQUEST_CODE = 1001

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnCheckPing).setOnClickListener { checkAllPings() }
        findViewById<Button>(R.id.btnUkraine).setOnClickListener { selectServer("ukraine") }
        findViewById<Button>(R.id.btnEuCentral).setOnClickListener { selectServer("eu_central") }
        findViewById<Button>(R.id.btnReset).setOnClickListener { stopServerLock() }

        checkAllPings()
    }

    private fun checkAllPings() {
        tvStatus.text = "Измерение пинга..."
        GlobalScope.launch(Dispatchers.IO) {
            val results = StringBuilder()
            for (server in servers) {
                val ping = measurePing(server.testIp)
                server.pingMs = ping
                val pingText = if (ping >= 0) "$ping мс" else "Недоступен"
                results.append("${server.name}: $pingText\n")
            }
            withContext(Dispatchers.Main) {
                tvStatus.text = results.toString()
            }
        }
    }

    private fun measurePing(ip: String, port: Int = 443, timeoutMs: Int = 1500): Long {
        return try {
            val startTime = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.close()
            System.currentTimeMillis() - startTime
        } catch (e: Exception) {
            -1L
        }
    }

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
            
            tvStatus.text = " Зафиксирован сервер: ${targetServer.name}\nОстальные заблокированы!"
        }
    }

    private fun stopServerLock() {
        val intent = Intent(this, MlbbVpnService::class.java).apply {
            action = "ACTION_STOP"
        }
        startService(intent)
        tvStatus.text = " Блокировки сброшены."
    }
}
