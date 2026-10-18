package com.example.mlbbserverpicker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import kotlin.concurrent.thread

class MlbbVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var workerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        val blockedSubnets = intent?.getStringArrayListExtra("BLOCKED_SUBNETS")
        if (!blockedSubnets.isNullOrEmpty()) {
            startVpn(blockedSubnets)
        }

        return START_STICKY
    }

    private fun startVpn(blockedSubnets: List<String>) {
        stopVpnSilently()

        try {
            val builder = Builder()
                .setSession("MLBB Server Picker")
                .setMtu(1500)
                // Локальные адреса виртуального туннеля (IPv4 и IPv6)
                .addAddress("10.0.0.2", 32)
                .addAddress("fd00::1", 128)
                // Публичные DNS для перехвата обходных запросов
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            var validRoutesCount = 0
            for (subnet in blockedSubnets) {
                try {
                    val parts = subnet.split("/")
                    if (parts.size == 2) {
                        val ip = parts[0]
                        val prefix = parts[1].toInt()
                        builder.addRoute(ip, prefix)
                        validRoutesCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (validRoutesCount == 0) return

            // Включаем блокирующий режим туннеля
            builder.setBlocking(true)
            vpnInterface = builder.establish()
            val pfd = vpnInterface ?: return

            isRunning = true

            // Фоновый поток для сброса пакетов заблокированных регионов
            workerThread = thread(start = true) {
                try {
                    val inputStream = FileInputStream(pfd.fileDescriptor)
                    val buffer = ByteArray(32768)
                    while (isRunning) {
                        val length = inputStream.read(buffer)
                        if (length <= 0) {
                            Thread.sleep(50)
                        }
                    }
                } catch (e: Exception) {
                    // Поток закрывается при остановке сервиса
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            stopVpnSilently()
        }
    }

    private fun stopVpnSilently() {
        isRunning = false
        try {
            workerThread?.interrupt()
            workerThread = null
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() {
        stopVpnSilently()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpnSilently()
        super.onDestroy()
    }
}
