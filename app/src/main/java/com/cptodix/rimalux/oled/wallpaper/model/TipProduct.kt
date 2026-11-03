package com.cptodix.rimalux.oled.wallpaper.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.cptodix.rimalux.oled.wallpaper.R

/**
 * Canonical Google Play Console One-Time Product (INAPP consumable) definitions
 * for Rimalux's voluntary "Send a Thanks" tip jar.
 *
 * Configure these exact Product IDs under:
 * Google Play Console -> Monetize with Play -> Products -> In-app products (One-time products)
 */
enum class TipProduct(
    val amountKey: String,
    val productId: String,
    val referenceUsdAmount: Int,
    @param:StringRes val fallbackLabelRes: Int
) {
    TIP_1(
        amountKey = "1",
        productId = "rimalux_tip_1",
        referenceUsdAmount = 1,
        fallbackLabelRes = R.string.tip_1_usd
    ),
    TIP_3(
        amountKey = "3",
        productId = "rimalux_tip_3",
        referenceUsdAmount = 3,
        fallbackLabelRes = R.string.tip_3_usd
    ),
    TIP_5(
        amountKey = "5",
        productId = "rimalux_tip_5",
        referenceUsdAmount = 5,
        fallbackLabelRes = R.string.tip_5_usd
    ),
    TIP_10(
        amountKey = "10",
        productId = "rimalux_tip_10",
        referenceUsdAmount = 10,
        fallbackLabelRes = R.string.tip_10_usd
    ),
    TIP_20(
        amountKey = "20",
        productId = "rimalux_tip_20",
        referenceUsdAmount = 20,
        fallbackLabelRes = R.string.tip_20_usd
    );

    companion object {
        fun fromProductId(productId: String): TipProduct? {
            return entries.firstOrNull { it.productId == productId }
        }
    }
}

@Immutable
data class TipCatalogItem(
    val product: TipProduct,
    val formattedPrice: String? = null,
    val isStoreReady: Boolean = false
) {
    companion object {
        val defaultCatalog: List<TipCatalogItem> = TipProduct.entries.map { TipCatalogItem(product = it) }
    }
}
