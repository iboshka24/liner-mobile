package dev.lyra.player.data

import android.util.Log
import dev.lyra.player.model.LyraLinks
import dev.lyra.player.model.QueryText
import dev.lyra.player.model.Track
import dev.lyra.player.model.TrackSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class AudiusRepository(private val net: Net) {
    fun search(query: String, limit: Int = 20): List<Track> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val url = "https://api.audius.co/v1/tracks/search?query=${QueryText.encode(q)}" +
            "&app_name=${LyraLinks.AUDIUS_APP}&limit=$limit"
        return parse(retrying { net.get(url) })
    }

    fun trending(limit: Int = 8): List<Track> {
        val url = "https://api.audius.co/v1/tracks/trending?app_name=${LyraLinks.AUDIUS_APP}&limit=$limit"
        return parse(retrying { net.get(url) })
    }

    private fun parse(body: String): List<Track> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Track>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            item.toTrack()?.let(out::add)
        }
        return out
    }

    private fun JSONObject.toTrack(): Track? {
        if (optBoolean("is_unlisted") || optBoolean("is_delete") || optBoolean("is_stream_gated")) return null
        if (has("is_available") && !optBoolean("is_available")) return null
        if (has("is_streamable") && !optBoolean("is_streamable")) return null
        val id = optString("id")
        if (!QueryText.AUDIUS_ID.matches(id)) return null
        val title = optString("title").trim()
        if (title.isEmpty()) return null
        val user = optJSONObject("user")
        val artist = user?.optString("name").orEmpty().trim()
            .ifBlank { user?.optString("handle").orEmpty().trim() }
            .ifBlank { "Unknown" }
        val artwork = optJSONObject("artwork")
        val art = artwork?.optString("480x480").takeUnless { it.isNullOrBlank() }
            ?: artwork?.optString("150x150").takeUnless { it.isNullOrBlank() }
        val downloadable = optBoolean("is_downloadable") && !optBoolean("is_download_gated")
        return Track(
            id = "audius:$id",
            title = title,
            artist = artist,
            album = "",
            durationMs = optLong("duration").coerceAtLeast(0L) * 1000L,
            source = TrackSource.AUDIUS,
            uri = LyraLinks.audiusStream(id),
            artworkUri = art,
            albumKey = "audius:${user?.optString("id").orEmpty()}",
            cacheable = downloadable,
        )
    }
}

class ArchiveRepository(
    private val net: Net,
    private val memoryFile: File,
) {
    private val lock = Any()
    private val memory = HashMap<String, String>()
    private var loaded = false

    fun search(query: String): List<Track> {
        val safe = QueryText.lucene(query)
        if (safe.length < 2) return emptyList()
        val q = "mediatype:audio AND licenseurl:*creativecommons.org* AND (title:(\"$safe\") OR creator:(\"$safe\"))"
        val url = "https://archive.org/advancedsearch.php?q=${QueryText.encode(q)}" +
            "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=licenseurl" +
            "&rows=12&page=1&output=json&sort[]=downloads+desc"
        val docs = JSONObject(retrying { net.get(url) })
            .optJSONObject("response")
            ?.optJSONArray("docs")
            ?: return emptyList()
        val out = ArrayList<Track>(docs.length())
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val identifier = text(doc, "identifier")
            val license = text(doc, "licenseurl")
            if (!QueryText.ARCHIVE_ID.matches(identifier)) continue
            if (!license.contains("creativecommons.org", ignoreCase = true)) continue
            val title = text(doc, "title").ifBlank { identifier }
            out += Track(
                id = "archive:$identifier",
                title = title,
                artist = text(doc, "creator").ifBlank { "Unknown" },
                album = "Internet Archive",
                source = TrackSource.ARCHIVE,
                uri = LyraLinks.archiveUri(identifier),
                cacheable = true,
            )
        }
        return out
    }

    fun resolveBlocking(identifier: String): String {
        if (!QueryText.ARCHIVE_ID.matches(identifier)) throw IOException("invalid archive id")
        synchronized(lock) {
            ensureLoaded()
            memory[identifier]?.let { return it }
            val body = net.get("https://archive.org/metadata/${QueryText.encode(identifier)}")
            val file = pickFile(JSONObject(body)) ?: throw IOException("no playable file")
            val encoded = QueryText.encode(file)
            val stream = "https://archive.org/download/$identifier/$encoded"
            memory[identifier] = stream
            persistLocked()
            return stream
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!memoryFile.exists()) return
        memoryFile.readLines().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val id = line.substring(0, tab)
            val url = line.substring(tab + 1)
            if (QueryText.ARCHIVE_ID.matches(id) && url.startsWith("https://archive.org/download/")) {
                memory[id] = url
            }
        }
    }

    private fun persistLocked() {
        val text = memory.entries.joinToString("\n") { (id, url) -> "$id\t$url" }
        memoryFile.parentFile?.mkdirs()
        val tmp = File(memoryFile.parentFile, memoryFile.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(memoryFile)) {
            memoryFile.writeText(text)
            tmp.delete()
        }
    }

    private fun pickFile(meta: JSONObject): String? {
        val files = meta.optJSONArray("files") ?: return null
        data class Candidate(val name: String, val size: Long, val rank: Int)
        val list = ArrayList<Candidate>()
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.optString("name")
            if (name.isBlank() || name.contains('/') || name.contains('\\')) continue
            val rank = when {
                name.endsWith(".mp3", true) -> 0
                name.endsWith(".opus", true) || name.endsWith(".ogg", true) -> 1
                name.endsWith(".m4a", true) || name.endsWith(".aac", true) -> 2
                name.endsWith(".flac", true) -> 3
                name.endsWith(".wav", true) -> 4
                else -> continue
            }
            val size = file.optString("size").toLongOrNull() ?: file.optLong("size")
            if (size <= 0L || size > 80L * 1024 * 1024) continue
            list += Candidate(name, size, rank)
        }
        val best = list.minOfOrNull { it.rank } ?: return null
        return list.filter { it.rank == best }.maxByOrNull { it.size }?.name
    }

    private fun text(doc: JSONObject, key: String): String {
        return when (val raw = doc.opt(key)) {
            is String -> raw.trim()
            is JSONArray -> (0 until raw.length())
                .mapNotNull { raw.optString(it).takeIf(String::isNotBlank) }
                .joinToString(", ")
            else -> ""
        }
    }

    companion object {
        private const val TAG = "Lyra"
        fun logFailure(message: String, error: Throwable) {
            Log.w(TAG, "$message: ${error.javaClass.simpleName}")
        }
    }
}
