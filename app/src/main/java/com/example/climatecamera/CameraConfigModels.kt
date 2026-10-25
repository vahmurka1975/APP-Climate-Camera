package com.example.climatecamera

internal data class ConfigField(val key: String, val label: String, val min: Double, val max: Double, val integer: Boolean = false) {
    fun normalized(text: String): String? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < min || value > max || (integer && value % 1.0 != 0.0)) return null
        return if (integer) value.toInt().toString() else value.toString()
    }
}
internal object CameraConfigFields {
    val all = listOf(
        ConfigField("tempTarget", "Температура, °C", 2.0, 30.0),
        ConfigField("tempHyst", "Гистерезис температуры, °C", 0.2, 5.0),
        ConfigField("humTarget", "Влажность, %", 30.0, 98.0),
        ConfigField("humHyst", "Гистерезис влажности, %", 1.0, 10.0),
        ConfigField("fanWorkMin", "Работа вентилятора, мин", 1.0, 180.0, true),
        ConfigField("fanPauseMin", "Пауза вентилятора, мин (0 — непрерывно)", 0.0, 360.0, true),
        ConfigField("fanTargetRPM", "Обороты вентилятора, об/мин", 300.0, 1500.0, true),
        ConfigField("tempOffset", "Поправка температуры, °C", -5.0, 5.0),
        ConfigField("humOffset", "Поправка влажности, %", -15.0, 15.0),
        ConfigField("pidKp", "ПИ-регулятор: Kp", 0.0, 1.0),
        ConfigField("pidKi", "ПИ-регулятор: Ki", 0.0, 1.0)
    )
}
