package dev.aezochka.budscontrol.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.aezochka.budscontrol.R

/**
 * Звуки нажатия из ассетов.
 *
 * Системные тоны звучали чуждо, поэтому здесь проигрываются подготовленные
 * короткие сэмплы. SoundPool держит их распакованными в памяти, поэтому
 * задержки между тапом и звуком нет, и главный поток не трогается.
 */
class ClickSounds(context: Context) {

    /** Набор звуков, который можно выбрать в настройках. */
    enum class Pack(val key: String, val resId: Int?) {
        Off("off", null),
        ClickA("click_a", R.raw.click_a),
        ClickB("click_b", R.raw.click_b),
        ClickC("click_c", R.raw.click_c),
        ;

        companion object {
            fun from(key: String?): Pack = entries.firstOrNull { it.key == key } ?: ClickA
        }
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // USAGE_MEDIA: канал «звук нажатий» системы этот путь не глушит,
                // в отличие от playSoundEffect.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** id загруженных сэмплов по ключу набора. */
    private val loaded: Map<String, Int> = Pack.entries
        .mapNotNull { pack -> pack.resId?.let { pack.key to pool.load(context, it, 1) } }
        .toMap()

    fun play(packKey: String, volume: Float = 1f) {
        val pack = Pack.from(packKey)
        if (pack == Pack.Off) return
        val id = loaded[pack.key] ?: return
        runCatching { pool.play(id, volume, volume, 1, 0, 1f) }
    }

    fun release() {
        runCatching { pool.release() }
    }
}
