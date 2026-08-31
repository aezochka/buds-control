package dev.aezochka.budscontrol.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.util.Locale

/**
 * Resolves a product image from the *actual Bluetooth device name*. First check
 * the app's offline catalog, then cache a CDN image through Coil. Unknown models
 * deliberately return null — UI renders a neutral headphones silhouette rather
 * than pretending it knows the product.
 */
object ProductCatalog {
    data class Product(val key: String, val aliases: List<String>, val asset: String?, val cdn: String?)

    private val products = listOf(
        Product(
            key = "realme_buds_t110",
            aliases = listOf("realme buds t110", "buds t110", "realme t110"),
            asset = "products/realme_buds_t110.png",
            cdn = "https://image01.realme.net/general/20240820/172414694234076a17cd9b0614358b65e17a104ca306a.png",
        ),
    )

    fun match(bluetoothName: String?): Product? {
        val normalized = bluetoothName.orEmpty().lowercase(Locale.ROOT).trim()
        return products.firstOrNull { p -> p.aliases.any { it in normalized } }
    }

    fun localAsset(bluetoothName: String?): String? = match(bluetoothName)?.asset
    fun remoteUrl(bluetoothName: String?): String? = match(bluetoothName)?.cdn
}
