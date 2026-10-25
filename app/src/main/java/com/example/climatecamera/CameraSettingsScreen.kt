package com.example.climatecamera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CameraSettingsScreen(connection: ConnectionSettings?) {
    if (connection == null) { Text("Подключитесь к камере для просмотра настроек."); return }
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var baseline by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var readOnly by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    var saveNvs by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Map<String, String>?>(null) }
    var confirmRefresh by remember { mutableStateOf(false) }
    suspend fun refresh(): Map<String, String> {
        verified = false
        val status = withContext(Dispatchers.IO) { CameraTransport.status(connection) }
        readOnly = status.optBoolean("readOnly", true)
        val config = status.getJSONObject("config")
        val loaded = CameraConfigFields.all.associate { field ->
            val number = config.getDouble(field.key)
            check(number.isFinite()) { "Некорректный ответ ESP32" }
            field.key to (if (field.integer) number.toInt().toString() else number.toString())
        }
        values = loaded; baseline = loaded
        verified = !status.optBoolean("otaMode") && !status.optBoolean("uiEditing")
        return loaded
    }
    fun requestRefresh() {
        busy = true
        scope.launch {
            try { refresh(); message = "Настройки перечитаны с ESP32" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = ConnectionPolicy.errorMessage(e) }
            finally { busy = false }
        }
    }
    LaunchedEffect(connection) {
        busy = true
        try { refresh() } catch (e: CancellationException) { throw e } catch (e: Exception) { message = ConnectionPolicy.errorMessage(e) } finally { busy = false }
    }
    val normalized = CameraConfigFields.all.associate { it.key to it.normalized(values[it.key].orEmpty()) }
    val changed = normalized.filter { (key, value) -> value != null && value.toDoubleOrNull() != baseline[key]?.toDoubleOrNull() }
        .mapValues { it.value!! }
    val editable = ReadbackPolicy.canEdit(verified, readOnly, busy)
    Text("Настройки камеры", style = MaterialTheme.typography.titleLarge)
    Text(if (readOnly) "Режим просмотра. Для редактирования подключитесь с правами администратора." else "Администратор · права подтверждены ESP32")
    if (!verified) Text("Изменение заблокировано. Обновите форму; завершите OTA или локальное редактирование, если они активны.")
    Button(enabled = !busy, onClick = {
        if (values != baseline) confirmRefresh = true else requestRefresh()
    }) { Text("Обновить форму") }
    if (confirmRefresh) {
        AlertDialog(onDismissRequest = { confirmRefresh = false },
            title = { Text("Сбросить несохранённые изменения?") },
            text = { Text("Поля формы будут заменены текущими значениями ESP32. Команда записи не отправляется.") },
            dismissButton = { TextButton(onClick = { confirmRefresh = false }) { Text("Отмена") } },
            confirmButton = { TextButton(onClick = { confirmRefresh = false; requestRefresh() }) { Text("Перечитать") } })
    }
    CameraConfigFields.all.forEach { field ->
        val value = values[field.key].orEmpty()
        val invalid = values.isNotEmpty() && field.normalized(value) == null
        OutlinedTextField(value = value, onValueChange = { values = values + (field.key to it) },
            label = { Text(field.label) }, enabled = editable, isError = invalid,
            supportingText = { Text("${field.min} … ${field.max}" + if (field.integer) "; целое число" else "; точка или запятая") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth())
    }
    Row { Checkbox(checked = saveNvs, onCheckedChange = { saveNvs = it }, enabled = editable); Text("Сохранить в память ESP32 (NVS)") }
    Button(enabled = editable && normalized.values.none { it == null } && changed.isNotEmpty(), onClick = { pending = changed.toMap() }) {
        Text(if (busy) "Выполняется…" else "Применить настройки")
    }
    if (message.isNotBlank()) Text(message)
    pending?.let { fields ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Подтвердить изменение?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CameraConfigFields.all.filter { it.key in fields }.forEach { Text("${it.label}: ${baseline[it.key]} → ${fields[it.key]}") }
                Text(if (saveNvs) "С сохранением в NVS" else "Без сохранения в NVS; после перезапуска значения могут сброситься")
            } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } },
            confirmButton = { TextButton(onClick = {
                pending = null; busy = true; verified = false
                scope.launch {
                    try {
                        val reply = withContext(Dispatchers.IO) { CameraTransport.saveConfig(connection, fields, baseline, saveNvs) }
                        // Exactly one POST above. Only GET readback may be retried below.
                        var matched = false
                        var lastDifferences = emptyList<String>()
                        var lastReadError: String? = null
                        for (attempt in 1..ReadbackPolicy.MAX_ATTEMPTS) {
                            message = "$reply. Проверка значений: $attempt/${ReadbackPolicy.MAX_ATTEMPTS}…"
                            try {
                                val actual = refresh()
                                lastDifferences = ReadbackPolicy.differences(fields, actual).map { key ->
                                    val label = CameraConfigFields.all.first { it.key == key }.label
                                    "$label: запрошено ${fields[key]}, получено ${actual[key] ?: "нет значения"}"
                                }
                                lastReadError = null
                                if (lastDifferences.isEmpty()) { matched = true; break }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { lastReadError = ConnectionPolicy.errorMessage(e) }
                            if (attempt < ReadbackPolicy.MAX_ATTEMPTS) delay(ReadbackPolicy.RETRY_DELAY_MS)
                        }
                        if (matched) {
                            message = "$reply. Отправленные значения подтверждены чтением ESP32. " +
                                if (saveNvs) "Сохранение в NVS проверяется отдельно после перезапуска ESP32." else "Запрошено без сохранения в NVS."
                        } else {
                            verified = false
                            message = "$reply. Результат записи НЕ подтверждён. " +
                                (lastReadError ?: lastDifferences.joinToString("; ")) +
                                ". POST не повторялся. Перечитайте форму перед дальнейшим изменением."
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        verified = false
                        message = "${ConnectionPolicy.errorMessage(e)}. Если запрос был отправлен, изменение могло выполниться. Автоматического повтора нет: перечитайте форму и проверьте значения."
                    } finally { busy = false }
                }
            }) { Text("Подтвердить") } })
    }
}
