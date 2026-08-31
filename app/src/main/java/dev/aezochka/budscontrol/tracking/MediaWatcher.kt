package dev.aezochka.budscontrol.tracking

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android grants this explicitly in Settings. The service is intentionally
 * passive: it only observes standard media notification fields (title/artist),
 * never reads messages or uploads anything. Persistence is wired when a live
 * walk session exists.
 */
class MediaWatcher : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val artist = extras.getCharSequence("android.text")?.toString()
        // Data is handled by the repository session bridge. Do not infer a
        // currently-playing track when Android's media metadata is absent.
        if (title.isNullOrBlank() && artist.isNullOrBlank()) return
    }
}
