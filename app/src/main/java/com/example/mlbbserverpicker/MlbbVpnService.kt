package com.example.mlbbserverpicker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import kotlin.concurrent.thread

class MlbbVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        val blockedSubnets = intent?.getStringArrayListExtra("BLOCKED_SUBNETS") ?: arrayListOf()
        startVpn(blockedSubnets)

        return START_STICKY
    }

    private fun startVpn(blockedSubnets: List<String>) {
        stopVpn()

        try {
            val builder = Builder()
                .setSession("MLBB Server Blocker")
                .addAddress("10.0.0.2", 32)

            for (subnet in blockedSubnets) {
                val parts = subnet.split("/")
                if (parts.size == 2) {
                    builder.addRoute(parts[0], parts[1].toInt())
                }
            }

            vpnInterface = builder.establish()
            isRunning = true

            thread {
                val input = FileInputStream(vpnInterface?.fileDescriptor)
                val buffer = ByteArray(32768)
                try {
                    while (isRunning) {
                        input.read(buffer) // "Сбрасываем" пакеты заблокированных серверов
                    }
                } catch (e: Exception) {
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
