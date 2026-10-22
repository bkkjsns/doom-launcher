@file:OptIn(ExperimentalMaterial3Api::class)

package com.msa.freedoom.ui.controlseditor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msa.freedoom.AppSettings
import com.msa.freedoom.R
import com.msa.freedoom.ui.fx.slimePress
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Touch-layout presets. The native touch engine keeps button positions in files under
 * `<game dir>/user_files/touch_layouts`; a preset is a named snapshot of those files, so a
 * layout can be switched, exported, imported and survives a reinstall when exported.
 */
object LayoutPresets {
    private const val ACTIVE_KEY = "active_layout"

    private fun root(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "ControlPresets").apply { mkdirs() }

    /** Where the native touch engine keeps layouts and touch settings (see SDLOpenTouch). */
    fun layoutDir(): File =
        File(AppSettings.getQuakeFullDir(), "user_files/touch_layouts").apply { mkdirs() }

    private fun isLayoutFile(name: String): Boolean =
        !name.endsWith(".default") && !name.endsWith(".bak") && name.matches(Regex("[A-Za-z0-9_.-]+"))

    private fun live(@Suppress("UNUSED_PARAMETER") ctx: Context): List<File> =
        layoutDir().listFiles { f -> f.isFile && isLayoutFile(f.name) }?.toList() ?: emptyList()

    fun safe(name: String): String = name.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_").take(40)

    private fun dir(ctx: Context, name: String): File = File(root(ctx), safe(name))

    fun names(ctx: Context): List<String> =
        root(ctx).listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    /** Snapshots the live layout into [name]. Returns how many files were saved. */
    fun save(ctx: Context, name: String): Int {
        val files = live(ctx)
        if (files.isEmpty()) return 0 // never wipe a preset with nothing
        val d = dir(ctx, name).apply { mkdirs() }
        d.listFiles()?.forEach { it.delete() }
        files.forEach { it.copyTo(File(d, it.name), overwrite = true) }
        return files.size
    }

    /** Copies preset [name] over the live layout. Returns how many files were applied. */
    fun apply(ctx: Context, name: String): Int {
        var n = 0
        dir(ctx, name).listFiles()?.filter { isLayoutFile(it.name) }?.forEach {
            it.copyTo(File(layoutDir(), it.name), overwrite = true)
            n++
        }
        return n
    }

    fun delete(ctx: Context, name: String) {
        dir(ctx, name).deleteRecursively()
    }

    fun exportZip(ctx: Context, name: String, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            dir(ctx, name).listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Imports a preset zip. Only plain `.xml` entries are kept, by file name (no paths). */
    fun importZip(ctx: Context, name: String, input: InputStream): Int {
        val d = dir(ctx, name).apply { mkdirs() }
        var n = 0
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val fileName = File(e.name).name
                if (!e.isDirectory && isLayoutFile(fileName)) {
                    File(d, fileName).outputStream().use { zip.copyTo(it) }
                    n++
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        return n
    }

    /** After a game closes: fold the (possibly edited) live layout back into the active preset. */
    fun syncActive(ctx: Context) {
        val name = AppSettings.getStringOption(ctx, ACTIVE_KEY, null)
        if (!name.isNullOrBlank()) {
            save(ctx, name)
            AppSettings.setStringOption(ctx, ACTIVE_KEY, "")
        }
    }

    fun markActive(ctx: Context, name: String) = AppSettings.setStringOption(ctx, ACTIVE_KEY, name)
}

private fun displayName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: "imported"

@Composable
fun ControlsScreen(onOpenTouchSettings: () -> Unit, onEditLayout: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var presets by remember { mutableStateOf(LayoutPresets.names(ctx)) }
    var saving by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var exportName by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        presets = LayoutPresets.names(ctx)
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val n = exportName
        exportName = null
        if (uri != null && n != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { LayoutPresets.exportZip(ctx, n, it) }
            }.isSuccess
            toast(ctx.getString(if (ok) R.string.controls_exported else R.string.controls_failed))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = displayName(ctx, uri).substringBeforeLast('.')
            val count = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { LayoutPresets.importZip(ctx, name, it) } ?: 0
            }.getOrDefault(0)
            refresh()
            toast(
                if (count > 0) ctx.getString(R.string.controls_imported, count)
                else ctx.getString(R.string.controls_failed),
            )
        }
    }

    val saveSrc = remember { MutableInteractionSource() }
    val importSrc = remember { MutableInteractionSource() }
    val openSrc = remember { MutableInteractionSource() }
    val editSrc = remember { MutableInteractionSource() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(
                    stringResource(R.string.controls_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.controls_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.controls_edit_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.controls_edit_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onEditLayout,
                        interactionSource = editSrc,
                        modifier = Modifier.slimePress(editSrc),
                    ) { Text(stringResource(R.string.controls_edit_layout)) }
                    OutlinedButton(
                        onClick = onOpenTouchSettings,
                        interactionSource = openSrc,
                        modifier = Modifier.slimePress(openSrc),
                    ) { Text(stringResource(R.string.controls_open_settings)) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.controls_presets), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.controls_presets_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { newName = ""; saving = true },
                            interactionSource = saveSrc,
                            modifier = Modifier.slimePress(saveSrc),
                        ) { Text(stringResource(R.string.controls_save_current)) }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
                            interactionSource = importSrc,
                            modifier = Modifier.slimePress(importSrc),
                        ) { Text(stringResource(R.string.controls_import)) }
                    }
                    if (presets.isEmpty()) {
                        Text(
                            stringResource(R.string.controls_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    presets.forEach { name ->
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    val n = LayoutPresets.apply(ctx, name)
                                    toast(ctx.getString(R.string.controls_applied, n))
                                }) { Text(stringResource(R.string.controls_apply)) }
                                TextButton(onClick = {
                                    exportName = name
                                    exportLauncher.launch("$name.zip")
                                }) { Text(stringResource(R.string.controls_export)) }
                                TextButton(onClick = { deleting = name }) {
                                    Text(stringResource(R.string.instance_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (saving) {
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(stringResource(R.string.controls_save_current)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.instance_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val n = LayoutPresets.save(ctx, newName)
                        saving = false
                        refresh()
                        toast(
                            if (n > 0) ctx.getString(R.string.controls_saved, n)
                            else ctx.getString(R.string.controls_none_found),
                        )
                    },
                ) { Text(stringResource(R.string.instance_save)) }
            },
            dismissButton = {
                TextButton(onClick = { saving = false }) { Text(stringResource(R.string.instance_cancel)) }
            },
        )
    }

    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.instance_delete)) },
            text = { Text(stringResource(R.string.instance_delete_confirm, name)) },
            confirmButton = {
                TextButton(onClick = {
                    LayoutPresets.delete(ctx, name)
                    deleting = null
                    refresh()
                }) { Text(stringResource(R.string.instance_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.instance_cancel)) }
            },
        )
    }
}
