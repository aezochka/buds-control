package dev.aezochka.budscontrol.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.CrashLog
import dev.aezochka.budscontrol.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Строка с логом последнего краша.
 *
 * Показывается только когда лог не пустой. Нужна, чтобы получить настоящий
 * стектрейс вместо догадок: копируется в буфер одной кнопкой.
 */
@Composable
fun CrashLogRow() {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // Читаем файл вне композиции: чтение в теле composable давало
    // микрофриз при каждом заходе в настройки.
    var log by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        log = withContext(Dispatchers.IO) {
            runCatching { CrashLog.read(context) }.getOrDefault("")
        }
    }

    if (log.isBlank()) return

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.errorContainer.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.error.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.BugReport, null,
                tint = scheme.error, modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.fillMaxWidth(0.62f)) {
            Text(
                tr("crashTitle"),
                style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface,
            )
            Text(
                tr("crashHint"),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.fillMaxWidth(0.04f))
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(scheme.surfaceContainer)
                .pressBounce { copyText(context, log) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ContentCopy, tr("copy"),
                tint = scheme.onSurfaceVariant, modifier = Modifier.size(17.dp),
            )
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(scheme.surfaceContainer)
                .pressBounce { CrashLog.clear(context); log = "" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.DeleteOutline, tr("clear"),
                tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("crash", text))
    }
}
