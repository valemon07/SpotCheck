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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.avalemon.spotcheck.data.Building
import com.avalemon.spotcheck.data.Category
import com.avalemon.spotcheck.data.Facility
import com.avalemon.spotcheck.data.RatingSummary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.launch

/** Roughly the middle of Central Grounds; the map opens here until we have a nearby fix. */
private val GroundsCenter = LatLng(38.0345, -78.5075)
private const val GROUNDS_ZOOM = 15f

/** Only jump to the user's position when they're actually near Grounds (emulators default to California). */
private const val NEARBY_METERS = 3_000f

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
        val meters = FloatArray(1).also {
            Location.distanceBetween(here.latitude, here.longitude, GroundsCenter.latitude, GroundsCenter.longitude, it)
        }[0]
        if (!centeredOnUser && meters < NEARBY_METERS) {
            centeredOnUser = true
            camera.animate(CameraUpdateFactory.newLatLngZoom(here, 16f))
        }
    }

    val pins: List<Pair<Building, Int>> = remember(catalog, vm.category) {
        catalog.buildings.values
            .filter { it.latitude != null && it.longitude != null }
            .map { b -> b to catalog.facilities.count { it.buildingId == b.id && (vm.category == null || it.category == vm.category) } }
            .filter { (_, count) -> count > 0 }
    }

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
                    val position = LatLng(b.latitude!!, b.longitude!!)
                    val isSelected = b.id == vm.buildingId
                    Marker(
                        state = rememberUpdatedMarkerState(position),
                        title = b.name,
                        contentDescription = "${b.name}, ${facilityCount(count)}",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (isSelected) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_ORANGE,
                        ),
                        zIndex = if (isSelected) 1f else 0f,
                        onClick = {
                            vm.onBuilding(b.id)
                            scope.launch { camera.animate(CameraUpdateFactory.newLatLng(position)) }
                            true // Suppress the default info window; the panel shows the details.
                        },
                    )
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
