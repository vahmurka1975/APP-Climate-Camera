package com.example.climatecamera

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ArchiveScreen(connection: ConnectionSettings?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { ProductArchiveStore.get(context) }
    // Application-scoped helper remains available across tab changes.
    var entries by remember { mutableStateOf<List<ArchivedProduct>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<ArchivedProduct?>(null) }
    var editing by remember { mutableStateOf<ArchivedProduct?>(null) }
    var exportText by rememberSaveable { mutableStateOf("") }
    var archiveQuery by rememberSaveable { mutableStateOf("") }
    val cameraKey = remember(connection?.address) {
        connection?.let { runCatching { ArchivePolicy.cameraKey(it.address) }.getOrNull() }
    }
    val visibleEntries = entries.filter { entry ->
        val q = archiveQuery.trim().lowercase(Locale.getDefault())
        q.isBlank() || listOf(
            entry.name, entry.notes, entry.state.label, entry.product.id.toString(),
            entry.product.startDate
        ).any { it.lowercase(Locale.getDefault()).contains(q) }
    }
    val readArchive: suspend () -> Unit = {
        entries = withContext(Dispatchers.IO) { store.list() }
        loaded = true
    }
    fun load() {
        if (busy) return
        busy = true
        scope.launch {
            try { readArchive(); error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Не удалось прочитать архив: ${e.message}" }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw java.io.IOException("Не удалось открыть файл")
                        stream.use { it.write(exportText.toByteArray(Charsets.UTF_8)) }
                    }
                    message = "CSV архива сохранён. Это экспорт для просмотра, не файл восстановления базы."
                    error = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = "Ошибка сохранения CSV: ${e.message}" }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Локальный архив", style = MaterialTheme.typography.titleLarge)
        Text("Копии продуктов на этом телефоне, не текущие показания ESP32. Архив доступен без подключения.")
        Text("Удаление приложения или очистка его данных удалит архив. CSV не содержит паролей и токенов. Восстановление базы из CSV в этой версии не поддерживается.")
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (message.isNotBlank()) Text(message)
        OutlinedButton(enabled = !busy, onClick = { load() }) { Text("Обновить архив") }
        if (loaded && entries.isEmpty()) Text("Архив пока пуст. Продукты сохраняются перед извлечением через это приложение.")
        if (entries.isNotEmpty()) {
            OutlinedTextField(
                value = archiveQuery,
                onValueChange = { archiveQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск в архиве") },
                placeholder = { Text("Название, заметки, слот или состояние") },
                singleLine = true
            )
            Text(
                if (archiveQuery.isBlank()) "Сохранённых копий: ${entries.size}"
                else "Показано: ${visibleEntries.size} из ${entries.size}"
            )
            OutlinedButton(enabled = !busy, onClick = {
                exportText = archiveCsv(entries)
                exporter.launch("climate-archive.csv")
            }) { Text("Сохранить CSV архива") }
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val csv = archiveCsv(entries)
                        val uri = withContext(Dispatchers.IO) {
                            val dir = File(context.cacheDir, "archive_exports").apply { mkdirs() }
                            val file = File(dir, "climate-archive.csv")
                            file.writeText(csv, Charsets.UTF_8)
                            FileProvider.getUriForFile(context, "${context.packageName}.archivefiles", file)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("Архив продуктов", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Поделиться архивом"))
                        error = null
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = "Не удалось передать CSV: ${e.message}" }
                    finally { busy = false }
                }
            }) { Text("Поделиться CSV") }
        }
        visibleEntries.forEach { entry ->
            val p = entry.product
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text("Слот №${p.id} · Закладка ${ProductDates.display(p.startDate)}")
                    Text("Источник: ${entry.cameraKey.take(12)} · ID: ${entry.archiveId.take(8)}")
                    Text("Копия: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(entry.archivedAt))}")
                    Text(entry.state.label, color = if (entry.state == ArchiveState.PENDING)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text("Начальный: ${archiveNumber(p.initialWeight)} г · Последний известный: ${archiveNumber(p.lastWeight)} г")
                    Text("Усушка ${archiveNumber(p.lossPct)} % · Цель ${archiveNumber(p.targetLossPct)} %")
                    Text("Доступных контрольных измерений: ${p.history.size}")
                    if (entry.notes.isNotBlank()) Text(entry.notes)
                    OutlinedButton(enabled = !busy, onClick = { detail = entry }) { Text("История копии") }
                    TextButton(enabled = !busy, onClick = { editing = entry }) { Text("Название и заметки") }
                    if (entry.state == ArchiveState.PENDING || entry.state == ArchiveState.STILL_PRESENT || entry.state == ArchiveState.SLOT_CHANGED) {
                        Text("Проверка только читает слот; команда извлечения не повторяется.")
                        TextButton(enabled = !busy && connection != null && cameraKey == entry.cameraKey, onClick = {
                            val settings = connection ?: return@TextButton
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val current = CameraTransport.products(settings).products.first { it.id == p.id }
                                        store.state(entry.archiveId, ArchivePolicy.observed(p, current))
                                    }
                                    readArchive()
                                    message = "Состояние слота проверено. Историческая копия не изменена."
                                    error = null
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = ConnectionPolicy.errorMessage(e) }
                                finally { busy = false }
                            }
                        }) { Text("Проверить слот на ESP32") }
                        if (connection == null || cameraKey != entry.cameraKey) Text("Для проверки подключитесь к исходному адресу камеры.")
                    }
                }
            }
        }
    }
    detail?.let { entry ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(entry.name) }, text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).then(Modifier.verticalArchiveScroll()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сохранённая копия; это не актуальный список камеры.")
                Text("Начальный: ${ProductDates.display(entry.product.startDate)} · ${archiveNumber(entry.product.initialWeight)} г")
                entry.product.history.forEach { Text("${ProductDates.display(it.date)} · ${archiveNumber(it.weight)} г") }
            }
        }, confirmButton = { TextButton(onClick = { detail = null }) { Text("Закрыть") } })
    }
    editing?.let { entry ->
        ArchiveEditDialog(entry, busy, onDismiss = { editing = null }) { name, notes ->
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { store.edit(entry.archiveId, name, notes) }
                    editing = null
                    readArchive()
                    error = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = "Ошибка записи архива: ${e.message}" }
                finally { busy = false }
            }
        }
    }
}

@Composable
private fun Modifier.verticalArchiveScroll(): Modifier = this.then(
    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
)

private fun archiveNumber(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun ArchiveEditDialog(entry: ArchivedProduct, busy: Boolean, onDismiss: () -> Unit,
                              onSave: (String, String) -> Unit) {
    var name by remember(entry.archiveId) { mutableStateOf(entry.name) }
    var notes by remember(entry.archiveId) { mutableStateOf(entry.notes) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Название и заметки") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 120) name = it },
                enabled = !busy, label = { Text("Название (до 120 символов)") }, singleLine = true)
            OutlinedTextField(value = notes, onValueChange = { if (it.length <= 4000) notes = it },
                enabled = !busy, label = { Text("Заметки (до 4000 символов)") }, maxLines = 5)
        }
    }, confirmButton = {
        TextButton(enabled = !busy && name.isNotBlank(), onClick = { onSave(name, notes) }) { Text("Сохранить") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } })
}
