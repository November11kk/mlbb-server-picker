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
        stopVpnSilently() // Безопасный сброс предыдущей сессии

        try {
            val builder = Builder()
                .setSession("MLBB Server Picker")
                .setMtu(1500)

            // Локальный адрес виртуального туннеля
            builder.addAddress("10.0.0.2", 32)

            // Направляем подсети для блокировки во внутренний тупик
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

            // Создаем VPN-интерфейс
            vpnInterface = builder.establish()

            val pfd = vpnInterface
            // Защита от NullPointerException (ошибка fdObj == null)
            if (pfd == null) {
                return
            }

            isRunning = true

            // Фоновый поток для поглощения трафика заблокированных регионов
            workerThread = thread(start = true) {
                try {
                    val inputStream = FileInputStream(pfd.fileDescriptor)
                    val buffer = ByteArray(32768)
                    while (isRunning) {
                        val length = inputStream.read(buffer)
                        if (length <= 0) {
                            Thread.sleep(100)
                        }
                    }
                } catch (e: Exception) {
                    // Поток завершает работу при остановке службы
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
