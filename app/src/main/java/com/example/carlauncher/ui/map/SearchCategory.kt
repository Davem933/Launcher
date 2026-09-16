package com.example.carlauncher.ui.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Quick POI category chips shown in [SearchOverlay], matching the reference app's category row.
 * [mapboxCategoryName] is the Search Box API's own "sbsCanonicalName" for that category — verified
 * against `mapbox/mapbox-search-android`'s `ui/view/category/Category.kt` (the SDK's own predefined
 * category list), not guessed. Contacts/Favorites categories are deliberately excluded (out of scope).
 */
enum class SearchCategory(
    val label: String,
    val icon: ImageVector,
    val mapboxCategoryName: String,
) {
    GAS_STATION("Benzín", Icons.Default.LocalGasStation, "gas_station"),
    PARKING("Parkování", Icons.Default.LocalParking, "parking_lot"),
    RESTAURANT("Restaurace", Icons.Default.Restaurant, "restaurant"),
    SHOPPING("Obchody", Icons.Default.ShoppingCart, "shopping_mall"),
    COFFEE("Káva", Icons.Default.LocalCafe, "cafe"),
}
