package com.example.mlbbserverpicker

/**
 * Модель данных сервера MLBB
 * @property id Уникальный идентификатор сервера
 * @property name Отображаемое название региона
 * @property testIps Список проверочных IP-адресов (основной и резервные)
 * @property subnets Список подсетей для блокировки
 * @property pingMs Измеренная задержка в миллисекундах (-1, если недоступен)
 */
data class MlbbServer(
    val id: String,
    val name: String,
    val testIps: List<String>,
    val subnets: List<String>,
    var pingMs: Long = -1
)
