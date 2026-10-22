@file:OptIn(ExperimentalMaterial3Api::class)

package com.msa.freedoom.ui.mods

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msa.freedoom.R
import com.msa.freedoom.idgames.VerificationRequiredException
import com.msa.freedoom.ui.fx.slimeEntrance
import com.msa.freedoom.ui.fx.slimePress
import com.msa.freedoom.ui.instances.Instance
import com.msa.freedoom.ui.instances.instanceColors
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

private const val DOWNLOADING = 0
private const val INSTALLED = 1
private const val FAILED = 2

private data class ItemStatus(val state: Int, val progress: Float = 0f, val msg: String = "")

/** Search a source and install straight into one instance. [kind] is "mods" or "maps". */
@Composable
fun ModDownloaderScreen(
    inst: Instance,
    kind: String,
    baseDir: String,
    onInstalled: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMaps = kind == "maps"
    var source by remember { mutableStateOf(Source.IDGAMES) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var modsOnly by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<ModItem>>(emptyList()) }
    val status = remember { mutableStateMapOf<String, ItemStatus>() }
    val searchSrc = remember { MutableInteractionSource() }
    val (c1, c2) = instanceColors(inst.id)

    LaunchedEffect(source, reload, modsOnly) {
        loading = true
        error = null
        try {
            results = if (source == Source.IDGAMES) {
                ModSources.searchIdgames(query.trim(), isMaps)
            } else {
                ModSources.searchGithub(query.trim(), modsOnly)
            }
        } catch (e: VerificationRequiredException) {
            results = emptyList()
            error = ctx.getString(R.string.browse_error_verification)
        } catch (e: IOException) {
            results = emptyList()
            error = e.message ?: ctx.getString(R.string.browse_error_offline)
        } catch (e: SerializationException) {
            results = emptyList()
            error = ctx.getString(R.string.mods_error_parse)
        } finally {
            loading = false
        }
    }

    fun download(item: ModItem) {
        scope.launch {
            status[item.id] = ItemStatus(DOWNLOADING)
            when (
                val r = ModSources.install(baseDir, inst, kind, item) { read, total ->
                    status[item.id] = ItemStatus(
                        DOWNLOADING,
                        if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    )
                }
            ) {
                is InstallResult.Done -> {
                    status[item.id] = ItemStatus(INSTALLED)
                    onInstalled(r.relPaths)
                }
                is InstallResult.Failed -> status[item.id] = ItemStatus(FAILED, msg = r.reason)
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Banner in the instance's own colours.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(c1, c2)))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) {
                            Text("\u2039 " + stringResource(R.string.instance_back), color = Color.White)
                        }
                        Text(
                            stringResource(if (isMaps) R.string.mods_title_maps else R.string.mods_title_mods),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Text(
                        stringResource(R.string.mods_subtitle, inst.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            Row(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.mods_search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { reload++ }),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { reload++ },
                    interactionSource = searchSrc,
                    modifier = Modifier.slimePress(searchSrc),
                ) { Text(stringResource(R.string.mods_search)) }
            }

            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = source == Source.IDGAMES,
                    onClick = { source = Source.IDGAMES },
                    label = { Text(stringResource(R.string.mods_source_idgames)) },
                )
                if (!isMaps) {
                    FilterChip(
                        selected = source == Source.GITHUB,
                        onClick = { source = Source.GITHUB },
                        label = { Text(stringResource(R.string.mods_source_github)) },
                    )
                }
                if (!isMaps && source == Source.GITHUB) {
                    FilterChip(
                        selected = modsOnly,
                        onClick = { modsOnly = !modsOnly },
                        label = { Text(stringResource(R.string.mods_only)) },
                    )
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.mods_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error != null -> Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            error.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { reload++ }) { Text(stringResource(R.string.browse_retry)) }
                    }
                    results.isEmpty() -> Text(
                        stringResource(R.string.mods_empty),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(320.dp),
                        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(results, key = { _, it -> it.id }) { index, item ->
                            ModCard(
                                item = item,
                                st = status[item.id],
                                index = index,
                                onDownload = { download(item) },
                                onOpen = {
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.pageUrl)))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModCard(
    item: ModItem,
    st: ItemStatus?,
    index: Int,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val accent = if (item.source == Source.IDGAMES) Color(0xFFE65100) else Color(0xFF3F51B5)
    Card(Modifier.fillMaxWidth().slimeEntrance(item.id, (index * 40L).coerceAtMost(320L))) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(
                    item.author,
                    item.sizeText,
                    if (item.rating.isNotBlank()) "\u2605 " + item.rating else "",
                ).filter { it.isNotBlank() }.joinToString("  \u00B7  ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.description.isNotBlank()) {
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                when (st?.state) {
                    DOWNLOADING -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProgressBar(st.progress)
                        Text(
                            stringResource(R.string.mods_downloading, (st.progress * 100).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    INSTALLED -> Text(
                        "\u2713 " + stringResource(R.string.mods_installed),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    else -> Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onDownload,
                                interactionSource = src,
                                modifier = Modifier.slimePress(src),
                            ) { Text(stringResource(R.string.mods_download)) }
                            if (item.pageUrl.isNotBlank()) {
                                TextButton(onClick = onOpen) { Text(stringResource(R.string.mods_open_page)) }
                            }
                        }
                        if (st?.state == FAILED) {
                            Text(
                                st.msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
