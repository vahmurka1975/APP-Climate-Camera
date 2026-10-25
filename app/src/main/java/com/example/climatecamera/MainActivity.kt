@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.climatecamera

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.climatecamera.ui.theme.ClimateCameraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClimateCameraTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ClimateScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

internal data class ConnectionSettings(
    val address: String,
    val username: String,
    val password: String
)

private data class CameraStatus(
    val version: String,
    val temperature: Double?,
    val humidity: Double?,
    val rpm: Double?,
    val enabled: Boolean,
    val sensorOk: Boolean,
    val sensorDegraded: Boolean,
    val fanFault: Boolean,
    val fridge: Boolean,
    val humidifier: Boolean,
    val fan: Boolean,
    val fridgeWait: Boolean,
    val fridgeWaitSec: Int,
    val targetTemperature: Double?,
    val targetHumidity: Double?,
    val targetRpm: Double?,
    val sensorAgeSec: Int,
    val uptimeSec: Long,
    val date: String,
    val time: String
)

@Composable
private fun ClimateScreen(modifier: Modifier = Modifier) {
    var address by rememberSaveable {
        mutableStateOf("http://192.168.0.150")
    }
    var username by rememberSaveable { mutableStateOf("") }

    // Пароль сохраняется только по явному согласию пользователя.
    var password by remember { mutableStateOf("") }

    var connection by remember {
        mutableStateOf<ConnectionSettings?>(null)
    }
    var status by remember { mutableStateOf<CameraStatus?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var receivedAt by remember { mutableStateOf("") }
    var pollFailures by remember { mutableStateOf(0) }
    var nextPollMs by remember { mutableStateOf(5_000L) }
    var receivedElapsed by remember { mutableStateOf<Long?>(null) }
    var elapsedNow by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(connection) {
        while (true) {
            elapsedNow = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val staleStatus = error != null || FreshnessPolicy.stale(
        receivedElapsed, elapsedNow, FreshnessPolicy.STATUS_MAX_AGE_MS
    )
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var connectionExpanded by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val store = remember(context) { ConnectionStore(context) }
    var rememberPassword by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf("") }
    LaunchedEffect(store) {
        try {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved != null) {
                address = saved.address
                username = saved.username
                password = saved.password
                rememberPassword = saved.rememberPassword
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            storageMessage = "Не удалось прочитать подключение. Можно ввести данные заново или удалить сохранённые."
        }
    }
    LaunchedEffect(connection, rememberPassword) {
        val settings = connection ?: return@LaunchedEffect
        while (status == null) delay(100)
        try {
            withContext(Dispatchers.IO) { store.save(settings, rememberPassword) }
            storageMessage = if (rememberPassword) "Подключение и пароль сохранены на устройстве" else "Адрес и логин сохранены; пароль не сохраняется"
        } catch (e: CancellationException) { throw e } catch (e: Exception) { storageMessage = "Не удалось сохранить подключение. Данные доступны только до закрытия приложения." }
    }


    LaunchedEffect(connection) {
        pollFailures = 0
        nextPollMs = 5_000L
        val settings = connection ?: return@LaunchedEffect

        while (true) {
            loading = true

            try {
                status = loadStatus(settings)
                pollFailures = 0
                receivedElapsed = SystemClock.elapsedRealtime()
                elapsedNow = SystemClock.elapsedRealtime()
                error = null
                receivedAt = java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
                ).format(java.util.Date())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Старые показания оставляем на экране,
                // но явно сообщаем об ошибке обновления.
                error = ConnectionPolicy.errorMessage(e)
                pollFailures = (pollFailures + 1).coerceAtMost(4)
            } finally {
                loading = false
            }

            nextPollMs = ConnectionPolicy.pollDelayMs(pollFailures)
            delay(nextPollMs)
        }
    }

    LaunchedEffect(connection, status != null) {
        if (connection != null && status != null) connectionExpanded = false
    }
    val sectionTitles = listOf("Камера", "Продукты", "Настройки", "Архив")
    val scrollState = key(selectedTab) { rememberScrollState() }
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CLIMATE CAMERA", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(sectionTitles[selectedTab], style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                }
                Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ConnectionBanner(
                label = ConnectionPolicy.label(connection != null, status != null, error != null, staleStatus),
                detail = when {
                    connection == null && status != null -> "Обновление остановлено · показаны последние данные"
                    connection == null -> "Укажите адрес камеры и подключитесь"
                    loading && pollFailures > 0 -> "Повторное чтение данных…"
                    pollFailures > 0 -> "Повторное чтение через интервал ${nextPollMs / 1000} с"
                    receivedAt.isNotEmpty() -> "Последний ответ: $receivedAt"
                    else -> "Ожидание первого ответа ESP32"
                },
                warning = connection != null && (error != null || staleStatus),
                active = connection != null && status != null && error == null && !staleStatus,
                onClick = { connectionExpanded = !connectionExpanded },
                expanded = connectionExpanded
            )
            if (connectionExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Подключение к камере", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес контроллера") },
                    placeholder = { Text("http://адрес:порт") },
                    singleLine = true,
                    enabled = connection == null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Логин") },
                    singleLine = true,
                    enabled = connection == null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    enabled = connection == null,
                    visualTransformation =
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row {
                    Checkbox(checked = rememberPassword,
                        onCheckedChange = { rememberPassword = it }, enabled = connection == null)
                    Text("Запомнить пароль на этом устройстве")
                }
                Text("Для удалённого доступа используйте VPN или HTTPS. Обычный HTTP не шифрует пароль при передаче.")
                if (storageMessage.isNotBlank()) Text(storageMessage)
                val storageScope = rememberCoroutineScope()
                TextButton(enabled = connection == null, onClick = {
                    storageScope.launch {
                        try {
                            withContext(Dispatchers.IO) { store.clear() }
                            address = ""; username = ""; password = ""; rememberPassword = false
                            storageMessage = "Сохранённое подключение удалено"
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { storageMessage = "Не удалось удалить сохранённое подключение" }
                    }
                }) { Text("Забыть подключение") }
                Button(
                    onClick = {
                        if (connection == null) {
                            error = null
                            status = null
                            receivedAt = ""
                            receivedElapsed = null

                            connection = ConnectionSettings(
                                address = address.trim(),
                                username = username.trim(),
                                password = password
                            )
                        } else {
                            connection = null
                            loading = false
                        }
                    },
                    enabled = connection != null ||
                        address.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (connection == null) {
                            "Подключиться"
                        } else {
                            "Остановить обновление"
                        }
                    )
                }

                Text(
                    "Обновление каждые 5 секунд. " +
                        "Логин и пароль нужны, если API требует авторизацию."
                )

                    }
                }
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            error?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Ошибка обновления: $message", modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            when (selectedTab) {
                1 -> key(connection) { ProductsScreen(connection) }
                2 -> key(connection) { CameraSettingsScreen(connection) }
                3 -> ArchiveScreen(connection)
            }
            if (selectedTab == 0 && status == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DashboardIcon("camera", Modifier.size(40.dp), MaterialTheme.colorScheme.primary)
                        Text(if (loading) "Получаем показания" else "Ваша камера — на одном экране",
                            style = MaterialTheme.typography.titleLarge)
                        Text("После подключения здесь появятся температура, влажность и состояние оборудования.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!connectionExpanded) TextButton(onClick = { connectionExpanded = true }) { Text("Открыть подключение") }
                    }
                }
            }
        if (selectedTab == 0) status?.let { data ->
            val age = FreshnessPolicy.ageSeconds(receivedElapsed, elapsedNow)
            Text(
                text = if (staleStatus)
                    "УСТАРЕВШИЕ ДАННЫЕ · последнее получение: ${age ?: 0} с назад. Состояния реле и показания ниже не подтверждены текущим ответом ESP32."
                else "Данные актуальны · получены ${age ?: 0} с назад",
                color = if (staleStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (connection == null) {
                    "Обновление остановлено. Показаны последние данные."
                } else if (error != null) {
                    "Нет свежих данных. Показания могут быть устаревшими."
                } else {
                    "Последний успешный ответ: $receivedAt"
                }
            )


                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 480.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("Температура", "${number(data.temperature, 2)} °C",
                                "${number(data.targetTemperature, 1)} °C", "temperature", Modifier.weight(1f), staleStatus || connection == null)
                            MetricCard("Влажность", "${number(data.humidity, 2)} %",
                                "${number(data.targetHumidity, 1)} %", "humidity", Modifier.weight(1f), staleStatus || connection == null)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard("Температура", "${number(data.temperature, 2)} °C",
                                "${number(data.targetTemperature, 1)} °C", "temperature", stale = staleStatus || connection == null)
                            MetricCard("Влажность", "${number(data.humidity, 2)} %",
                                "${number(data.targetHumidity, 1)} %", "humidity", stale = staleStatus || connection == null)
                        }
                    }
                }
                MetricCard("Вентилятор", "${number(data.rpm, 0)} об/мин",
                    "${number(data.targetRpm, 0)} об/мин", "fan", stale = staleStatus || connection == null)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Оборудование", style = MaterialTheme.typography.titleLarge)
                        if (staleStatus || connection == null) Text("Последние известные состояния · не подтверждены сейчас",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        EquipmentRow("Автоматика", if (data.enabled) "Включена" else "Выключена", "settings", data.enabled)
                        EquipmentRow("Датчик", when {
                            !data.sensorOk -> "Ошибка"
                            data.sensorDegraded -> "Нестабильные данные"
                            else -> "Исправен"
                        }, "temperature", data.sensorOk && !data.sensorDegraded, !data.sensorOk || data.sensorDegraded)
                        HorizontalDivider()
                        EquipmentRow("Холодильник", onOff(data.fridge), "cold", data.fridge)
                        EquipmentRow("Увлажнитель", onOff(data.humidifier), "humidity", data.humidifier)
                        EquipmentRow("Вентилятор", onOff(data.fan), "fan", data.fan)
                        if (data.fridgeWait) Text("Ожидание компрессора: ${data.fridgeWaitSec} с",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (data.fanFault) Text("АВАРИЯ ТАХОМЕТРА", color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold)
                    }
                }
                var detailsExpanded by rememberSaveable { mutableStateOf(false) }
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { detailsExpanded = !detailsExpanded }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (detailsExpanded) "Скрыть сведения о камере" else "Сведения о камере")
                        }
                        if (detailsExpanded) {
                            Text("Возраст данных датчика: ${data.sensorAgeSec} с")
                            Text("Прошивка: ${data.version}")
                            Text("Время ESP32: ${data.date} ${data.time}")
                            Text("Работа после запуска: ${data.uptimeSec / 3600} ч ${(data.uptimeSec % 3600) / 60} мин")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            listOf(0, 1, 3, 2).forEach { index ->
                NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    icon = { DashboardIcon(when (index) { 0 -> "camera"; 1 -> "products"; 3 -> "archive"; else -> "settings" }) },
                    label = { Text(sectionTitles[index], maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

private fun number(value: Double?, digits: Int): String {
    return value?.let {
        String.format(Locale.getDefault(), "%.${digits}f", it)
    } ?: "—"
}

private fun onOff(value: Boolean): String {
    return if (value) "ВКЛ" else "ВЫКЛ"
}

private fun JSONObject.finiteDouble(key: String): Double? {
    return optDouble(key, Double.NaN).takeIf { it.isFinite() }
}

private suspend fun loadStatus(
    settings: ConnectionSettings
): CameraStatus = withContext(Dispatchers.IO) {
    val data = CameraTransport.status(settings)

        val config = data.optJSONObject("config")
            ?: throw IOException(
                "В ответе нет config. Проверьте адрес API."
            )

        CameraStatus(
            version = data.optString("version", "—"),
            temperature = data.finiteDouble("temp"),
            humidity = data.finiteDouble("hum"),
            rpm = data.finiteDouble("rpm"),
            enabled = data.getBoolean("enabled"),
            sensorOk = data.getBoolean("sensorOk"),
            sensorDegraded = data.optBoolean("sensorDegraded"),
            fanFault = data.optBoolean("fanTachFault"),
            fridge = data.getBoolean("fridge"),
            humidifier = data.getBoolean("humid"),
            fan = data.getBoolean("fan"),
            fridgeWait = data.optBoolean("fridgeWait"),
            fridgeWaitSec = data.optInt("fridgeWaitSec"),
            targetTemperature = config.finiteDouble("tempTarget"),
            targetHumidity = config.finiteDouble("humTarget"),
            targetRpm = config.finiteDouble("fanTargetRPM"),
            sensorAgeSec = data.optInt("sensorAgeSec"),
            uptimeSec = data.optLong("uptimeSec"),
            date = data.optString("date", "—"),
            time = data.optString("time", "—")
        )
}
