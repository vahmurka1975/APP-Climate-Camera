package com.example.climatecamera

import android.app.DatePickerDialog
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

private fun grams(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

@Composable
internal fun ProductsScreen(connection: ConnectionSettings?) {
    if (connection == null) {
        Text("Подключитесь к камере для просмотра продуктов.")
        return
    }
    val settings = connection
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<ProductsSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Product?>(null) }
    var history by remember { mutableStateOf<Product?>(null) }
    var remove by remember { mutableStateOf<Product?>(null) }
    var exportText by remember { mutableStateOf("") }
    var refreshedAt by remember { mutableStateOf("") }
    var receivedElapsed by remember { mutableStateOf<Long?>(null) }
    var elapsedNow by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var refreshFailed by remember { mutableStateOf(false) }
    var needsVerification by remember { mutableStateOf(false) }
    LaunchedEffect(settings) {
        while (true) {
            elapsedNow = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val staleProducts = refreshFailed || FreshnessPolicy.stale(
        receivedElapsed, elapsedNow, FreshnessPolicy.PRODUCTS_MAX_AGE_MS
    )
    val writeBlocked = busy || staleProducts || needsVerification

    suspend fun refresh() {
        try {
            snapshot = withContext(Dispatchers.IO) { CameraTransport.products(settings) }
            receivedElapsed = SystemClock.elapsedRealtime()
            elapsedNow = SystemClock.elapsedRealtime()
            refreshFailed = false
            needsVerification = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            refreshFailed = true
            throw e
        }
        refreshedAt = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
    }
    fun reload() {
        if (busy) return
        busy = true
        scope.launch {
            try { refresh(); error = null } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Не удалось загрузить продукты" } finally { busy = false }
        }
    }
    fun mutate(action: String, fields: Map<String, String>) {
        if (busy || staleProducts || needsVerification) return
        busy = true
        message = ""
        error = null
        scope.launch {
            var committed = false
            try {
                withContext(Dispatchers.IO) { CameraTransport.mutate(settings, action, fields) }
                message = when (action) {
                    "add" -> "ESP32 подтвердил загрузку нового продукта."
                    "weigh" -> "ESP32 подтвердил сохранение контрольного веса."
                    else -> "ESP32 подтвердил извлечение продукта и удаление его данных."
                }
                committed = true
                editor = null
                remove = null
                history = null
                refresh()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                needsVerification = true
                editor = null
                remove = null
                error = if (committed) "Изменение принято ESP32, но обновить список не удалось: ${e.message}. Нажмите «Обновить продукты». Не повторяйте запись."
                else "${e.message}. Результат операции не подтверждён: запрос мог быть принят ESP32. Нажмите «Обновить продукты» и проверьте историю перед повтором. Автоматического повтора нет."
            } finally { busy = false }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val text = exportText
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IOException("Не удалось открыть файл")
                        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    message = "CSV сохранён. Экспорт содержит только доступные в ESP32 записи."
                } catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Ошибка экспорта: ${e.message}" }
            }
        }
    }

    LaunchedEffect(settings) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Контроль за весом продукта", style = MaterialTheme.typography.titleLarge)
        Text("Данные хранятся в ESP32. Прошивка 1.3.2 хранит до 32 контрольных взвешиваний на продукт. Старые записи нельзя восстановить экспортом.")
        Text("После 32 замеров приложение блокирует новую запись, чтобы прошивка не удалила самый старый замер. Для большей истории требуется доработка прошивки.")
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Обмен с ESP32… Дождитесь ответа; повторная отправка заблокирована.")
        }
        if (snapshot != null) {
            val age = FreshnessPolicy.ageSeconds(receivedElapsed, elapsedNow) ?: 0
            Text(
                if (staleProducts) "Список устарел · получен $age с назад. Обновите продукты перед изменениями."
                else "Список актуален · получен $age с назад",
                color = if (staleProducts) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        if (needsVerification) Text("Новая запись заблокирована до успешного обновления списка. Сверьте последнее взвешивание и историю.", color = MaterialTheme.colorScheme.error)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { reload() }, enabled = !busy) { Text(if (busy) "Обновление…" else "Обновить продукты") }
        snapshot?.let { data ->
            Text("Занято ${data.products.count { it.active }} из 30 · Обновлено $refreshedAt")
            Text("Текущая дата ESP32: ${ProductDates.display(data.today)}")
            if (!ProductDates.valid(data.today)) {
                Text("Часы ESP32 не синхронизированы. Добавление и взвешивание недоступны до NTP-синхронизации.", color = MaterialTheme.colorScheme.error)
            }
            if (data.readOnly) Text("Режим просмотра. Для изменения данных нужен администратор.")
            val free = data.products.firstOrNull { !it.active }
            Button(
                enabled = !writeBlocked && !data.readOnly && free != null && ProductDates.valid(data.today),
                modifier = Modifier.fillMaxWidth(),
                onClick = { selected = free; editor = "add" }
            ) { Text(if (free == null) "Все 30 слотов заняты" else "Загрузить новый продукт") }
            OutlinedButton(enabled = !busy && data.products.any { it.active }, onClick = {
                exportText = productsCsv(data)
                exporter.launch("ClimateCamera_products_${data.today.ifBlank { "history" }}.csv")
            }) { Text("Экспорт истории в CSV") }
            data.products.forEach { product ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Продукт №${product.id}", style = MaterialTheme.typography.titleMedium)
                        if (!product.active) {
                            Text("Свободный слот")
                        } else {
                            Text("Закладка: ${ProductDates.display(product.startDate)}")
                            Text("Начальный вес: ${grams(product.initialWeight)} г")
                            val last = product.history.lastOrNull()
                            Text(if (last == null) "Контрольных взвешиваний пока нет"
                                else "Последний вес: ${grams(last.weight)} г · ${ProductDates.display(last.date)}")
                            Text("Усушка: ${grams(product.lossPct)} % · Цель: ${grams(product.targetLossPct)} %")
                            Text("Целевой вес: ${grams(product.targetWeight)} г")
                            Text("Прогноз достижения цели: ${product.forecast()}")
                            Text("Прогноз по средней потере веса, не подтверждение безопасности или готовности к употреблению.", style = MaterialTheme.typography.bodySmall)
                            Button(enabled = !writeBlocked && !data.readOnly && ProductDates.valid(data.today) && product.history.size < 32, onClick = {
                                selected = product; editor = "weigh"
                            }) { Text("Взвесить") }
                            if (product.history.size >= 32) Text("Достигнут лимит 32 замеров. Сохраните CSV; для продолжения без потери истории обновите хранилище прошивки.")
                            OutlinedButton(enabled = !busy, onClick = { history = product }) {
                                Text("История взвешиваний (${product.history.size})")
                            }
                            OutlinedButton(enabled = !writeBlocked && !data.readOnly, onClick = { remove = product }) { Text("Извлечь продукт") }
                        }
                    }
                }
            }
        }
    }

    val editMode = editor
    val editProduct = selected
    val current = snapshot
    if (editMode != null && editProduct != null && current != null) {
        ProductEditor(editMode, editProduct, current.today, busy, staleProducts || needsVerification, onDismiss = { if (!busy) editor = null }, onSave = { fields ->
            mutate(editMode, fields)
        })
    }
    history?.let { product ->
        AlertDialog(onDismissRequest = { history = null }, title = { Text("История · Продукт №${product.id}") }, text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Начальный: ${ProductDates.display(product.startDate)} · ${grams(product.initialWeight)} г")
                if (product.history.isEmpty()) Text("Контрольных взвешиваний нет")
                product.history.forEachIndexed { index, w ->
                    val loss = if (product.initialWeight > 0) (product.initialWeight - w.weight) / product.initialWeight * 100 else 0.0
                    Text("${index + 1}. ${ProductDates.display(w.date)} · ${grams(w.weight)} г · усушка ${grams(loss)} %")
                }
                Text("В памяти прошивки сохраняются максимум 32 контрольных замера.")
            }
        }, confirmButton = { TextButton(onClick = { history = null }) { Text("Закрыть") } })
    }
    remove?.let { product ->
        AlertDialog(onDismissRequest = { if (!busy) remove = null }, title = { Text("Извлечь продукт №${product.id}?") }, text = {
            Text("Начальный вес и вся история будут удалены из памяти ESP32 без возможности отмены. При необходимости сначала сохраните CSV.")
        }, confirmButton = {
            TextButton(enabled = !writeBlocked, onClick = { mutate("remove", mapOf("id" to product.id.toString())) }) { Text("Удалить данные") }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { remove = null }) { Text("Отмена") } })
    }
}

@Composable
private fun ProductEditor(mode: String, product: Product, today: String, busy: Boolean, blocked: Boolean, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val context = LocalContext.current
    var weight by remember(mode, product.id) { mutableStateOf("") }
    var date by remember(mode, product.id) { mutableStateOf(today) }
    var target by remember(mode, product.id) { mutableStateOf("38") }
    val parsedWeight = parseWeight(weight)
    val parsedTarget = target.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 5.0..80.0 }
    val add = mode == "add"
    val validDate = ProductDates.valid(date) && date <= today
    val ready = parsedWeight != null && (!add || (validDate && parsedTarget != null))
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = {
        Text(if (add) "Загрузить новый продукт" else "Взвесить продукт №${product.id}")
    }, text = {
        Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (blocked) Text("Данные устарели. Закройте окно и нажмите «Обновить продукты» перед сохранением.", color = MaterialTheme.colorScheme.error)
            Text(if (add) "Первый свободный номер: №${product.id}. Номер окончательно назначает ESP32 при сохранении." else "Дата взвешивания: ${ProductDates.display(today)} · по часам ESP32")
            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(if (add) "Первоначальный вес, г" else "Текущий вес, г") },
                enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Text("Допустимо 1–100000 г; дробную часть можно вводить через точку или запятую.")
            if (add) {
                OutlinedButton(enabled = !busy, onClick = {
                    val parts = date.split('-').map { it.toIntOrNull() ?: 1 }
                    DatePickerDialog(context, { _, year, month, day ->
                        date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    }, parts.getOrElse(0) { 2026 }, parts.getOrElse(1) { 1 } - 1, parts.getOrElse(2) { 1 }).show()
                }) { Text("Дата закладки: ${ProductDates.display(date)} · выбрать") }
                if (!validDate) Text("Дата не должна быть позже текущей даты ESP32", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Целевая усушка, % (5–80)") },
                    enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            } else {
                Text("Текущую дату записывает контроллер автоматически. Дата телефона в запрос не передаётся.")
                if (parsedWeight != null && parsedWeight > product.initialWeight) Text("Вес выше начального: усушка будет отрицательной. Проверьте введённое значение.")
            }
        }
    }, confirmButton = {
        TextButton(enabled = !busy && !blocked && ready, onClick = {
            // ESP32 expects initialWeight for add, but weight for a control weighing.
            val fields = mutableMapOf(
                "id" to product.id.toString(),
                (if (add) "initialWeight" else "weight") to parsedWeight.toString()
            )
            if (add) { fields["startDate"] = date; fields["targetLossPct"] = parsedTarget.toString() }
            onSave(fields)
        }) { Text(if (busy) "Сохранение…" else "Сохранить") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}
