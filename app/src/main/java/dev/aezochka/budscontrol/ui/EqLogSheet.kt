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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.audio.EqLog

/**
 * Лог эквалайзера прямо в приложении: видно, создался ли системный
 * эффект, дошли ли жесты, приняло ли железо значения. Копируется в буфер.
 */
@Composable
fun EqLogSheet(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lines by EqLog.lines.collectAsState()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerHigh,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 46.dp, height = 5.dp).clip(CircleShape).background(scheme.outline))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxWidth(0.66f)) {
                    Text("Лог звука", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        "${lines.size} записей",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainer)
                        .pressBounce { copy(context, EqLog.dump()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ContentCopy, "Копировать", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainer)
                        .pressBounce { EqLog.clear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, "Очистить", tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            if (lines.isEmpty()) {
                Text(
                    "Пусто. Открой эквалайзер и потяни полосу — тут появится, что произошло.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(scheme.surfaceContainerLow)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(lines.reversed()) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun copy(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("eq log", text))
    }
}
