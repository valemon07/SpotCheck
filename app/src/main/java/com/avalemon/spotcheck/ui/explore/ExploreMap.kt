package com.avalemon.spotcheck.ui.explore

import androidx.compose.ui.res.painterResource
import com.avalemon.spotcheck.R
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avalemon.spotcheck.data.Building
import com.avalemon.spotcheck.data.Catalog
import com.avalemon.spotcheck.data.ExploreView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
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

/** Material "Place" icon tinted from the theme; the selected building's pin is larger and tertiary. */
@Composable
@GoogleMapComposable
private fun BuildingMarker(
    building: Building,
    count: Int,
    selected: Boolean,
    colors: PinColors,
    onClick: () -> Unit,
) {
    val tint = if (selected) colors.selected else colors.normal
    val size = if (selected) 48.dp else 36.dp
    MarkerComposable(
        building.id, selected, tint, size,
        state = rememberUpdatedMarkerState(LatLng(building.latitude!!, building.longitude!!)),
        title = building.name,
        contentDescription = "${building.name}, ${facilityCount(count)}",
        anchor = Offset(0.5f, 0.92f), // Tip of the glyph, not the icon's bottom padding.
        zIndex = if (selected) 1f else 0f,
        onClick = { onClick(); true }, // true suppresses the default info window.
    ) {
        Icon(painterResource(R.drawable.ic_location_on), contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * The one interactive map of Grounds behind Explore's bottom sheet: one pin per building with
 * facilities in the active category. [bottomInset] is the part covered by the sheet, so the
 * camera center, Google logo and my-location button stay in the visible area. In list mode the
 * visible strip acts as a preview: tapping it (or a pin) switches to the full map.
 */
@Composable
fun ExploreMap(vm: ExploreViewModel, bottomInset: Dp) {
    val catalog = vm.catalog
    val scope = rememberCoroutineScope()
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(GroundsCenter, GROUNDS_ZOOM)
    }
    val mapMode = vm.view == ExploreView.MAP

    // Center on the user once per Explore session, and only if they're on or near Grounds.
    var centeredOnUser by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.userLocation) {
        val here = vm.userLocation ?: return@LaunchedEffect
        if (!centeredOnUser && mapCenter(here) == here) {
            centeredOnUser = true
            camera.animate(CameraUpdateFactory.newLatLngZoom(here, 16f))
        }
    }

    val pins = remember(catalog, vm.category) { catalog?.let { buildingPins(it, vm.category) }.orEmpty() }
    val colors = PinColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    BackHandler(enabled = mapMode && vm.buildingId != null) { vm.onBuilding(null) }

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
        contentPadding = PaddingValues(bottom = bottomInset),
        onMapClick = { if (mapMode) vm.onBuilding(null) else vm.onView(ExploreView.MAP) },
    ) {
        pins.forEach { (b, count) ->
            key(b.id) {
                BuildingMarker(b, count, selected = b.id == vm.buildingId, colors = colors) {
                    vm.onBuilding(b.id)
                    vm.onView(ExploreView.MAP)
                    scope.launch { camera.animate(CameraUpdateFactory.newLatLng(LatLng(b.latitude!!, b.longitude!!))) }
                }
            }
        }
    }
}

internal fun facilityCount(n: Int) = if (n == 1) "1 facility" else "$n facilities"
