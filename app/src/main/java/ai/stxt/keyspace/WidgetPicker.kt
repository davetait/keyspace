package ai.stxt.keyspace

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ClipData label marking a drag that carries a widget provider (vs. an app). */
const val WIDGET_CLIP_LABEL = "widget"

/** An installed widget the user can add. Sizes are in dp. */
class WidgetChoice(
    val provider: ComponentName,
    val label: String,
    val appLabel: String,
    val preview: Drawable?,
    val minWidthDp: Float,
    val minHeightDp: Float,
    val targetCellsX: Int,
    val targetCellsY: Int,
)

suspend fun loadWidgetChoices(context: Context): List<WidgetChoice> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    AppWidgetManager.getInstance(context).installedProviders.map { info ->
        val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
            .getOrDefault(info.provider.packageName)
        WidgetChoice(
            provider = info.provider,
            label = info.loadLabel(pm),
            appLabel = appLabel,
            preview = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0),
            minWidthDp = info.minWidth / density,
            minHeightDp = info.minHeight / density,
            targetCellsX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellWidth else 0,
            targetCellsY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellHeight else 0,
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, WidgetChoice::appLabel).thenBy(String.CASE_INSENSITIVE_ORDER, WidgetChoice::label))
}

/** Installed widgets grouped by app. Long-press a preview and drag it onto the home grid. */
@Composable
fun WidgetPicker(
    choices: List<WidgetChoice>,
    grid: GridMetrics?,
    onDragStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .semantics { contentDescription = "Widgets" },
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Long-press a widget and drag it onto the home screen",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (choices.isEmpty()) {
            item { Text("No widgets installed", color = Color.White) }
        }
        choices.groupBy { it.appLabel }.forEach { (app, widgets) ->
            item(key = "app:$app") {
                Text(app, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(widgets, key = { it.provider.flattenToString() }) { choice ->
                WidgetRow(choice, grid, onDragStart)
            }
        }
    }
}

@Composable
private fun WidgetRow(choice: WidgetChoice, grid: GridMetrics?, onDragStart: () -> Unit) {
    val preview = remember(choice.provider) { choice.preview?.toBitmap()?.asImageBitmap() }
    val span = grid?.let { spanFor(choice.minWidthDp, choice.minHeightDp, choice.targetCellsX, choice.targetCellsY, it) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "${choice.label} preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 160.dp)
                    .heightIn(max = 120.dp)
                    .dragAndDropSource { _ ->
                        onDragStart()
                        DragAndDropTransferData(
                            ClipData(WIDGET_CLIP_LABEL, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(choice.provider.flattenToString()))
                        )
                    },
            )
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(choice.label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            if (span != null) {
                Text("${span.first} × ${span.second}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
