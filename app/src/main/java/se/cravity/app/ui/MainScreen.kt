package se.cravity.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import se.cravity.app.model.ClockMode
import se.cravity.app.model.Digestion
import se.cravity.app.model.Foodmap
import se.cravity.app.model.GutForecastPoint
import se.cravity.app.model.Intake
import se.cravity.app.model.IntakeKind
import se.cravity.app.model.Tag
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showLog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::import)
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cravity") },
                actions = {
                    TextButton(onClick = { menuOpen = true }) { Text("Data") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Export to file…") }, onClick = {
                            menuOpen = false
                            exportLauncher.launch("cravity-${LocalDate.now()}.json")
                        })
                        DropdownMenuItem(text = { Text("Import from file…") }, onClick = {
                            menuOpen = false
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        })
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showLog = true }) { Text("Log intake") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ClockMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = s.mode == m,
                            onClick = { vm.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ClockMode.entries.size),
                        ) { Text(if (m == ClockMode.H12) "12 h" else "24 h") }
                    }
                }
            }
            item {
                val dark = isSystemInDarkTheme()
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    drawIntoCanvas { ClockRenderer.draw(it.nativeCanvas, size.width, size.height, s.face, dark) }
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(s.headline, style = MaterialTheme.typography.titleMedium)
                    s.digestion?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(6.dp))
                    Legend()
                }
            }
            item { FoodmapCard(s.foodmap) }
            item { GutCard(s.forecast) }
            item { Text("Log", style = MaterialTheme.typography.titleMedium) }
            if (s.intakes.isEmpty()) {
                item { Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(s.intakes.take(50), key = { it.id }) { IntakeRow(it, s.now, onDelete = { vm.delete(it.id) }) }
            item {
                Text(
                    "Everything stays on this device. Hunger, digestion and gut outlooks are rough estimates for " +
                        "personal reflection, not medical advice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showLog) {
        LogDialog(
            onDismiss = { showLog = false },
            onSave = { kind, tags, offset, note ->
                vm.log(kind, tags, offset, note)
                showLog = false
            },
        )
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(Color(ClockRenderer.GREEN), "Satisfied")
        LegendDot(Color(ClockRenderer.ORANGE), "Building")
        LegendDot(Color(ClockRenderer.RED), "Hungry")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Bar(label: String, value: Double, max: Double, color: Color, trailing: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier.fillMaxWidth((value / max).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp)).background(color),
            )
        }
        Text(trailing, modifier = Modifier.width(44.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FoodmapCard(fm: Foodmap) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Foodmap, last 24 h", style = MaterialTheme.typography.titleSmall)
            val max = maxOf(10.0, fm.sweetness, fm.grain, fm.fat)
            val c = MaterialTheme.colorScheme.primary
            Bar("Sweetness", fm.sweetness, max, c, "%.1f".format(fm.sweetness))
            Bar("Grain", fm.grain, max, c, "%.1f".format(fm.grain))
            Bar("Fat", fm.fat, max, c, "%.1f".format(fm.fat))
        }
    }
}

@Composable
private fun GutCard(forecast: List<GutForecastPoint>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Gut outlook, coming hours", style = MaterialTheme.typography.titleSmall)
            Text(
                "Rough estimate, not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val now = forecast.firstOrNull()?.outlook
            if (now != null) {
                Text("Now", style = MaterialTheme.typography.labelLarge)
                Bar("Sugar swing", now.sugarSwing, 1.0, Color(0xFFAB47BC), pct(now.sugarSwing))
                Bar("Bloating", now.fermentation, 1.0, Color(0xFF8D6E63), pct(now.fermentation))
                Bar("Heaviness", now.heaviness, 1.0, Color(0xFF5C6BC0), pct(now.heaviness))
            }
            Spacer(Modifier.height(4.dp))
            Text("Next hours (sugar / bloating / heaviness)", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                forecast.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.height(48.dp), verticalAlignment = Alignment.Bottom) {
                            MiniBar(p.outlook.sugarSwing, Color(0xFFAB47BC))
                            MiniBar(p.outlook.fermentation, Color(0xFF8D6E63))
                            MiniBar(p.outlook.heaviness, Color(0xFF5C6BC0))
                        }
                        Text("+${p.hoursAhead}h", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBar(v: Double, color: Color) {
    Box(Modifier.padding(horizontal = 1.dp).width(5.dp).height((48 * v.coerceIn(0.03, 1.0)).dp).background(color))
}

private fun pct(v: Double) = "${(v * 100).roundToInt()}%"

@Composable
private fun IntakeRow(i: Intake, now: Long, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val planned = if (i.timeMillis > now) " (planned)" else ""
                val title = if (i.kind == IntakeKind.SUPPLEMENT) "Supplement" else "Meal"
                Text("${StatusText.time(i.timeMillis)} · $title$planned", fontWeight = FontWeight.SemiBold)
                if (i.tags.isNotEmpty()) Text(i.tags.sortedBy { it.ordinal }.joinToString(", ") { it.label })
                if (i.note.isNotBlank()) Text(i.note, style = MaterialTheme.typography.bodySmall)
                if (i.kind == IntakeKind.MEAL) {
                    Text(
                        "Processed ~${StatusText.time(Digestion.processedAtMillis(i))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogDialog(onDismiss: () -> Unit, onSave: (IntakeKind, Set<Tag>, Int, String) -> Unit) {
    var kind by remember { mutableStateOf(IntakeKind.MEAL) }
    var tags by remember { mutableStateOf(setOf<Tag>()) }
    var offset by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log intake") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    IntakeKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k,
                            onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, IntakeKind.entries.size),
                        ) { Text(if (k == IntakeKind.MEAL) "Meal" else "Supplement") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag.entries.forEach { t ->
                        FilterChip(
                            selected = t in tags,
                            onClick = { tags = if (t in tags) tags - t else tags + t },
                            label = { Text(t.label) },
                        )
                    }
                }
                val mins = offset.roundToInt()
                val time = StatusText.time(System.currentTimeMillis() + mins * 60_000L)
                val whenText = when {
                    mins == 0 -> "Now ($time)"
                    mins < 0 -> "${-mins} min ago ($time)"
                    else -> "Planned in $mins min ($time)"
                }
                Text(whenText, style = MaterialTheme.typography.labelLarge)
                Slider(value = offset, onValueChange = { offset = it }, valueRange = -240f..240f, steps = 31)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalTags = if (kind == IntakeKind.SUPPLEMENT) tags + Tag.SUPPLEMENT else tags
                onSave(kind, finalTags, offset.roundToInt(), note)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
