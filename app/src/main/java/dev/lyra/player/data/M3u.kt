package dev.lyra.player.data

import dev.lyra.player.model.Track

object M3u {
    fun match(text: String, library: List<Track>): List<Track> {
        if (text.isBlank() || library.isEmpty()) return emptyList()
        val byTitle = HashMap<String, Track>(library.size)
        library.forEach { track ->
            byTitle.putIfAbsent(track.title.trim().lowercase(), track)
        }
        val found = LinkedHashMap<String, Track>()
        var pendingTitle: String? = null
        text.replace("\uFEFF", "").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingTitle = line.substringAfter(",", "").trim().ifBlank { null }
                return@forEach
            }
            if (line.startsWith("#")) {
                pendingTitle = null
                return@forEach
            }
            val stem = line.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').trim()
            val titled = pendingTitle
            val hit = byTitle[stem.lowercase()] ?: titled?.let { title ->
                byTitle[title.lowercase()] ?: byTitle[title.substringAfter(" - ").trim().lowercase()]
            }
            if (hit != null) found.putIfAbsent(hit.id, hit)
            pendingTitle = null
        }
        return found.values.toList()
    }
}
