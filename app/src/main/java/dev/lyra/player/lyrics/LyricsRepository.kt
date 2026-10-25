package dev.lyra.player.lyrics

import android.content.Context
import android.util.Log
import dev.lyra.player.data.HttpStatusException
import dev.lyra.player.data.Net
import dev.lyra.player.data.retrying
import dev.lyra.player.model.QueryText
import dev.lyra.player.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class LyricsRepository(
    context: Context,
    private val net: Net,
) {
    private val cacheDir = File(context.filesDir, "lyrics-cache").apply { mkdirs() }
    private val localDir = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "lyrics-local",
    ).apply { mkdirs() }
    private val memory = object : LinkedHashMap<String, LyricResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricResult>?) = size > 8
    }
    private val lock = Any()

    fun load(track: Track): LyricResult {
        synchronized(lock) { memory[track.id]?.let { return it } }
        readFile(localFile(track))?.let { return remember(track.id, it) }
        readFile(namedFile(track))?.let { return remember(track.id, it) }
        readCache(track)?.let { return remember(track.id, it) }
        val remote = fetch(track)
        if (remote != null) {
            writeCache(track, remote)
            return remember(track.id, remote)
        }
        val missing = LyricResult.Missing
        writeCache(track, missing)
        return remember(track.id, missing)
    }

    fun saveImport(track: Track, lrc: String) {
        val text = lrc.take(200_000)
        localFile(track).writeText(text)
        cacheFile(track).delete()
        synchronized(lock) { memory.remove(track.id) }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        synchronized(lock) { memory.clear() }
    }

    fun cacheBytes(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun fetch(track: Track): LyricResult? {
        if (track.title.isBlank()) return null
        val direct = runCatching { retrying { net.get(getUrl(track)) } }
        direct.onSuccess { body -> parsePayload(body)?.let { return it } }
        val error = direct.exceptionOrNull()
        if (error != null && error !is HttpStatusException) throw error
        if (error is HttpStatusException && error.code != 404) throw error
        return search(track)
    }

    private fun search(track: Track): LyricResult? {
        val url = "https://lrclib.net/api/search?track_name=${QueryText.encode(track.title)}" +
            "&artist_name=${QueryText.encode(track.artist)}"
        val body = try {
            retrying { net.get(url) }
        } catch (error: HttpStatusException) {
            if (error.code == 404) return null
            throw error
        }
        val array = JSONArray(body)
        var best: JSONObject? = null
        var bestScore = Long.MAX_VALUE
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optBoolean("instrumental")) {
                best = item
                break
            }
            val duration = item.optLong("duration") * 1000L
            val score = if (track.durationMs > 0 && duration > 0) {
                kotlin.math.abs(duration - track.durationMs)
            } else if (!item.optString("syncedLyrics").isNullOrBlank()) {
                0L
            } else {
                60_000L
            }
            if (score < bestScore) {
                best = item
                bestScore = score
            }
        }
        return best?.let { parsePayload(it.toString()) }
    }

    private fun parsePayload(body: String): LyricResult? {
        val json = JSONObject(body)
        if (json.optBoolean("instrumental")) return LyricResult.Instrumental
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        if (synced != null) {
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return LyricResult.Synced(lines)
        }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
        if (plain != null) return LyricResult.Plain(plain)
        return null
    }

    private fun getUrl(track: Track): String = buildString {
        append("https://lrclib.net/api/get?track_name=")
        append(QueryText.encode(track.title))
        append("&artist_name=")
        append(QueryText.encode(track.artist))
        if (track.album.isNotBlank()) {
            append("&album_name=")
            append(QueryText.encode(track.album))
        }
        if (track.durationMs > 0) {
            append("&duration=")
            append(track.durationMs / 1000)
        }
    }

    private fun readCache(track: Track): LyricResult? {
        val file = cacheFile(track)
        if (!file.exists()) return null
        return runCatching { readStored(file.readText()) }.getOrElse {
            Log.w(TAG, "dropped unreadable lyrics cache")
            file.delete()
            null
        }
    }

    private fun readFile(file: File): LyricResult? {
        if (!file.exists()) return null
        val lines = LrcParser.parse(file.readText())
        return if (lines.isEmpty()) null else LyricResult.Synced(lines)
    }

    private fun writeCache(track: Track, result: LyricResult) {
        val json = JSONObject()
        when (result) {
            LyricResult.Instrumental -> json.put("kind", "instrumental")
            is LyricResult.Synced -> {
                json.put("kind", "synced")
                json.put("lrc", toLrc(result.lines))
            }
            is LyricResult.Plain -> {
                json.put("kind", "plain")
                json.put("text", result.text)
            }
            LyricResult.Missing -> json.put("kind", "missing")
        }
        val file = cacheFile(track)
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }

    private fun readStored(text: String): LyricResult? {
        val json = JSONObject(text)
        return when (json.optString("kind")) {
            "instrumental" -> LyricResult.Instrumental
            "plain" -> LyricResult.Plain(json.optString("text"))
            "missing" -> LyricResult.Missing
            "synced" -> {
                val lines = LrcParser.parse(json.optString("lrc"))
                if (lines.isEmpty()) null else LyricResult.Synced(lines)
            }
            else -> null
        }
    }

    private fun toLrc(lines: List<LyricLine>): String = buildString {
        lines.forEach { line ->
            if (line.words.isEmpty()) {
                append(stamp(line.startMs))
                append(line.text)
                append('\n')
            } else {
                append(stamp(line.startMs))
                line.words.forEach { word ->
                    append(stamp(word.startMs, word = true))
                    append(word.text)
                    append(' ')
                }
                append('\n')
            }
        }
    }

    private fun stamp(ms: Long, word: Boolean = false): String {
        val safe = ms.coerceAtLeast(0L)
        val minutes = safe / 60_000
        val seconds = (safe % 60_000) / 1000
        val fraction = (safe % 1000) / 10
        val body = "%02d:%02d.%02d".format(minutes, seconds, fraction)
        return if (word) "<$body>" else "[$body]"
    }

    private fun remember(id: String, result: LyricResult): LyricResult {
        synchronized(lock) { memory[id] = result }
        return result
    }

    private fun cacheFile(track: Track) = File(cacheDir, digest(track.id) + ".json")
    private fun localFile(track: Track) = File(localDir, "id-" + digest(track.id) + ".lrc")

    private fun namedFile(track: Track): File {
        val slug = "${track.artist}-${track.title}".lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "track" }
        return File(localDir, "$slug.lrc")
    }

    private fun digest(text: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "Lyra"
    }
}
