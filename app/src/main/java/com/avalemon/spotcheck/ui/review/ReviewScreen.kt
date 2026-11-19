package com.avalemon.spotcheck.ui.review

import androidx.compose.ui.res.painterResource
import com.avalemon.spotcheck.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avalemon.spotcheck.data.ReviewRepository
import com.avalemon.spotcheck.data.locationLine
import com.avalemon.spotcheck.ui.components.LoadingState
import com.avalemon.spotcheck.ui.components.MessageState
import com.avalemon.spotcheck.ui.components.StarRatingInput

private val ratingWords = listOf("", "Poor", "Fair", "Good", "Very good", "Excellent")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewScreen(
    onClose: () -> Unit,
    onSaved: (message: String) -> Unit,
    vm: ReviewViewModel = viewModel(factory = ReviewViewModel.Factory),
) {
    LaunchedEffect(vm.done) { if (vm.done) onSaved(if (vm.isEditing) "Review updated" else "Review posted") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(if (vm.isEditing) "Edit review" else "Write a review") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), "Back without saving")
                    }
                },
            )
        },
    ) { padding ->
        val catalog = vm.catalog
        when {
            catalog == null && vm.loadError != null -> MessageState(
                painterResource(R.drawable.ic_cloud_off), "Couldn't load facilities", vm.loadError!!,
                modifier = Modifier.padding(padding), actionLabel = "Retry", onAction = { vm.loadCatalog() },
            )
            catalog == null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (vm.facilityLocked) {
                    vm.facility?.let { f ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Reviewing", style = MaterialTheme.typography.labelLarge)
                                Text(f.name, style = MaterialTheme.typography.titleLargeEmphasized)
                                Text(f.locationLine(catalog.buildingName(f)), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    Picker(
                        label = "Building",
                        selected = vm.buildingId?.let { catalog.buildings[it]?.name },
                        options = catalog.buildings.values.sortedBy { it.name },
                        optionText = { it.name },
                        onSelect = { vm.onBuilding(it.id) },
                        enabled = !vm.saving,
                    )
                    Picker(
                        label = "Facility",
                        selected = vm.facility?.let { f -> listOf(f.name, f.locator).filter { it.isNotBlank() }.joinToString(" · ") },
                        options = vm.facilitiesInBuilding,
                        optionText = { f -> listOf(f.name, f.locator).filter { it.isNotBlank() }.joinToString(" · ") },
                        onSelect = vm::onFacility,
                        enabled = !vm.saving && vm.buildingId != null,
                        supporting = if (vm.buildingId == null) "Choose a building first" else null,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your rating", style = MaterialTheme.typography.titleMedium)
                    StarRatingInput(vm.rating, vm::onRating, enabled = !vm.saving)
                    Text(
                        if (vm.rating == 0) "Required — tap a star" else ratingWords[vm.rating],
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (vm.rating == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                    )
                }

                OutlinedTextField(
                    value = vm.comment,
                    onValueChange = vm::onComment,
                    label = { Text("Comment (optional)") },
                    placeholder = { Text("Noise level, outlets, cleanliness, crowding…") },
                    supportingText = { Text("${vm.comment.length} / ${ReviewRepository.MAX_COMMENT}") },
                    enabled = !vm.saving,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                vm.saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = vm::submit,
                    enabled = vm.canSubmit,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (vm.saving) {
                        LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (vm.isEditing) "Save changes" else "Post review", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> Picker(
    label: String,
    selected: String?,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    supporting: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Select ${label.lowercase()}") },
            supportingText = supporting?.let { { Text(it) } },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so the whole field opens the menu (read-only fields swallow taps otherwise).
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = "Choose ${label.lowercase()}") {
                    expanded = true
                },
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(optionText(opt)) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}
