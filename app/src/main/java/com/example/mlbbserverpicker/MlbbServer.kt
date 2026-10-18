package com.example.mlbbserverpicker

/**
 * Модель отдельного сервера
 * @property ips Список проверочных IP-адресов узла (основной и резервные)
 */
data class ServerNode(
    val id: String,
    val name: String,
    val ips: List<String>,
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
