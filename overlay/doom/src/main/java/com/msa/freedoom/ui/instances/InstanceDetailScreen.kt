@file:OptIn(ExperimentalMaterial3Api::class)

package com.msa.freedoom.ui.instances

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.freedoom.R
import com.msa.freedoom.ui.controlseditor.LayoutPresets
import com.msa.freedoom.ui.fx.SlimeOverlay
import com.msa.freedoom.ui.fx.slimeContentTransform
import com.msa.freedoom.ui.fx.slimeEnter
import com.msa.freedoom.ui.fx.slimeExit
import com.msa.freedoom.ui.fx.slimePress
import com.msa.freedoom.ui.launch.LaunchState
import com.msa.freedoom.ui.mods.ModDownloaderScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IMPORT_EXT = setOf("wad", "pk3", "pk7", "deh", "bex")

/** Copies picked files into [dir]; returns their paths relative to the game dir (prefix [relBase]). */
private fun copyIn(ctx: Context, uris: List<Uri>, dir: File, relBase: String): List<String> {
    dir.mkdirs()
    val out = mutableListOf<String>()
    for (uri in uris) {
        val name = ctx.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: continue
        if (name.substringAfterLast('.', "").lowercase() !in IMPORT_EXT) continue
        val clean = name.replace('/', '_')
        val dest = File(dir, clean)
        val part = File(dir, "$clean.part")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { src -> part.outputStream().use { src.copyTo(it) } }
            if (part.renameTo(dest)) out += "$relBase/$clean" else part.delete()
        } catch (e: Exception) {
            part.delete()
        }
    }
    return out
}

private val CATEGORIES = listOf("overview", "mods", "maps", "multiplayer", "controls")

@Composable
fun InstanceDetailScreen(
    state: LaunchState,
    initial: Instance,
    startCategory: String,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onEditLayout: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val base = state.baseDir
    var inst by remember(initial.id) { mutableStateOf(initial) }
    var category by remember { mutableStateOf(startCategory) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var lastKind by remember { mutableStateOf("mods") }
    var confirmDelete by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val playSrc = remember { MutableInteractionSource() }
    val (c1, c2) = instanceColors(inst.id)

    fun update(new: Instance) {
        inst = new
        InstanceStore.save(ctx, new)
        onChanged()
    }

    fun addFiles(kind: String, rels: List<String>) {
        if (rels.isEmpty()) return
        update(
            if (kind == "mods") inst.copy(modRelPaths = (inst.modRelPaths + rels).distinct())
            else inst.copy(mapRelPaths = (inst.mapRelPaths + rels).distinct()),
        )
    }

    fun importInto(kind: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                copyIn(ctx, uris, File(base, "instances/${inst.id}/$kind"), "instances/${inst.id}/$kind")
            }
            addFiles(kind, added)
            Toast.makeText(ctx, ctx.getString(R.string.instance_imported, added.size), Toast.LENGTH_SHORT).show()
        }
    }

    val pickMods = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        importInto("mods", it)
    }
    val pickMaps = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        importInto("maps", it)
    }

    BackHandler { if (downloading != null) downloading = null else onClose() }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(c1, c2)))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = onClose) {
                                Text("\u2039 " + stringResource(R.string.instance_back), color = Color.White)
                            }
                            Text(
                                inst.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                            Text(
                                inst.iwadName + "  \u00B7  " +
                                    stringResource(R.string.instance_cat_mods) + " " + inst.modRelPaths.size + "  \u00B7  " +
                                    stringResource(R.string.instance_cat_maps) + " " + inst.mapRelPaths.size,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                            )
                        }
                        Button(
                            onClick = {
                                playInstance(state, ctx, inst, scope)
                                onChanged()
                            },
                            interactionSource = playSrc,
                            modifier = Modifier.slimePress(playSrc),
                        ) { Text(stringResource(R.string.instance_play)) }
                    }
                }

                val label: @Composable (String) -> String = { key ->
                    when (key) {
                        "overview" -> stringResource(R.string.instance_cat_overview)
                        "mods" -> stringResource(R.string.instance_cat_mods) + " (" + inst.modRelPaths.size + ")"
                        "maps" -> stringResource(R.string.instance_cat_maps) + " (" + inst.mapRelPaths.size + ")"
                        "multiplayer" -> stringResource(R.string.instance_cat_mp)
                        else -> stringResource(R.string.instance_cat_controls)
                    }
                }

                val content: @Composable (Modifier) -> Unit = { mod ->
                    AnimatedContent(
                        targetState = category,
                        transitionSpec = { slimeContentTransform() },
                        modifier = mod,
                        label = "instanceCategory",
                    ) { cat ->
                        when (cat) {
                            "overview" -> OverviewPanel(
                                inst = inst,
                                games = state.games.map { it.file },
                                onChange = { update(it) },
                                onDelete = { confirmDelete = true },
                            )
                            "mods" -> FilesPanel(
                                kind = "mods",
                                rels = inst.modRelPaths,
                                onImport = { pickMods.launch(arrayOf("*/*")) },
                                onDownload = { lastKind = "mods"; downloading = "mods" },
                                onRemove = { rel ->
                                    File(base, rel).takeIf { rel.startsWith("instances/") }?.delete()
                                    update(inst.copy(modRelPaths = inst.modRelPaths - rel))
                                },
                            )
                            "maps" -> FilesPanel(
                                kind = "maps",
                                rels = inst.mapRelPaths,
                                onImport = { pickMaps.launch(arrayOf("*/*")) },
                                onDownload = { lastKind = "maps"; downloading = "maps" },
                                onRemove = { rel ->
                                    File(base, rel).takeIf { rel.startsWith("instances/") }?.delete()
                                    update(inst.copy(mapRelPaths = inst.mapRelPaths - rel))
                                },
                            )
                            "multiplayer" -> MultiplayerPanel(inst) { update(it) }
                            else -> ControlsPanel(inst, onChange = { update(it) }, onEditLayout = onEditLayout)
                        }
                    }
                }

                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.width(200.dp).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CATEGORIES.forEach { key ->
                                CategoryItem(label(key), category == key) { category = key }
                            }
                        }
                        content(Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CATEGORIES.forEach { key ->
                                CategoryItem(label(key), category == key) { category = key }
                            }
                        }
                        content(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }

        SlimeOverlay(visible = downloading != null) {
            ModDownloaderScreen(
                inst = inst,
                kind = lastKind,
                baseDir = base,
                onInstalled = { addFiles(lastKind, it) },
                onClose = { downloading = null },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.instance_delete)) },
            text = { Text(stringResource(R.string.instance_delete_confirm, inst.name)) },
            confirmButton = {
                TextButton(onClick = {
                    InstanceStore.delete(ctx, inst.id)
                    confirmDelete = false
                    onChanged()
                    onClose()
                }) { Text(stringResource(R.string.instance_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.instance_cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryItem(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun OverviewPanel(
    inst: Instance,
    games: List<String>,
    onChange: (Instance) -> Unit,
    onDelete: () -> Unit,
) {
    var iwadMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = inst.name,
            onValueChange = { onChange(inst.copy(name = it)) },
            label = { Text(stringResource(R.string.instance_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box {
            OutlinedButton(onClick = { iwadMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(inst.iwadName.ifBlank { stringResource(R.string.instance_iwad) })
            }
            DropdownMenu(expanded = iwadMenu, onDismissRequest = { iwadMenu = false }) {
                games.forEach { g ->
                    DropdownMenuItem(
                        text = { Text(g) },
                        onClick = { onChange(inst.copy(iwadName = g)); iwadMenu = false },
                    )
                }
            }
        }
        OutlinedTextField(
            value = inst.extraArgs,
            onValueChange = { onChange(inst.copy(extraArgs = it)) },
            label = { Text(stringResource(R.string.instance_args)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.instance_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FilesPanel(
    kind: String,
    rels: List<String>,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val dlSrc = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(if (kind == "mods") R.string.instance_files_hint_mods else R.string.instance_files_hint_maps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDownload, interactionSource = dlSrc, modifier = Modifier.slimePress(dlSrc)) {
                Text(stringResource(R.string.mods_download))
            }
            OutlinedButton(onClick = onImport) { Text(stringResource(R.string.instance_import)) }
        }
        if (rels.isEmpty()) {
            Text(
                stringResource(R.string.instance_files_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rels, key = { it }) { rel ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    rel.substringAfterLast('/'),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    rel.substringBeforeLast('/', ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onRemove(rel) }) { Text(stringResource(R.string.instance_remove)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiplayerPanel(inst: Instance, onChange: (Instance) -> Unit) {
    var players by remember { mutableStateOf(inst.mpPlayers.toString()) }
    var port by remember { mutableStateOf(inst.mpPort.toString()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("off" to R.string.instance_mp_off, "host" to R.string.instance_mp_host, "join" to R.string.instance_mp_join)
                .forEach { (mode, res) ->
                    FilterChip(
                        selected = inst.mpMode == mode,
                        onClick = { onChange(inst.copy(mpMode = mode)) },
                        label = { Text(stringResource(res)) },
                    )
                }
        }
        if (inst.mpMode == "host") {
            OutlinedTextField(
                value = players,
                onValueChange = {
                    players = it.filter(Char::isDigit).take(1)
                    onChange(inst.copy(mpPlayers = players.toIntOrNull()?.coerceIn(2, 8) ?: 2))
                },
                label = { Text(stringResource(R.string.instance_players)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it.filter(Char::isDigit).take(5)
                    onChange(inst.copy(mpPort = port.toIntOrNull()?.coerceIn(1024, 65535) ?: 5029))
                },
                label = { Text(stringResource(R.string.instance_port)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (inst.mpMode == "join") {
            OutlinedTextField(
                value = inst.mpAddress,
                onValueChange = { onChange(inst.copy(mpAddress = it)) },
                label = { Text(stringResource(R.string.instance_address)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ControlsPanel(inst: Instance, onChange: (Instance) -> Unit, onEditLayout: () -> Unit) {
    val ctx = LocalContext.current
    val names = remember { LayoutPresets.names(ctx) }
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.instance_layout) + ": " +
                        inst.layout.ifBlank { stringResource(R.string.instance_layout_none) },
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.instance_layout_none)) },
                    onClick = { onChange(inst.copy(layout = "")); menu = false },
                )
                names.forEach { n ->
                    DropdownMenuItem(text = { Text(n) }, onClick = { onChange(inst.copy(layout = n)); menu = false })
                }
            }
        }
        Button(onClick = onEditLayout) { Text(stringResource(R.string.instance_edit_layout)) }
    }
}
