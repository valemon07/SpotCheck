package com.avalemon.spotcheck.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avalemon.spotcheck.data.RatingSummary
import java.util.Locale

fun formatAverage(avg: Double): String = String.format(Locale.US, "%.1f", avg)

/** Read-only stars (supports halves). Announced as a single phrase for TalkBack. */
@Composable
fun StarRow(rating: Double, modifier: Modifier = Modifier, starSize: Dp = 18.dp) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "${formatAverage(rating)} out of 5 stars" },
    ) {
        for (i in 1..5) {
            val icon: ImageVector = when {
                rating >= i - 0.25 -> Icons.Rounded.Star
                rating >= i - 0.75 -> Icons.AutoMirrored.Outlined.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(starSize))
        }
    }
}

/** Compact rating pill for list rows: "★ 4.3 (12)" or "No reviews". */
@Composable
fun RatingBadge(summary: RatingSummary?, modifier: Modifier = Modifier) {
    val avg = summary?.average
    val hasReviews = summary != null && summary.count > 0 && avg != null
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (hasReviews) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (hasReviews) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (hasReviews) {
                Icon(Icons.Rounded.Star, null, modifier = Modifier.size(16.dp)) // inherits onTertiaryContainer
                Text(
                    "${formatAverage(avg)} (${summary.count})",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "Rated ${formatAverage(avg)} from ${summary.count} reviews"
                    },
                )
            } else {
                Text(if (summary == null) "—" else "No reviews", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Tappable 1–5 star picker with 48dp targets and spoken labels. Stars morph shape when pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StarRatingInput(rating: Int, onRatingChange: (Int) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            IconToggleButton(
                checked = rating >= i,
                onCheckedChange = { onRatingChange(i) },
                enabled = enabled,
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier
                    .size(52.dp)
                    .semantics { contentDescription = if (i == 1) "1 star" else "$i stars" },
            ) {
                Icon(
                    if (rating >= i) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (rating >= i) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

/** Empty / error placeholder with an optional action button. */
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** M3 Expressive connected button group: single-choice toggle buttons that morph shape on selection. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, opt ->
            ToggleButton(
                checked = selected == opt,
                onCheckedChange = { onSelect(opt) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) { Text(label(opt)) }
        }
    }
}
