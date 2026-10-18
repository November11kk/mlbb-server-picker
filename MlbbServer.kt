package com.example.mlbbserverpicker

/**
 * Класс, описывающий один сервер MLBB (имя, IP для пинга и список подсетей)
 */
data class MlbbServer(
    val id: String,
    val name: String,
    val testIp: String,
    val subnets: List<String>,
    var pingMs: Long = -1
)
