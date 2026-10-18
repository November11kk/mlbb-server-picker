package com.example.mlbbserverpicker

/**
 * Модель отдельного сервера
 */
data class ServerNode(
    val id: String,
    val name: String,
    val ip: String,
    val subnets: List<String>,
    var pingMs: Long = -1
)

/**
 * Модель страны с группой серверов
 */
data class CountryRegion(
    val id: String,
    val countryName: String,
    val flagEmoji: String,
    val servers: List<ServerNode>,
    var isExpanded: Boolean = false
)
