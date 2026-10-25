package com.example.climatecamera

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.climatecamera.ui.theme.ClimateCameraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    // Пароль не записывается в постоянное хранилище.
    var password by remember { mutableStateOf("") }

    var connection by remember {
        mutableStateOf<ConnectionSettings?>(null)
    }
    var status by remember { mutableStateOf<CameraStatus?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var receivedAt by remember { mutableStateOf("") }
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

    LaunchedEffect(connection) {
        val settings = connection ?: return@LaunchedEffect

        while (true) {
            loading = true

            try {
                status = loadStatus(settings)
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
            } finally {
                loading = false
            }

            delay(5_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Климатическая камера",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "ESP32 · приложение v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Подключение",
                    style = MaterialTheme.typography.titleMedium
                )

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

        Text(
            text = ConnectionPolicy.label(
                connected = connection != null,
                hasData = status != null,
                failed = error != null,
                stale = staleStatus
            ),
            color = if (connection != null && (error != null || staleStatus))
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        if (receivedAt.isNotEmpty()) Text("Последний успешный ответ: $receivedAt")

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Климат") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Продукты") })
        }
        if (selectedTab == 1) {
            key(connection) { ProductsScreen(connection) }
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        error?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ошибка обновления: $message",
                    modifier = Modifier.padding(16.dp),
                    color =
                        MaterialTheme.colorScheme.onErrorContainer
                )
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

            MetricCard(
                title = "Температура",
                value = "${number(data.temperature, 2)} °C",
                target = "${number(data.targetTemperature, 1)} °C"
            )

            MetricCard(
                title = "Влажность",
                value = "${number(data.humidity, 2)} %",
                target = "${number(data.targetHumidity, 1)} %"
            )

            MetricCard(
                title = "Вентилятор",
                value = "${number(data.rpm, 0)} об/мин",
                target = "${number(data.targetRpm, 0)} об/мин"
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Состояние оборудования",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Автоматика: " +
                            if (data.enabled) "включена" else "выключена"
                    )

                    Text(
                        "Датчик: " + when {
                            !data.sensorOk -> "ошибка"
                            data.sensorDegraded -> "нестабильные данные"
                            else -> "исправен"
                        }
                    )

                    Text("Холодильник: ${onOff(data.fridge)}")
                    Text("Увлажнитель: ${onOff(data.humidifier)}")
                    Text("Вентилятор: ${onOff(data.fan)}")

                    if (data.fridgeWait) {
                        Text(
                            "Ожидание компрессора: " +
                                "${data.fridgeWaitSec} с"
                        )
                    }

                    if (data.fanFault) {
                        Text(
                            "АВАРИЯ ТАХОМЕТРА",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text("Возраст данных датчика: ${data.sensorAgeSec} с")
                    Text("Прошивка: ${data.version}")
                    Text("Время ESP32: ${data.date} ${data.time}")

                    val hours = data.uptimeSec / 3600
                    val minutes = (data.uptimeSec % 3600) / 60
                    Text("Работа после запуска: $hours ч $minutes мин")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    target: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge
            )
            Text("Уставка: $target")
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
