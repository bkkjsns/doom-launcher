@file:OptIn(ExperimentalMaterial3Api::class)

package com.msa.freedoom.ui.instances

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.freedoom.AppSettings
import com.msa.freedoom.BuildConfig
import com.msa.freedoom.R
import com.msa.freedoom.ui.changelog.ChangelogDialog
import com.msa.freedoom.ui.controlseditor.LayoutPresets
import com.msa.freedoom.ui.fx.slimeEntrance
import com.msa.freedoom.ui.fx.slimePress
import kotlinx.coroutines.CoroutineScope
import com.msa.freedoom.ui.launch.LaunchProfile
import com.msa.freedoom.ui.launch.LaunchState
import com.msa.freedoom.ui.launch.WadEntry
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An instance is a launch loadout with its OWN data folder: separate engine config and
 * separate saves, plus optional multiplayer settings. Mods are listed per instance.
 */
@Serializable
data class Instance(
    val id: String,
    val name: String,
    val iwadName: String,
    /** Add-on paths relative to the game dir, in load order. */
    val modRelPaths: List<String> = emptyList(),
    val extraArgs: String = "",
    /** "off", "host" or "join". */
    val mpMode: String = "off",
    val mpPlayers: Int = 2,
    val mpAddress: String = "",
    val mpPort: Int = 5029,
    /** Touch-layout preset applied before launch (empty = leave the current layout alone). */
    val layout: String = "",
    val lastPlayed: Long = 0L,
)

object InstanceStore {
    private const val KEY = "instances_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(ctx: Context): List<Instance> {
        val raw = AppSettings.getStringOption(ctx, KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Instance>>(raw) }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, instance: Instance) {
        val all = load(ctx)
        val updated = if (all.any { it.id == instance.id }) {
            all.map { if (it.id == instance.id) instance else it }
        } else {
            all + instance
        }
        persist(ctx, updated)
    }

    fun delete(ctx: Context, id: String) = persist(ctx, load(ctx).filterNot { it.id == id })

    private fun persist(ctx: Context, list: List<Instance>) {
        AppSettings.setStringOption(ctx, KEY, json.encodeToString(list))
    }
}

fun Instance.dataDir(base: String): String = "$base/instances/$id"

/**
 * Engine arguments that isolate this instance. They are placed before the launcher's
 * default `-savedir`, so the instance's own save folder is the first one the engine sees.
 */
fun Instance.engineArgs(base: String): String = buildString {
    append("-config ${dataDir(base)}/config.ini -savedir ${dataDir(base)}/saves ")
    when (mpMode) {
        "host" -> append("-host $mpPlayers -port $mpPort ")
        "join" -> if (mpAddress.isNotBlank()) append("-join ${mpAddress.trim()} ")
    }
    if (extraArgs.isNotBlank()) append(extraArgs.trim()).append(' ')
}

private val PALETTE = listOf(
    Color(0xFFB71C1C) to Color(0xFFE65100),
    Color(0xFF4A148C) to Color(0xFFD81B60),
    Color(0xFF0D47A1) to Color(0xFF00897B),
    Color(0xFF1B5E20) to Color(0xFF9E9D24),
    Color(0xFF263238) to Color(0xFF6D4C41),
)

@Composable
fun InstancesScreen(state: LaunchState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var instances by remember { mutableStateOf(InstanceStore.load(ctx)) }
    var editing by remember { mutableStateOf<Instance?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Instance?>(null) }
    val fabSrc = remember { MutableInteractionSource() }
    var showLog by remember { mutableStateOf(false) }

    fun refresh() {
        instances = InstanceStore.load(ctx)
    }

    fun play(inst: Instance) {
        playInstance(state, ctx, inst, scope)
        refresh()
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        stringResource(R.string.instances_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.instances_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Doom Launcher v" + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { showLog = true }) {
                            Text(stringResource(R.string.changelog_open))
                        }
                    }
                }
            }
            if (instances.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.instance_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
            itemsIndexed(instances, key = { _, it -> it.id }) { index, inst ->
                InstanceCard(
                    inst = inst,
                    index = index,
                    onPlay = { play(inst) },
                    onEdit = { editing = inst },
                    onDuplicate = {
                        InstanceStore.save(
                            ctx,
                            inst.copy(
                                id = newId(),
                                name = inst.name + " " + ctx.getString(R.string.instance_copy_suffix),
                                lastPlayed = 0L,
                            ),
                        )
                        refresh()
                    },
                    onDelete = { deleting = inst },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.instance_new)) },
            interactionSource = fabSrc,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).slimePress(fabSrc),
        )
    }

    if (showLog) ChangelogDialog(latestOnly = false, onDismiss = { showLog = false })

    if (creating || editing != null) {
        val initial = editing
        InstanceDialog(
            initial = initial,
            games = state.games,
            currentIwad = state.selectedGame?.file,
            playTabMods = state.selectedMods.size,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { inst, copyMods ->
                val finalInst = if (copyMods) {
                    inst.copy(modRelPaths = state.selectedMods.map { it.relPath })
                } else {
                    inst
                }
                InstanceStore.save(ctx, finalInst)
                refresh()
                creating = false
                editing = null
            },
        )
    }

    deleting?.let { inst ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.instance_delete)) },
            text = { Text(stringResource(R.string.instance_delete_confirm, inst.name)) },
            confirmButton = {
                TextButton(onClick = {
                    InstanceStore.delete(ctx, inst.id)
                    refresh()
                    deleting = null
                }) { Text(stringResource(R.string.instance_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.instance_cancel))
                }
            },
        )
    }
}

/**
 * Launches [inst]: applies its mods/args, optionally its touch layout, then starts the engine.
 * Returns false (with a toast) when the instance's IWAD is not in the game folder.
 */
fun playInstance(state: LaunchState, ctx: Context, inst: Instance, scope: CoroutineScope): Boolean {
    val base = state.baseDir
    File(inst.dataDir(base), "saves").mkdirs()
    state.applyProfile(
        LaunchProfile(
            name = inst.name,
            iwadName = inst.iwadName,
            modRelPaths = inst.modRelPaths,
            extraArgs = inst.engineArgs(base),
        ),
    )
    if (state.selectedGame?.file != inst.iwadName) {
        Toast.makeText(ctx, ctx.getString(R.string.instance_iwad_missing, inst.iwadName), Toast.LENGTH_LONG).show()
        return false
    }
    if (inst.layout.isNotBlank()) {
        LayoutPresets.syncActive(ctx) // flush the previous instance's layout first
        if (LayoutPresets.apply(ctx, inst.layout) > 0) LayoutPresets.markActive(ctx, inst.layout)
    }
    InstanceStore.save(ctx, inst.copy(lastPlayed = System.currentTimeMillis()))
    scope.launch { state.launchGame() }
    return true
}

private fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

@Composable
private fun InstanceCard(
    inst: Instance,
    index: Int,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val (c1, c2) = PALETTE[abs(inst.id.hashCode()) % PALETTE.size]
    var menu by remember { mutableStateOf(false) }
    val playSrc = remember { MutableInteractionSource() }

    Card(Modifier.fillMaxWidth().slimeEntrance(inst.id, (index * 70L).coerceAtMost(420L))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(c1, c2))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        inst.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        inst.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        inst.iwadName + " · " +
                            stringResource(R.string.instance_mods_count, inst.modRelPaths.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val played = if (inst.lastPlayed == 0L) {
                        stringResource(R.string.instance_never)
                    } else {
                        DateUtils.getRelativeTimeSpanString(
                            inst.lastPlayed,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ).toString()
                    }
                    val mp = when (inst.mpMode) {
                        "host" -> " · " + stringResource(R.string.instance_mp_host)
                        "join" -> " · " + stringResource(R.string.instance_mp_join)
                        else -> ""
                    }
                    Text(
                        played + mp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.instance_edit)) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.instance_duplicate)) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.instance_delete)) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onPlay,
                interactionSource = playSrc,
                modifier = Modifier.fillMaxWidth().slimePress(playSrc),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.instance_play))
            }
        }
    }
}

@Composable
private fun InstanceDialog(
    initial: Instance?,
    games: List<WadEntry>,
    currentIwad: String?,
    playTabMods: Int,
    onDismiss: () -> Unit,
    onSave: (Instance, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iwad by remember { mutableStateOf(initial?.iwadName ?: currentIwad ?: "") }
    var iwadMenu by remember { mutableStateOf(false) }
    var copyMods by remember { mutableStateOf(initial == null) }
    var args by remember { mutableStateOf(initial?.extraArgs ?: "") }
    var mpMode by remember { mutableStateOf(initial?.mpMode ?: "off") }
    var players by remember { mutableStateOf((initial?.mpPlayers ?: 2).toString()) }
    var address by remember { mutableStateOf(initial?.mpAddress ?: "") }
    var port by remember { mutableStateOf((initial?.mpPort ?: 5029).toString()) }
    val ctx = LocalContext.current
    val layoutNames = remember { LayoutPresets.names(ctx) }
    var layout by remember { mutableStateOf(initial?.layout ?: "") }
    var layoutMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.instance_new else R.string.instance_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.instance_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(onClick = { iwadMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(iwad.ifBlank { stringResource(R.string.instance_iwad) })
                    }
                    DropdownMenu(expanded = iwadMenu, onDismissRequest = { iwadMenu = false }) {
                        games.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.file) },
                                onClick = { iwad = g.file; iwadMenu = false },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyMods, onCheckedChange = { copyMods = it })
                    Text(stringResource(R.string.instance_copy_mods, playTabMods))
                }
                OutlinedTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = { Text(stringResource(R.string.instance_args)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(onClick = { layoutMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.instance_layout) + ": " +
                                layout.ifBlank { stringResource(R.string.instance_layout_none) },
                        )
                    }
                    DropdownMenu(expanded = layoutMenu, onDismissRequest = { layoutMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.instance_layout_none)) },
                            onClick = { layout = ""; layoutMenu = false },
                        )
                        layoutNames.forEach { n ->
                            DropdownMenuItem(
                                text = { Text(n) },
                                onClick = { layout = n; layoutMenu = false },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.instance_mp),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mpMode == "off",
                        onClick = { mpMode = "off" },
                        label = { Text(stringResource(R.string.instance_mp_off)) },
                    )
                    FilterChip(
                        selected = mpMode == "host",
                        onClick = { mpMode = "host" },
                        label = { Text(stringResource(R.string.instance_mp_host)) },
                    )
                    FilterChip(
                        selected = mpMode == "join",
                        onClick = { mpMode = "join" },
                        label = { Text(stringResource(R.string.instance_mp_join)) },
                    )
                }
                if (mpMode == "host") {
                    OutlinedTextField(
                        value = players,
                        onValueChange = { players = it.filter(Char::isDigit).take(1) },
                        label = { Text(stringResource(R.string.instance_players)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.instance_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mpMode == "join") {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.instance_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && iwad.isNotBlank(),
                onClick = {
                    onSave(
                        Instance(
                            id = initial?.id ?: newId(),
                            name = name.trim(),
                            iwadName = iwad,
                            modRelPaths = initial?.modRelPaths ?: emptyList(),
                            extraArgs = args,
                            layout = layout,
                            mpMode = mpMode,
                            mpPlayers = players.toIntOrNull()?.coerceIn(2, 8) ?: 2,
                            mpAddress = address,
                            mpPort = port.toIntOrNull()?.coerceIn(1024, 65535) ?: 5029,
                            lastPlayed = initial?.lastPlayed ?: 0L,
                        ),
                        copyMods,
                    )
                },
            ) { Text(stringResource(R.string.instance_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.instance_cancel)) }
        },
    )
}
