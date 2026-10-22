package com.msa.freedoom.ui.mods

import com.msa.freedoom.idgames.IdgamesApi
import com.msa.freedoom.idgames.IdgamesDirClassifier
import com.msa.freedoom.idgames.IdgamesResult
import com.msa.freedoom.idgames.WadDownloader
import com.msa.freedoom.idgames.ZipInstaller
import com.msa.freedoom.ui.instances.Instance
import com.msa.freedoom.ui.instances.dataDir
import com.msa.freedoom.ui.launch.gameFolderName
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

enum class Source { IDGAMES, GITHUB }

/** One downloadable thing, whichever source it came from. */
data class ModItem(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val sizeText: String,
    val rating: String,
    val source: Source,
    val dir: String = "",
    val filename: String = "",
    val repo: String = "",
    val pageUrl: String = "",
)

sealed interface InstallResult {
    data class Done(val relPaths: List<String>) : InstallResult
    data class Failed(val reason: String) : InstallResult
}

@Serializable
private data class GhSearch(val items: List<GhRepo> = emptyList())

@Serializable
private data class GhRepo(
    val full_name: String = "",
    val name: String = "",
    val topics: List<String> = emptyList(),
    val description: String? = null,
    val stargazers_count: Int = 0,
    val html_url: String = "",
)

@Serializable
private data class GhRelease(val assets: List<GhAsset> = emptyList())

@Serializable
private data class GhAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
)

object ModSources {
    private val json = Json { ignoreUnknownKeys = true }
    private val DOWNLOAD_EXT = listOf("pk3", "pk7", "zip", "wad")

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        bytes > 0 -> "$bytes B"
        else -> ""
    }

    /** idgames: maps live under levels/, everything else (combos, themes, utils...) is a mod. */
    suspend fun searchIdgames(query: String, maps: Boolean): List<ModItem> {
        val api = IdgamesApi()
        val res = if (query.isBlank()) api.latest(40) else api.search(query, IdgamesApi.SearchType.TITLE)
        val files = when (res) {
            is IdgamesResult.Success -> res.value
            is IdgamesResult.ApiError -> throw IOException(res.message)
        }
        return files
            .filter { (it.dir.startsWith("levels/")) == maps }
            .map {
                ModItem(
                    id = "idg:${it.id}",
                    title = it.title?.takeIf { t -> t.isNotBlank() } ?: it.filename,
                    author = it.author.orEmpty(),
                    description = it.description.orEmpty(),
                    sizeText = formatSize(it.size),
                    rating = if (it.votes > 0) String.format("%.1f", it.rating) else "",
                    source = Source.IDGAMES,
                    dir = it.dir,
                    filename = it.filename,
                    pageUrl = "https://www.doomworld.com/idgames/?id=${it.id}",
                )
            }
    }

    // A plain topic:gzdoom search is mostly launchers, editors and ports. Ask for repositories that
    // tag themselves as mods, then drop anything that still reads like a tool.
    private val GITHUB_QUERIES = listOf(
        "topic:gzdoom-mod",
        "topic:doom-mod",
        "topic:zdoom-mod",
        "gzdoom pk3 mod in:name,description,topics",
    )
    private val MOD_TOPICS = setOf("gzdoom-mod", "doom-mod", "zdoom-mod", "uzdoom-mod", "doom-wad", "pk3", "wad", "mod", "mods")
    private val TOOL_WORDS = Regex(
        "launcher|editor|tool|engine|source.?port|android|\\bios\\b|frontend|front-end|manager|converter|compiler|" +
            "server|language|\\blsp\\b|library|\\bsdk\\b|framework|installer|updater|builder|generator|\\bgui\\b|" +
            "wrapper|plugin|extension|vscode|syntax|highlight|wiki|tutorial|template|boilerplate",
        RegexOption.IGNORE_CASE,
    )

    private fun looksLikeMod(r: GhRepo): Boolean {
        val topics = r.topics.map { it.lowercase() }
        if (topics.any { it in MOD_TOPICS }) return true
        val text = r.full_name.substringAfter('/') + " " + r.description.orEmpty() + " " + topics.joinToString(" ")
        return !TOOL_WORDS.containsMatchIn(text)
    }

    suspend fun searchGithub(query: String, modsOnly: Boolean): List<ModItem> {
        val seen = LinkedHashMap<String, GhRepo>()
        var failure: IOException? = null
        for (extra in GITHUB_QUERIES) {
            val q = (if (query.isBlank()) "" else query.trim() + " ") + extra + " archived:false fork:false"
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("api.github.com").addPathSegments("search/repositories")
                .addQueryParameter("q", q)
                .addQueryParameter("sort", "stars")
                .addQueryParameter("order", "desc")
                .addQueryParameter("per_page", "30")
                .build()
            try {
                json.decodeFromString<GhSearch>(httpGet(url.toString())).items.forEach { seen.putIfAbsent(it.full_name, it) }
            } catch (e: IOException) {
                failure = e
            }
        }
        val failed = failure
        if (seen.isEmpty() && failed != null) throw failed
        return seen.values
            .filter { !modsOnly || looksLikeMod(it) }
            .sortedByDescending { it.stargazers_count }
            .take(40)
            .map {
                ModItem(
                    id = "gh:${it.full_name}",
                    title = it.full_name.substringAfter('/'),
                    author = it.full_name.substringBefore('/'),
                    description = it.description.orEmpty(),
                    sizeText = "",
                    rating = it.stargazers_count.toString(),
                    source = Source.GITHUB,
                    repo = it.full_name,
                    pageUrl = it.html_url,
                )
            }
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        IdgamesApi.sharedClient.newCall(req).execute().use { r ->
            if (r.code == 403 || r.code == 429) {
                throw IOException("GitHub rate limit reached. Wait a minute and try again.")
            }
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    private fun downloadFile(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val req = Request.Builder().url(url).build()
        IdgamesApi.sharedClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(32 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        }
    }

    /**
     * Installs [item] into the instance's own folder (`instances/<id>/<kind>/`) and returns the
     * content files as paths relative to the game dir, ready to add to the instance.
     */
    suspend fun install(
        baseDir: String,
        inst: Instance,
        kind: String,
        item: ModItem,
        onProgress: (Long, Long) -> Unit,
    ): InstallResult = withContext(Dispatchers.IO) {
        val destRoot = File(inst.dataDir(baseDir), kind)
        val relBase = "instances/${inst.id}/$kind"
        try {
            when (item.source) {
                Source.IDGAMES -> {
                    val guess = gameFolderName(IdgamesDirClassifier.classify(item.dir, item.filename))
                    when (val r = WadDownloader().downloadAndInstall(item.dir, item.filename, destRoot, guess, onProgress)) {
                        is WadDownloader.Result.Installed -> InstallResult.Done(
                            r.contentFiles.map { "$relBase/${r.gameFolder}/${r.installName}/$it" },
                        )
                        is WadDownloader.Result.Failed -> InstallResult.Failed(
                            if (r.reason == WadDownloader.FailReason.ALL_MIRRORS_FAILED) {
                                "All mirrors failed"
                            } else {
                                "Nothing playable in that file"
                            },
                        )
                    }
                }
                Source.GITHUB -> installGithub(item, destRoot, relBase, onProgress)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            InstallResult.Failed(e.message ?: "Download failed")
        }
    }

    private suspend fun installGithub(
        item: ModItem,
        destRoot: File,
        relBase: String,
        onProgress: (Long, Long) -> Unit,
    ): InstallResult {
        val release = json.decodeFromString<GhRelease>(
            httpGet("https://api.github.com/repos/${item.repo}/releases/latest"),
        )
        val asset = release.assets
            .filter { it.name.substringAfterLast('.').lowercase() in DOWNLOAD_EXT }
            .minByOrNull { DOWNLOAD_EXT.indexOf(it.name.substringAfterLast('.').lowercase()) }
            ?: return InstallResult.Failed("The latest release has no downloadable file")
        val folder = "github/" + item.repo.replace('/', '_')
        val dir = File(destRoot, folder).apply { mkdirs() }
        val part = File(dir, asset.name + ".part")
        withContext(Dispatchers.IO) { downloadFile(asset.browser_download_url, part, onProgress) }
        return if (asset.name.lowercase().endsWith(".zip")) {
            val files = ZipInstaller.install(part, dir)
            part.delete()
            if (files.isEmpty()) {
                InstallResult.Failed("Nothing playable in that file")
            } else {
                InstallResult.Done(files.map { "$relBase/$folder/$it" })
            }
        } else {
            val dest = File(dir, asset.name)
            if (!part.renameTo(dest)) {
                part.delete()
                InstallResult.Failed("Could not save the file")
            } else {
                InstallResult.Done(listOf("$relBase/$folder/${asset.name}"))
            }
        }
    }
}
