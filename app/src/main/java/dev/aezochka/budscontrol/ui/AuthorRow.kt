package dev.aezochka.budscontrol.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aezochka.budscontrol.i18n.tr

private const val TELEGRAM = "rz3nx"

/** Автор приложения: тап открывает Telegram. */
@Composable
fun AuthorRow() {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainer)
            .pressBounce {
                // Сначала пробуем само приложение, иначе браузер.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$TELEGRAM"))
                    )
                }.onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$TELEGRAM"))
                        )
                    }
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Send, null,
                tint = scheme.primary, modifier = Modifier.size(19.dp),
            )
        }
        // Тот же вид, что у остальных строк: слева подпись, справа значение.
        Text(
            tr("author"),
            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "@$TELEGRAM",
            style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
        )
    }
}
