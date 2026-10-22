package com.msa.freedoom.ui.changelog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msa.freedoom.AppSettings
import com.msa.freedoom.BuildConfig
import com.msa.freedoom.R

/** Reads the bundled changelog and remembers which release the user has already seen. */
object ChangelogStore {
    private const val SEEN_KEY = "changelog_seen_version"

    /** Release number without the per-run suffix: "1.4.0-r12" becomes "1.4.0". */
    fun releaseVersion(): String = BuildConfig.VERSION_NAME.substringBefore("-r")

    fun shouldAutoShow(ctx: Context): Boolean =
        AppSettings.getStringOption(ctx, SEEN_KEY, null) != releaseVersion()

    fun markSeen(ctx: Context) = AppSettings.setStringOption(ctx, SEEN_KEY, releaseVersion())

    /** CHANGELOG.md is copied into the app's assets at build time; null if it was not. */
    fun load(ctx: Context): String? =
        runCatching { ctx.assets.open("changelog.md").bufferedReader().use { it.readText() } }.getOrNull()
}

/** One displayed line: 'h' heading, 'b' bullet, 'p' paragraph. */
private fun parse(md: String, latestOnly: Boolean): List<Pair<Char, String>> {
    val out = mutableListOf<Pair<Char, String>>()
    var sections = 0
    for (raw in md.lines()) {
        val line = raw.trimEnd()
        // An indented line continues the bullet above it.
        if (raw.startsWith("  ") && line.isNotBlank() && out.isNotEmpty() && out.last().first == 'b') {
            out[out.lastIndex] = 'b' to (out.last().second + " " + line.trim())
            continue
        }
        when {
            line.startsWith("## ") -> {
                sections++
                if (latestOnly && sections > 1) break
                out += 'h' to line.removePrefix("## ")
            }
            line.startsWith("# ") || line.isBlank() -> Unit
            line.startsWith("- ") -> if (sections >= 1) out += 'b' to line.removePrefix("- ")
            else -> if (!latestOnly) out += 'p' to line.trim()
        }
    }
    return out
}

/** [latestOnly] shows just the newest release (auto popup); otherwise the whole history. */
@Composable
fun ChangelogDialog(latestOnly: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember(latestOnly) {
        ChangelogStore.load(ctx)?.let { parse(it, latestOnly) } ?: emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (latestOnly) R.string.changelog_whats_new else R.string.changelog_title))
        },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.changelog_unavailable))
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEach { (kind, text) ->
                        when (kind) {
                            'h' -> Text(
                                text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            'b' -> Row {
                                Text("\u2022")
                                Spacer(Modifier.width(8.dp))
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                            else -> Text(
                                text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.changelog_close)) }
        },
    )
}
