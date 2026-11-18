package com.avalemon.spotcheck.ui.details

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avalemon.spotcheck.data.Category
import com.avalemon.spotcheck.data.Review
import com.avalemon.spotcheck.ui.components.LoadingState
import com.avalemon.spotcheck.ui.components.MessageState
import com.avalemon.spotcheck.ui.components.StarRow
import com.avalemon.spotcheck.ui.components.formatAverage
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onWriteReview: (buildingId: String, facilityId: String) -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    vm: DetailsViewModel = viewModel(factory = DetailsViewModel.Factory),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(vm.facility?.name ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(vm.building?.name ?: "") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            val text = vm.shareText() ?: return@IconButton
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SpotCheck recommendation")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Share facility"))
                        },
                        enabled = vm.facility != null,
                    ) { Icon(Icons.Rounded.Share, "Share facility") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val facility = vm.facility
        when {
            facility == null && vm.error != null -> MessageState(
                Icons.Outlined.ErrorOutline, "Something went wrong", vm.error!!,
                modifier = Modifier.padding(padding), actionLabel = "Retry", onAction = { vm.load() },
            )
            facility == null -> LoadingState(Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            facility.locator.ifBlank { facility.floor?.let { "Floor $it" }.orEmpty() },
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (facility.description.isNotBlank()) {
                            Text(
                                facility.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Non-interactive tag (a chip would read as a button that does nothing).
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                Category.labelFor(facility.category),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                item { SummaryCard(vm) }
                item {
                    MyReviewSection(
                        review = vm.myReview,
                        loaded = vm.reviews != null,
                        deleting = vm.deleting,
                        onWrite = { onWriteReview(vm.buildingId, vm.facilityId) },
                        onDelete = { confirmDelete = true },
                    )
                }
                item {
                    Text(
                        "Community reviews",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (vm.reviews != null && vm.otherReviews.isEmpty()) {
                    item {
                        Text(
                            "No reviews from other students yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(vm.otherReviews, key = { it.userId }) { ReviewCard(it) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Delete your review?") },
            text = { Text("This removes your rating and comment for this facility.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteMyReview() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryCard(vm: DetailsViewModel) {
    val s = vm.summary
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.reviews == null) {
                Text("Loading rating…", style = MaterialTheme.typography.titleMedium)
            } else if (s.average == null) {
                Column {
                    Text("No reviews", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text("Be the first to rate this spot.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(formatAverage(s.average), style = MaterialTheme.typography.displayMediumEmphasized)
                Column(Modifier.padding(start = 16.dp)) {
                    StarRow(s.average, starSize = 22.dp, tint = LocalContentColor.current)
                    Text("${s.count} review${if (s.count == 1L) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MyReviewSection(
    review: Review?,
    loaded: Boolean,
    deleting: Boolean,
    onWrite: () -> Unit,
    onDelete: () -> Unit,
) {
    if (!loaded) return
    if (review == null) {
        Button(onClick = onWrite, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.RateReview, null)
            Text("Write a review", Modifier.padding(start = 8.dp))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your review", style = MaterialTheme.typography.titleLargeEmphasized)
        ReviewCard(review, highlight = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onWrite, enabled = !deleting, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Edit, null)
                Text("Edit", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onDelete, enabled = !deleting, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Delete, null)
                Text(if (deleting) "Deleting…" else "Delete", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ReviewCard(review: Review, highlight: Boolean = false) {
    val date = review.updatedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Reviews are anonymous: never show another student's name.
                Text(if (highlight) "You" else "Anonymous", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (date != null) {
                    Text(
                        if (review.isEdited) "Edited $date" else date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StarRow(review.rating.toDouble())
            if (review.comment.isNotBlank()) Text(review.comment, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
