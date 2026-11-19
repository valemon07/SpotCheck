package com.avalemon.spotcheck.ui.explore

import androidx.compose.ui.res.painterResource
import com.avalemon.spotcheck.R
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avalemon.spotcheck.data.Category
import com.avalemon.spotcheck.data.ExploreView
import com.avalemon.spotcheck.data.Facility
import com.avalemon.spotcheck.data.RatingSummary
import com.avalemon.spotcheck.data.locationLine
import com.avalemon.spotcheck.ui.components.LoadingState
import com.avalemon.spotcheck.ui.components.MessageState
import com.avalemon.spotcheck.ui.components.RatingBadge
import kotlinx.coroutines.launch

/**
 * Explore is one live map with the facility list in a standard bottom sheet on top of it.
 * List mode = sheet expanded, leaving a strip of map at the top as a preview; map mode = sheet
 * collapsed to its filters. The filters live in the sheet, so they work in both modes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExploreScreen(
    onOpenFacility: (Facility) -> Unit,
    onWriteReview: (buildingId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    vm: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onMessageShown() } }

    // Location: ask once per Explore session; browsing works the same without it.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askedLocation by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        askedLocation = true
        val granted = result.values.any { it }
        vm.onLocationPermission(granted)
        if (!granted) scope.launch {
            val r = snackbar.showSnackbar(
                "Location is off. You can still browse all of Grounds.",
                actionLabel = "Settings",
                withDismissAction = true,
            )
            if (r == SnackbarResult.ActionPerformed) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }
        }
    }
    fun requestLocation() {
        askedLocation = true // Set first: the dialog pauses/resumes Explore before the result arrives.
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    // Refresh averages (and location) whenever Explore comes back into view, e.g. after saving a
    // review or returning from system settings. Rating refresh is a no-op until the catalog loads.
    LifecycleResumeEffect(Unit) {
        vm.refreshRatings()
        val granted = vm.checkLocationPermission()
        when {
            granted || askedLocation -> vm.onLocationPermission(granted)
            else -> requestLocation()
        }
        onPauseOrDispose { }
    }

    // Search mode swaps the title bar for a search field and shows the list.
    var searching by rememberSaveable { mutableStateOf(vm.query.isNotEmpty()) }
    fun openSearch() { searching = true; vm.onView(ExploreView.LIST) }
    fun closeSearch() { searching = false; vm.onQuery("") }
    BackHandler(enabled = searching) { closeSearch() }
    // Map mode → back returns to the list. ExploreMap's own handler (clear selection) is composed
    // later, so it wins while a building is selected.
    BackHandler(enabled = vm.view == ExploreView.MAP && !searching) { vm.onView(ExploreView.LIST) }

    // Keep the sheet and vm.view in sync both ways: the toggle/back/pins drive the sheet, and
    // dragging the sheet updates the view (which is what rotation and DataStore defaults restore).
    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (vm.view == ExploreView.LIST) SheetValue.Expanded else SheetValue.PartiallyExpanded,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    LaunchedEffect(vm.view) {
        when (vm.view) {
            ExploreView.LIST -> if (sheetState.currentValue != SheetValue.Expanded) sheetState.expand()
            ExploreView.MAP -> if (sheetState.currentValue != SheetValue.PartiallyExpanded) sheetState.partialExpand()
        }
    }
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }.collect { value ->
            val view = if (value == SheetValue.Expanded) ExploreView.LIST else ExploreView.MAP
            if (vm.view != view) vm.onView(view)
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            AnimatedContent(targetState = searching, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "topBar") { isSearching ->
                if (isSearching) {
                    SearchTopBar(query = vm.query, onQuery = vm::onQuery, onClose = ::closeSearch)
                } else {
                    TopAppBar(
                        title = { Text("Explore") },
                        actions = {
                            if (!vm.hasLocationPermission) {
                                IconButton(onClick = ::requestLocation, shapes = IconButtonDefaults.shapes()) {
                                    Icon(painterResource(R.drawable.ic_location_on), contentDescription = "Use my location")
                                }
                            }
                            IconButton(onClick = ::openSearch, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
                            }
                            IconButton(
                                onClick = { vm.onView(if (vm.view == ExploreView.LIST) ExploreView.MAP else ExploreView.LIST) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    if (vm.view == ExploreView.LIST) painterResource(R.drawable.ic_map) else painterResource(R.drawable.ic_list),
                                    contentDescription = if (vm.view == ExploreView.LIST) "Show map" else "Show list",
                                )
                            }
                            IconButton(onClick = { vm.refresh() }, enabled = !vm.refreshing, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh")
                            }
                            IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            // Shrinks to an icon while the list is scrolled; the label stays as the accessible name.
            // List mode only: in map mode it would sit on top of the sheet's filter chips.
            if (vm.view == ExploreView.LIST) SmallExtendedFloatingActionButton(
                onClick = { onWriteReview(vm.buildingId) },
                expanded = !listState.canScrollBackward,
                icon = { Icon(painterResource(R.drawable.ic_rate_review), contentDescription = "Write a review") },
                text = { Text("Write a review") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            // Expanded sheet always leaves this much map visible: the "preview card" of list mode.
            val mapStrip = min(200.dp, maxHeight * 0.35f)
            val sheetHeight = maxHeight - mapStrip
            val dragHandleHeight = 48.dp // BottomSheetDefaults.DragHandle: 4dp bar + 22dp padding each side.
            val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val peekHeight = navBar + if (vm.view == ExploreView.MAP && vm.buildingId != null) maxHeight * 0.45f else 140.dp
            val bottomInset = if (sheetState.currentValue == SheetValue.Expanded) sheetHeight else peekHeight

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = peekHeight,
                // Plain surface so the surfaceContainerLow facility cards stand out, as before.
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetContent = {
                    ExploreSheet(vm, listState, showFilters = !searching, onOpenFacility, Modifier.height(sheetHeight - dragHandleHeight))
                },
            ) {
                ExploreMap(vm, bottomInset)
            }
        }
    }
}

/** Sheet content: filters, a count/building header, then the facility list (or load/error states). */
@Composable
private fun ExploreSheet(
    vm: ExploreViewModel,
    listState: LazyListState,
    showFilters: Boolean,
    onOpenFacility: (Facility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        val catalog = vm.catalog
        when {
            vm.loading -> LoadingState(Modifier.height(200.dp))
            catalog == null -> MessageState(
                painterResource(R.drawable.ic_cloud_off), "Couldn't load facilities", vm.error ?: "",
                actionLabel = "Retry", onAction = { vm.retry() },
            )
            else -> {
                if (showFilters) FilterRow(vm)
                SheetHeader(vm)
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (vm.visibleFacilities.isEmpty()) {
                        item(key = "empty") {
                            MessageState(
                                painterResource(R.drawable.ic_search_off),
                                if (catalog.facilities.isEmpty()) "No facilities yet" else "No matches",
                                if (catalog.facilities.isEmpty()) "The catalog is empty. Refresh once facilities are added."
                                else "Try a different search, building or category.",
                                modifier = Modifier.height(280.dp),
                            )
                        }
                    }
                    items(vm.visibleFacilities, key = { it.key }) { f ->
                        FacilityRow(
                            facility = f,
                            buildingName = catalog.buildingName(f),
                            summary = vm.summaries[f.key] ?: if (vm.summaries.isEmpty()) null else RatingSummary.Empty,
                            distance = vm.distanceLabel(f),
                            onClick = { onOpenFacility(f) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** "12 facilities", or "Rotunda · 4 facilities · 480 ft" with a clear button when a building is picked. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetHeader(vm: ExploreViewModel) {
    val building = vm.buildingId?.let { vm.catalog?.buildings?.get(it) }
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOfNotNull(building?.name, facilityCount(vm.visibleFacilities.size), vm.distanceLabel(building))
                .joinToString(" · "),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (building != null) {
            IconButton(onClick = { vm.onBuilding(null) }, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Show all buildings")
            }
        }
    }
}

/** Contextual search bar that replaces the Explore title while searching. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search buildings or facilities") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus),
            )
        },
    )
}

/** Sort, category and building share one control style: a dropdown chip showing its current value. */
@Composable
private fun FilterRow(vm: ExploreViewModel) {
    val catalog = vm.catalog ?: return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 2.dp),
    ) {
        item {
            DropdownChip(
                label = vm.effectiveSort.label,
                active = false,
                leadingIcon = { Icon(painterResource(R.drawable.ic_sort), null, Modifier.size(FilterChipDefaults.IconSize)) },
                options = vm.sortOptions,
                selected = vm.effectiveSort,
                optionText = { it.label },
                onSelect = vm::onSort,
                description = "Sort by",
            )
        }
        item {
            DropdownChip(
                label = vm.category?.let(Category::labelFor) ?: "Category",
                active = vm.category != null,
                options = listOf<String?>(null) + vm.categories,
                selected = vm.category,
                optionText = { it?.let(Category::labelFor) ?: "All categories" },
                onSelect = vm::onCategory,
                description = "Category filter",
            )
        }
        item {
            DropdownChip(
                label = vm.buildingId?.let { catalog.buildings[it]?.name ?: it } ?: "Building",
                active = vm.buildingId != null,
                options = listOf<String?>(null) + catalog.buildings.values.sortedBy { it.name }.map { it.id },
                selected = vm.buildingId,
                optionText = { id -> id?.let { catalog.buildings[it]?.name ?: it } ?: "All buildings" },
                onSelect = vm::onBuilding,
                description = "Building filter",
            )
        }
    }
}

@Composable
private fun <T> DropdownChip(
    label: String,
    active: Boolean,
    options: List<T>,
    selected: T,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    description: String,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = if (active) {
                { Icon(painterResource(R.drawable.ic_check), null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else leadingIcon,
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = description) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionText(opt)) },
                    onClick = { onSelect(opt); expanded = false },
                    trailingIcon = if (opt == selected) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected") }) else null,
                )
            }
        }
    }
}

@Composable
fun FacilityRow(
    facility: Facility,
    buildingName: String,
    summary: RatingSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distance: String? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(facility.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(facility.locationLine(buildingName), distance).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Category.labelFor(facility.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            RatingBadge(summary)
        }
    }
}
