package com.avalemon.spotcheck.ui.explore

import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.avalemon.spotcheck.data.Building
import com.avalemon.spotcheck.data.Catalog
import com.avalemon.spotcheck.data.Category
import com.avalemon.spotcheck.data.Facility
import com.avalemon.spotcheck.data.RatingSummary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch

/** Roughly the middle of Central Grounds; the map opens here until we have a nearby fix. */
private val GroundsCenter = LatLng(38.0345, -78.5075)
private const val GROUNDS_ZOOM = 15f

/** Only jump to the user's position when they're actually near Grounds (emulators default to California). */
private const val NEARBY_METERS = 3_000f

/** Buildings with coordinates and at least one facility in [category], paired with that count. */
private fun buildingPins(catalog: Catalog, category: String?): List<Pair<Building, Int>> =
    catalog.buildings.values
        .filter { it.latitude != null && it.longitude != null }
        .map { b -> b to catalog.facilities.count { it.buildingId == b.id && (category == null || it.category == category) } }
        .filter { (_, count) -> count > 0 }

/** The user's position when they're on or near Grounds; otherwise Central Grounds. */
private fun mapCenter(userLocation: LatLng?): LatLng {
    val here = userLocation ?: return GroundsCenter
    val meters = FloatArray(1).also {
        Location.distanceBetween(here.latitude, here.longitude, GroundsCenter.latitude, GroundsCenter.longitude, it)
    }[0]
    return if (meters < NEARBY_METERS) here else GroundsCenter
}

private data class PinColors(val normal: Color, val selected: Color)

@Composable
private fun pinColors() = PinColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)

/** Material "Place" icon tinted from the theme; the selected building's pin is larger and tertiary. */
@Composable
@GoogleMapComposable
private fun BuildingMarker(
    building: Building,
    count: Int,
    selected: Boolean,
    colors: PinColors,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val size = when {
        compact -> if (selected) 32.dp else 24.dp
        else -> if (selected) 48.dp else 36.dp
    }
    val tint = if (selected) colors.selected else colors.normal
    MarkerComposable(
        building.id, selected, tint, size,
        state = rememberUpdatedMarkerState(LatLng(building.latitude!!, building.longitude!!)),
        title = building.name,
        contentDescription = "${building.name}, ${facilityCount(count)}",
        anchor = Offset(0.5f, 0.92f), // Tip of the glyph, not the icon's bottom padding.
        zIndex = if (selected) 1f else 0f,
        onClick = { onClick(); true }, // true suppresses the default info window.
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * Interactive map of Grounds: one pin per building that has facilities in the active category.
 * Tapping a pin selects the building (shared with the list's building filter) and shows its
 * floor-specific facilities in a panel at the bottom.
 */
@Composable
fun ExploreMap(vm: ExploreViewModel, onOpenFacility: (Facility) -> Unit) {
    val catalog = vm.catalog ?: return
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(GroundsCenter, GROUNDS_ZOOM)
    }

    // Center on the user once per Explore session, and only if they're on or near Grounds.
    var centeredOnUser by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.userLocation) {
        val here = vm.userLocation ?: return@LaunchedEffect
        if (!centeredOnUser && mapCenter(here) == here) {
            centeredOnUser = true
            camera.animate(CameraUpdateFactory.newLatLngZoom(here, 16f))
        }
    }

    val pins = remember(catalog, vm.category) { buildingPins(catalog, vm.category) }
    val colors = pinColors()

    val selected = vm.buildingId?.let { catalog.buildings[it] }
    BackHandler(enabled = selected != null) { vm.onBuilding(null) }

    // Keep the Google logo and my-location button visible above the panel.
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val panelHeight = with(LocalDensity.current) { panelHeightPx.toDp() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Panel never covers more than about half the map, so pins stay usable in landscape.
        val panelMaxHeight = maxHeight * 0.55f
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            contentDescription = "Map of Grounds with building pins",
            properties = MapProperties(isMyLocationEnabled = vm.hasLocationPermission),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = vm.hasLocationPermission,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                indoorLevelPickerEnabled = false,
            ),
            contentPadding = PaddingValues(bottom = if (selected != null) panelHeight else 0.dp),
            onMapClick = { vm.onBuilding(null) },
        ) {
            pins.forEach { (b, count) ->
                key(b.id) {
                    BuildingMarker(b, count, selected = b.id == vm.buildingId, colors = colors) {
                        vm.onBuilding(b.id)
                        scope.launch { camera.animate(CameraUpdateFactory.newLatLng(LatLng(b.latitude!!, b.longitude!!))) }
                    }
                }
            }
        }

        // Remember the last building so the panel keeps its content while animating out.
        var shown by remember { mutableStateOf(selected) }
        if (selected != null) shown = selected
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .heightIn(max = panelMaxHeight)
                .onSizeChanged { panelHeightPx = it.height },
        ) {
            shown?.let { b ->
                BuildingPanel(
                    building = b,
                    facilities = catalog.facilities
                        .filter { it.buildingId == b.id && (vm.category == null || it.category == vm.category) }
                        .sortedWith(compareBy({ it.floor ?: Int.MAX_VALUE }, { it.name })),
                    category = vm.category,
                    distance = vm.distanceLabel(b),
                    summaries = vm.summaries,
                    onClose = { vm.onBuilding(null) },
                    onOpenFacility = onOpenFacility,
                )
            }
        }
    }
}

/**
 * Static (lite-mode) map at the top of the list: centered on the user near Grounds, else Central
 * Grounds, or on the filtered building. The whole card is one button that opens the full map.
 */
@Composable
fun MapPreviewCard(vm: ExploreViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val catalog = vm.catalog ?: return
    val pins = remember(catalog, vm.category) { buildingPins(catalog, vm.category) }
    val colors = pinColors()
    val building = vm.buildingId?.let { catalog.buildings[it] }?.takeIf { it.latitude != null && it.longitude != null }
    val center = building?.let { LatLng(it.latitude!!, it.longitude!!) } ?: mapCenter(vm.userLocation)
    val nearUser = building == null && vm.userLocation != null && center == vm.userLocation
    val label = building?.name ?: if (nearUser) "Near you" else "Central Grounds"

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, GROUNDS_ZOOM)
    }
    // Lite mode can't animate and rounds zoom to whole levels, so just move.
    LaunchedEffect(center) { camera.move(CameraUpdateFactory.newLatLngZoom(center, GROUNDS_ZOOM)) }

    Card(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clearAndSetSemantics {
                contentDescription = "Map of Grounds, ${pins.size} buildings. $label"
                role = Role.Button
                onClick(label = "Open map") { onOpen(); true }
            },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                mergeDescendants = true,
                focusable = false,
                cameraPositionState = camera,
                googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                properties = MapProperties(isMyLocationEnabled = vm.hasLocationPermission),
                uiSettings = MapUiSettings(mapToolbarEnabled = false),
                onMapClick = { onOpen() }, // Lite mode would otherwise open the Google Maps app.
            ) {
                pins.forEach { (b, count) ->
                    key(b.id) { BuildingMarker(b, count, selected = b.id == building?.id, colors = colors, compact = true, onClick = onOpen) }
                }
            }
            // Top corners only: the Google logo (bottom-start) must stay visible.
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 2.dp,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 2.dp,
            ) {
                Icon(Icons.Outlined.OpenInFull, contentDescription = null, modifier = Modifier.padding(8.dp).size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BuildingPanel(
    building: Building,
    facilities: List<Facility>,
    category: String?,
    distance: String?,
    summaries: Map<String, RatingSummary>,
    onClose: () -> Unit,
    onOpenFacility: (Facility) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Apartment, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(building.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(facilityCount(facilities.size), distance).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Close, contentDescription = "Close ${building.name}")
            }
        }
        if (facilities.isEmpty()) {
            Text(
                "No ${category?.let(Category::labelFor)?.lowercase() ?: "facilities"} in this building.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(facilities, key = { it.key }) { f ->
                    FacilityRow(
                        facility = f,
                        buildingName = building.name,
                        summary = summaries[f.key] ?: if (summaries.isEmpty()) null else RatingSummary.Empty,
                        onClick = { onOpenFacility(f) },
                    )
                }
            }
        }
    }
}

private fun facilityCount(n: Int) = if (n == 1) "1 facility" else "$n facilities"
