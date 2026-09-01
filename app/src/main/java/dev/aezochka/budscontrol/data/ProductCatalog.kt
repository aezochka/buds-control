package dev.aezochka.budscontrol.data

import java.util.Locale

/**
 * Фото продукта по имени Bluetooth-устройства.
 *
 * Локальные ассеты лежат в APK, поэтому работают без сети. Для моделей,
 * которых нет в каталоге, отдаём null — UI рисует нейтральный силуэт,
 * а не подсовывает чужую картинку.
 */
object ProductCatalog {
    data class Product(
        val key: String,
        val title: String,
        val aliases: List<String>,
        val asset: String?,
        val cdn: String? = null,
    )

    private val products = listOf(
        Product(
            key = "realme_buds_t110",
            title = "realme Buds T110",
            aliases = listOf("buds t110", "realme t110", "t110"),
            asset = "products/realme_buds_t110.png",
            cdn = "https://image01.realme.net/general/20240820/172414694234076a17cd9b0614358b65e17a104ca306a.png",
        ),
        Product(
            key = "realme_buds_t310",
            title = "realme Buds T310",
            aliases = listOf("buds t310", "realme t310", "t310"),
            asset = "products/realme_buds_t310.png",
        ),
        Product(
            key = "realme_buds_air5_pro",
            title = "realme Buds Air 5 Pro",
            aliases = listOf("buds air 5 pro", "air5 pro", "buds air5"),
            asset = "products/realme_buds_air5_pro.png",
        ),
        Product(
            key = "realme_buds_air6",
            title = "realme Buds Air 6",
            aliases = listOf("buds air 6", "air6"),
            asset = "products/realme_buds_air6.png",
        ),
        Product(
            key = "oneplus_nord_buds3_pro",
            title = "OnePlus Nord Buds 3 Pro",
            aliases = listOf("nord buds 3 pro", "nord buds 3", "nord buds3"),
            asset = "products/oneplus_nord_buds3_pro.png",
        ),
    )

    fun match(bluetoothName: String?): Product? {
        val normalized = bluetoothName.orEmpty().lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty()) return null
        return products.firstOrNull { p -> p.aliases.any { it in normalized } }
    }

    fun localAsset(bluetoothName: String?): String? = match(bluetoothName)?.asset
    fun remoteUrl(bluetoothName: String?): String? = match(bluetoothName)?.cdn

    /** Все известные фото — для превью в настройках. */
    fun all(): List<Product> = products
}
