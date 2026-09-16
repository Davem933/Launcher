// SearchResultType (deprecated in favor of NewSearchResultType) is still the only public way to
// pass a type into HistoryRecord's constructor from outside the Search SDK module — the
// non-deprecated overload needs an internal-only String mapping. Matches the SDK's own
// HistoryRecord.kt, which carries the identical file-level suppress for the same reason.
@file:Suppress("DEPRECATION")

package com.example.carlauncher.ui.map

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.ui.theme.CarColors
import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.CategorySearchOptions
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.ServiceProvider
import com.mapbox.search.common.CompletionCallback
import com.mapbox.search.record.HistoryRecord
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchResultType
import com.mapbox.search.result.SearchSuggestion
import kotlinx.coroutines.delay
import java.util.Locale

private const val TAG = "SearchOverlay"
private const val MIN_QUERY_LENGTH = 2
private const val SEARCH_DEBOUNCE_MS = 300L
private const val MAX_RECENT_SEARCHES = 8
private const val CATEGORY_RESULT_LIMIT = 12

/**
 * Full-panel search takeover (fills the whole map panel, matching the reference app's
 * celoobrazovkové vyhledávání), replacing the compact [DestinationSearchBar] button while open.
 *
 * Three content states below the search field, mutually exclusive:
 *  - query blank, no category active -> POI category chips + recent searches
 *    ([com.mapbox.search.record.HistoryDataProvider], the Search SDK's own disk-persisted
 *    history store — reused rather than building a parallel DataStore).
 *  - query blank, category active -> dedicated category sub-screen (Autozen's "zoom out +
 *    numbered pins" convention): a header (category name) and a numbered result list float over
 *    the real map, which is NOT covered by this composable's own background in this state —
 *    [onCategoryResultsChanged] reports the current results so [MapWidget] can draw matching
 *    numbered pins and fit the camera on the SAME map instance, since this composable has no
 *    map of its own. [SearchEngine.search] with [CategorySearchOptions] resolves [SearchResult]s
 *    directly, no suggestion->select step needed.
 *  - query non-blank -> live suggestions, same two-step suggest->select flow as before.
 *
 * Voice input delegates to the system speech-recognizer app via
 * [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] rather than the raw `SpeechRecognizer` API — no new
 * `RECORD_AUDIO` permission needed in this app's manifest, the separate recognizer app handles it.
 */
@Composable
fun SearchOverlay(
    currentLocation: VehicleDisplayLocation?,
    onDestinationSelected: (Point, String) -> Unit,
    onDismiss: () -> Unit,
    onCategoryResultsChanged: (List<SearchResult>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val searchEngine = remember {
        SearchEngine.createSearchEngine(
            apiType = ApiType.SEARCH_BOX,
            settings = SearchEngineSettings(),
        )
    }
    val historyDataProvider = remember { ServiceProvider.INSTANCE.historyDataProvider() }

    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var activeCategory by remember { mutableStateOf<SearchCategory?>(null) }
    var categoryResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var recentSearches by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }

    val latestLocation by rememberUpdatedState(currentLocation)
    val focusRequester = remember { FocusRequester() }

    // Guarantees the map's numbered pins never outlive this overlay, regardless of which exit
    // path closed it (selecting a result, tapping back from the category screen straight to
    // dismiss, or the parent hiding it because navigation started) — this is the single, always-
    // run cleanup; transitions that stay within the overlay (category -> browse) clear separately.
    DisposableEffect(Unit) {
        onDispose { onCategoryResultsChanged(emptyList()) }
    }

    LaunchedEffect(Unit) {
        historyDataProvider.getAll(object : CompletionCallback<List<HistoryRecord>> {
            override fun onComplete(result: List<HistoryRecord>) {
                recentSearches = result.sortedByDescending { it.timestamp }.take(MAX_RECENT_SEARCHES)
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "Failed to load recent searches", e)
            }
        })
        focusRequester.requestFocus()
    }

    // Typing exits category-browse mode — free-text query and category results are mutually
    // exclusive content states.
    LaunchedEffect(query) {
        if (query.isNotEmpty() && activeCategory != null) {
            activeCategory = null
            onCategoryResultsChanged(emptyList())
        }
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

    fun rememberInHistory(
        id: String,
        name: String,
        descriptionText: String?,
        address: com.mapbox.search.result.SearchAddress?,
        routablePoints: List<com.mapbox.search.common.RoutablePoint>?,
        categories: List<String>?,
        makiIcon: String?,
        coordinate: Point,
        type: SearchResultType,
        metadata: com.mapbox.search.SearchResultMetadata?,
    ) {
        @Suppress("DEPRECATION") // Non-deprecated ctor needs an internal-only newType mapping we
        // can't call from outside the SDK module — this is the SDK's own sanctioned public path
        // (mirrors HistoryRecordsSerializer.kt's own file-level @Suppress("DEPRECATION")).
        val record = HistoryRecord(
            id = id,
            name = name,
            descriptionText = descriptionText,
            address = address,
            routablePoints = routablePoints,
            categories = categories,
            makiIcon = makiIcon,
            coordinate = coordinate,
            type = type,
            metadata = metadata,
            timestamp = System.currentTimeMillis(),
        )
        historyDataProvider.upsert(record, object : CompletionCallback<Unit> {
            override fun onComplete(result: Unit) {}
            override fun onError(e: Exception) {
                Log.e(TAG, "Failed to store recent search", e)
            }
        })
    }

    fun selectResult(result: SearchResult) {
        rememberInHistory(
            id = result.id,
            name = result.name,
            descriptionText = result.descriptionText,
            address = result.address,
            routablePoints = result.routablePoints,
            categories = result.categories,
            makiIcon = result.makiIcon,
            coordinate = result.coordinate,
            type = result.types.firstOrNull() ?: SearchResultType.POI,
            metadata = result.metadata,
        )
        onDestinationSelected(result.coordinate, result.name)
        onDismiss()
    }

    fun selectRecent(record: HistoryRecord) {
        // Re-selecting a recent item just bumps its timestamp — upsert dedups by id, so this
        // moves it back to the top of the list next time the overlay opens instead of duplicating.
        historyDataProvider.upsert(
            record.copy(timestamp = System.currentTimeMillis()),
            object : CompletionCallback<Unit> {
                override fun onComplete(result: Unit) {}
                override fun onError(e: Exception) {
                    Log.e(TAG, "Failed to refresh recent search", e)
                }
            },
        )
        onDestinationSelected(record.coordinate, record.name)
        onDismiss()
    }

    fun runCategorySearch(category: SearchCategory) {
        activeCategory = category
        categoryResults = emptyList()
        searchEngine.search(
            categoryName = category.mapboxCategoryName,
            options = CategorySearchOptions(
                proximity = latestLocation?.let { Point.fromLngLat(it.lng, it.lat) },
                limit = CATEGORY_RESULT_LIMIT,
            ),
            callback = object : SearchCallback {
                override fun onResults(results: List<SearchResult>, responseInfo: ResponseInfo) {
                    categoryResults = results
                    onCategoryResultsChanged(results)
                }

                override fun onError(e: Exception) {
                    Log.e(TAG, "Category search failed for ${category.mapboxCategoryName}", e)
                    categoryResults = emptyList()
                    onCategoryResultsChanged(emptyList())
                }
            },
        )
    }

    fun exitCategory() {
        activeCategory = null
        onCategoryResultsChanged(emptyList())
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                query = spoken
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val category = activeCategory
        if (category != null) {
            // Category-results sub-screen (Autozen convention): only this card has a background —
            // the rest of the Box is transparent, so the real map (already rendered behind this
            // whole overlay in MapWidget) shows through with the numbered pins MapWidget just drew
            // from onCategoryResultsChanged. Back returns to the search/category browse screen,
            // it does not close the overlay (spec: "samostatná obrazovka jako Autozen").
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(CarColors.Bg)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { exitCategory() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zpět",
                            tint = CarColors.Text,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category.label,
                        color = CarColors.Text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(top = 8.dp)
                ) {
                    itemsIndexed(categoryResults) { index, result ->
                        NumberedResultRow(
                            number = index + 1,
                            title = result.name,
                            subtitle = result.descriptionText ?: result.fullAddress,
                            onClick = { selectResult(result) },
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CarColors.Bg)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zpět",
                            tint = CarColors.Text,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(CarColors.Surface)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = TextStyle(color = CarColors.Text, fontSize = 16.sp),
                            cursorBrush = SolidColor(CarColors.Text),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text("Hledat", color = CarColors.Text3, fontSize = 16.sp)
                                }
                                innerTextField()
                            },
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = {
                        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Kam jedeme?")
                        }
                        try {
                            voiceLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e(TAG, "No speech recognizer app available on this device", e)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Hlasové vyhledávání",
                            tint = CarColors.Text3,
                        )
                    }
                }

                Spacer(Modifier.width(0.dp).padding(top = 12.dp))

                if (query.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        items(suggestions) { suggestion ->
                            SearchResultRow(
                                title = suggestion.name,
                                subtitle = suggestion.descriptionText ?: suggestion.fullAddress,
                                onClick = {
                                    searchEngine.select(
                                        suggestion = suggestion,
                                        callback = object : SearchSelectionCallback {
                                            override fun onResult(
                                                suggestion: SearchSuggestion,
                                                result: SearchResult,
                                                responseInfo: ResponseInfo,
                                            ) {
                                                selectResult(result)
                                            }

                                            override fun onResults(
                                                suggestion: SearchSuggestion,
                                                results: List<SearchResult>,
                                                responseInfo: ResponseInfo,
                                            ) {
                                                val result = results.firstOrNull() ?: return
                                                selectResult(result)
                                            }

                                            override fun onSuggestions(
                                                newSuggestions: List<SearchSuggestion>,
                                                responseInfo: ResponseInfo,
                                            ) {
                                                suggestions = newSuggestions
                                            }

                                            override fun onError(e: Exception) {
                                                Log.e(TAG, "Search selection request failed", e)
                                            }
                                        },
                                    )
                                },
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            SearchCategory.entries.forEach { entry ->
                                CategoryChip(
                                    category = entry,
                                    selected = false,
                                    onClick = { runCategorySearch(entry) },
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            if (recentSearches.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Nedávná hledání",
                                        color = CarColors.Text2,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                            }
                            items(recentSearches) { record ->
                                SearchResultRow(
                                    title = record.name,
                                    subtitle = record.descriptionText ?: record.address?.formattedAddress(),
                                    icon = Icons.Default.History,
                                    onClick = { selectRecent(record) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: SearchCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) CarColors.Accent else CarColors.Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (selected) CarColors.Bg else CarColors.Text,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = category.label,
            color = if (selected) CarColors.Bg else CarColors.Text,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun NumberedResultRow(
    number: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(CarColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), color = CarColors.Bg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = title, color = CarColors.Text, fontSize = 15.sp)
            if (subtitle != null) {
                Text(text = subtitle, color = CarColors.Text3, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = CarColors.Text3)
            Spacer(Modifier.width(14.dp))
        }
        Column {
            Text(text = title, color = CarColors.Text, fontSize = 15.sp)
            if (subtitle != null) {
                Text(text = subtitle, color = CarColors.Text3, fontSize = 12.sp)
            }
        }
    }
}

private fun com.mapbox.search.result.SearchAddress.formattedAddress(): String? =
    listOfNotNull(street, houseNumber, place).takeIf { it.isNotEmpty() }?.joinToString(" ")
