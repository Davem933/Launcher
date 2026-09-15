package com.example.carlauncher.ui.map

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.ui.theme.CarColors
import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import kotlinx.coroutines.delay

private const val TAG = "DestinationSearchBar"
private const val MIN_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Always-visible search overlay for the map panel, backed by the Mapbox Search Box SDK
 * (`SearchEngine`, `ApiType.SEARCH_BOX`). Two-step "interactive search" flow per the SDK's
 * own docs (MapboxSearch/search-sdk/src/main/java/com/mapbox/search/SearchEngine.kt):
 *  1) [SearchEngine.search] — query text -> [SearchSuggestion] list (no coordinates yet).
 *  2) [SearchEngine.select] — chosen suggestion -> resolved [SearchResult] with coordinates,
 *     delivered via [SearchSelectionCallback.onResult] (or [SearchSelectionCallback.onResults]
 *     for a category/brand suggestion, or [SearchSelectionCallback.onSuggestions] for a
 *     "query" suggestion that expands into further suggestions instead of a final place).
 *
 * [onDestinationSelected] just reports the resolved point + name for now — Task 3 wires it to
 * `MapboxNavigation.requestRoutes(...)`.
 */
@Composable
fun DestinationSearchBar(
    currentLocation: VehicleDisplayLocation?,
    onDestinationSelected: (Point, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchEngine = remember {
        SearchEngine.createSearchEngine(
            apiType = ApiType.SEARCH_BOX,
            settings = SearchEngineSettings(),
        )
    }

    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }

    // Read the latest GPS fix at request time without re-triggering the debounce below on every
    // ~500ms location tick — keying the LaunchedEffect on `currentLocation` directly would cancel
    // the pending search on every fix and it would never actually fire while driving.
    val latestLocation by rememberUpdatedState(currentLocation)

    LaunchedEffect(query) {
        if (query.length < MIN_QUERY_LENGTH) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        searchEngine.search(
            query = query,
            options = SearchOptions(
                proximity = latestLocation?.let { Point.fromLngLat(it.lng, it.lat) },
            ),
            callback = object : SearchSuggestionsCallback {
                override fun onSuggestions(newSuggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                    suggestions = newSuggestions
                }

                override fun onError(e: Exception) {
                    Log.e(TAG, "Search suggestions request failed", e)
                    suggestions = emptyList()
                }
            },
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CarColors.Surface)
                .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = CarColors.Text3,
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { newQuery -> query = newQuery },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = CarColors.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(CarColors.Text),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Kam jedeme?", color = CarColors.Text3, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                },
            )
        }

        if (suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarColors.Surface),
            ) {
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                searchEngine.select(
                                    suggestion = suggestion,
                                    callback = object : SearchSelectionCallback {
                                        override fun onResult(
                                            suggestion: SearchSuggestion,
                                            result: SearchResult,
                                            responseInfo: ResponseInfo,
                                        ) {
                                            Log.d(TAG, "Destination selected: ${result.name} @ ${result.coordinate}")
                                            onDestinationSelected(result.coordinate, result.name)
                                            query = result.name
                                            suggestions = emptyList()
                                        }

                                        override fun onResults(
                                            suggestion: SearchSuggestion,
                                            results: List<SearchResult>,
                                            responseInfo: ResponseInfo,
                                        ) {
                                            // Category/brand suggestion resolved into multiple
                                            // places — take the closest (first, per
                                            // SearchOptions.proximity ordering) as the destination.
                                            val result = results.firstOrNull() ?: return
                                            Log.d(
                                                TAG,
                                                "Destination selected (category): ${result.name} @ ${result.coordinate}",
                                            )
                                            onDestinationSelected(result.coordinate, result.name)
                                            query = result.name
                                            suggestions = emptyList()
                                        }

                                        override fun onSuggestions(
                                            newSuggestions: List<SearchSuggestion>,
                                            responseInfo: ResponseInfo,
                                        ) {
                                            // "Query" suggestion (e.g. "restaurants near me")
                                            // expands into further suggestions rather than a
                                            // final place — show them instead of selecting.
                                            suggestions = newSuggestions
                                        }

                                        override fun onError(e: Exception) {
                                            Log.e(TAG, "Search selection request failed", e)
                                        }
                                    },
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(text = suggestion.name, color = CarColors.Text, fontSize = 15.sp)
                            val subtitle = suggestion.descriptionText ?: suggestion.fullAddress
                            if (subtitle != null) {
                                Text(text = subtitle, color = CarColors.Text3, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
