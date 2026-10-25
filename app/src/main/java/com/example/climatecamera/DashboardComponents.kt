package com.example.climatecamera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Locally drawn icons: no extra dependency, asset download or icon font. */
@Composable
internal fun DashboardIcon(kind: String, modifier: Modifier = Modifier.size(24.dp),
    tint: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        val stroke = Stroke(1.7f * scale, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, xx: Float, yy: Float) =
            drawLine(tint, point(x, y), point(xx, yy), strokeWidth = 1.7f * scale, cap = StrokeCap.Round)
        when (kind) {
            "temperature" -> {
                drawRoundRect(tint, point(9f, 2f), Size(6f * scale, 14f * scale),
                    androidx.compose.ui.geometry.CornerRadius(3f * scale), style = stroke)
                drawCircle(tint, 4.5f * scale, point(12f, 17f), style = stroke)
                line(12f, 7f, 12f, 17f)
                drawCircle(tint, 2f * scale, point(12f, 17f))
            }
            "humidity" -> {
                val path = Path().apply {
                    moveTo(12f * scale, 2f * scale)
                    cubicTo(9f * scale, 7f * scale, 5f * scale, 11f * scale, 5f * scale, 15f * scale)
                    cubicTo(5f * scale, 24f * scale, 19f * scale, 24f * scale, 19f * scale, 15f * scale)
                    cubicTo(19f * scale, 11f * scale, 15f * scale, 7f * scale, 12f * scale, 2f * scale)
                    close()
                }
                drawPath(path, tint, style = stroke)
                line(8f, 15f, 8f, 17f)
            }
            "fan" -> {
                drawCircle(tint, 2.5f * scale, point(12f, 12f), style = stroke)
                listOf(Offset(12f, 5.5f), Offset(18f, 15.5f), Offset(6f, 15.5f)).forEach {
                    drawCircle(tint, 3.2f * scale, point(it.x, it.y), style = stroke)
                }
            }
            "cold" -> {
                line(12f, 2f, 12f, 22f); line(3.3f, 7f, 20.7f, 17f); line(3.3f, 17f, 20.7f, 7f)
                line(9f, 4f, 12f, 7f); line(15f, 4f, 12f, 7f)
                line(9f, 20f, 12f, 17f); line(15f, 20f, 12f, 17f)
            }
            "products" -> {
                drawRoundRect(tint, point(4f, 7f), Size(16f * scale, 14f * scale),
                    androidx.compose.ui.geometry.CornerRadius(2f * scale), style = stroke)
                line(8f, 7f, 8f, 3f); line(8f, 3f, 16f, 3f); line(16f, 3f, 16f, 7f)
                line(4f, 12f, 20f, 12f)
            }
            "archive" -> {
                drawRect(tint, point(4f, 8f), Size(16f * scale, 13f * scale), style = stroke)
                drawRect(tint, point(3f, 3f), Size(18f * scale, 5f * scale), style = stroke)
                line(9f, 12f, 15f, 12f)
            }
            "settings" -> {
                listOf(6f to 8f, 12f to 16f, 18f to 10f).forEach { (x, y) ->
                    line(x, 3f, x, y - 2.5f); line(x, y + 2.5f, x, 21f)
                    drawCircle(tint, 2.5f * scale, point(x, y), style = stroke)
                }
            }
            else -> {
                drawRoundRect(tint, point(4f, 2f), Size(16f * scale, 20f * scale),
                    androidx.compose.ui.geometry.CornerRadius(3f * scale), style = stroke)
                line(4f, 10f, 20f, 10f); line(16f, 5f, 16f, 7f); line(16f, 13f, 16f, 16f)
            }
        }
    }
}

@Composable
internal fun ConnectionBanner(label: String, detail: String, warning: Boolean,
    active: Boolean, onClick: () -> Unit, expanded: Boolean) {
    val colors = MaterialTheme.colorScheme
    val accent = when { warning -> colors.error; active -> colors.primary; else -> colors.onSurfaceVariant }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (warning) colors.errorContainer else colors.surfaceVariant)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(accent, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall,
                    color = if (warning) colors.onErrorContainer else colors.onSurface)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = if (warning) colors.onErrorContainer else colors.onSurfaceVariant)
                Text(if (expanded) "Скрыть подключение" else "Показать подключение",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (warning) colors.onErrorContainer else colors.primary)
            }
        }
    }
}

@Composable
internal fun MetricCard(title: String, value: String, target: String, icon: String,
    modifier: Modifier = Modifier, stale: Boolean = false) {
    Card(modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardIcon(icon, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("Уставка: $target", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stale) Text("Последнее известное значение", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun EquipmentRow(title: String, value: String, icon: String, active: Boolean, fault: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardIcon(icon, tint = if (fault) colors.error else colors.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.labelLarge,
                color = when { fault -> colors.error; active -> colors.primary; else -> colors.onSurfaceVariant })
        }
    }
}
