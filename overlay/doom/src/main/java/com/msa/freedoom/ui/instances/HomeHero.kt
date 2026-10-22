package com.msa.freedoom.ui.instances

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msa.freedoom.BuildConfig
import com.msa.freedoom.R
import com.msa.freedoom.ui.fx.HomeAccent
import com.msa.freedoom.ui.fx.slimeEntrance
import com.msa.freedoom.ui.fx.slimePress
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BG_FILE = "home_bg.jpg"

/** Decodes the picked image at a sane size and keeps one JPEG copy inside the app. */
private fun saveBackground(ctx: Context, uri: Uri): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: return@runCatching false
    File(ctx.filesDir, BG_FILE).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    bmp.recycle()
    true
}.getOrDefault(false)

private fun freeRamGb(ctx: Context): String {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return String.format("%.1f", info.availMem / 1_073_741_824.0)
}

/**
 * The big card at the top of Home: the selected instance with its name, what it loads, a PLAY
 * button and shortcuts, over a background you can set yourself.
 */
@Composable
fun HomeHero(
    inst: Instance?,
    instances: List<Instance>,
    onSelect: (String) -> Unit,
    onPlay: () -> Unit,
    onMods: () -> Unit,
    onSettings: () -> Unit,
    onNew: () -> Unit,
    onChangelog: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp >= 600
    val heroH = (config.screenHeightDp * 0.62f).dp.coerceIn(240.dp, 460.dp)
    val (c1, c2) = instanceColors(inst?.id ?: "none")
    var bgVersion by remember { mutableStateOf(0) }
    val bg by produceState<ImageBitmap?>(null, bgVersion) {
        value = withContext(Dispatchers.IO) {
            val f = File(ctx.filesDir, BG_FILE)
            if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
        }
    }
    var chipMenu by remember { mutableStateOf(false) }
    var bgMenu by remember { mutableStateOf(false) }
    val playSrc = remember { MutableInteractionSource() }
    val ramGb = remember(inst?.id) { freeRamGb(ctx) }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                if (withContext(Dispatchers.IO) { saveBackground(ctx, uri) }) bgVersion++
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(heroH)
            .slimeEntrance("hero")
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(c1, c2)))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp)),
    ) {
        bg?.let {
            Image(it, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x11000000), Color(0xCC000000)))),
        )

        // Top left: which instance this card shows.
        Box(Modifier.align(Alignment.TopStart).padding(14.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66000000))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(50))
                    .clickable { chipMenu = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (inst?.name ?: "?").take(1).uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    inst?.name ?: stringResource(R.string.instance_none),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("\u25BE", color = Color.White)
            }
            DropdownMenu(expanded = chipMenu, onDismissRequest = { chipMenu = false }) {
                instances.forEach { i ->
                    DropdownMenuItem(text = { Text(i.name) }, onClick = { chipMenu = false; onSelect(i.id) })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.instance_new)) },
                    onClick = { chipMenu = false; onNew() },
                )
            }
        }

        // Top right: version badge (opens the changelog) and the background picker.
        Row(
            Modifier.align(Alignment.TopEnd).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66000000))
                    .clickable(onClick = onChangelog)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF9800)))
                Spacer(Modifier.width(6.dp))
                Text("v" + BuildConfig.VERSION_NAME, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box {
                CircleButton(R.drawable.ic_image) { bgMenu = true }
                DropdownMenu(expanded = bgMenu, onDismissRequest = { bgMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_bg_choose)) },
                        onClick = { bgMenu = false; pickBg.launch(arrayOf("image/*")) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_bg_remove)) },
                        onClick = {
                            bgMenu = false
                            File(ctx.filesDir, BG_FILE).delete()
                            bgVersion++
                        },
                    )
                }
            }
        }

        if (inst != null) {
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text(
                    inst.name,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inst.iwadName, color = HomeAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "  |  " + stringResource(R.string.instance_cat_mods) + " " + inst.modRelPaths.size +
                            "  |  " + stringResource(R.string.instance_cat_maps) + " " + inst.mapRelPaths.size,
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp,
                    )
                }
                if (!wide) {
                    Spacer(Modifier.height(6.dp))
                    HeroInfo(ramGb, inst.layout, Alignment.Start)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPlay,
                        interactionSource = playSrc,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = HomeAccent, contentColor = Color.Black),
                        modifier = Modifier.slimePress(playSrc),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.instance_play).uppercase(), fontWeight = FontWeight.ExtraBold)
                    }
                    CircleButton(R.drawable.ic_folder, onMods)
                    CircleButton(R.drawable.ic_tune, onSettings)
                }
            }
            if (wide) {
                Box(Modifier.align(Alignment.BottomEnd).padding(horizontal = 22.dp, vertical = 18.dp)) {
                    HeroInfo(ramGb, inst.layout, Alignment.End)
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.instance_empty), color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onNew,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = HomeAccent, contentColor = Color.Black),
                ) { Text(stringResource(R.string.instance_new), fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun HeroInfo(ramGb: String, layout: String, horizontalAlignment: Alignment.Horizontal) {
    Column(horizontalAlignment = horizontalAlignment) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF43A047)))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.home_ram_free, ramGb), color = Color(0xCCFFFFFF), fontSize = 11.sp)
        }
        Text(
            stringResource(R.string.home_layout, layout.ifBlank { stringResource(R.string.home_layout_default) }),
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CircleButton(res: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .border(1.dp, Color(0x55FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(res), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
