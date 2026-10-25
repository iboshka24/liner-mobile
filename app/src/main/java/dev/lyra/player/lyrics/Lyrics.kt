package dev.lyra.player.lyrics

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord>,
)

data class WordState(
    val index: Int,
    val progress: Float,
)

sealed interface LyricResult {
    data object Instrumental : LyricResult
    data class Synced(val lines: List<LyricLine>) : LyricResult
    data class Plain(val text: String) : LyricResult
    data object Missing : LyricResult
}

object LyricsSync {
    fun lineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    fun wordState(line: LyricLine, positionMs: Long): WordState {
        val words = line.words
        if (words.isEmpty()) return WordState(-1, 0f)
        if (positionMs <= words.first().startMs) return WordState(0, 0f)
        val index = words.indexOfLast { positionMs >= it.startMs }.coerceAtLeast(0)
        val word = words[index]
        if (positionMs >= word.endMs) return WordState(index, 1f)
        val span = (word.endMs - word.startMs).coerceAtLeast(1L)
        val progress = ((positionMs - word.startMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return WordState(index, progress)
    }

    /**
     * Spreads words across a line when the file only has a line timestamp.
     * Longer words (by letters and digits) keep a larger share of the line.
     * The first word starts at [startMs] and the last word ends at [endMs].
     */
    fun interpolate(text: String, startMs: Long, endMs: Long): List<LyricWord> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val safeEnd = if (endMs > startMs) endMs else startMs + words.size
        val span = (safeEnd - startMs).coerceAtLeast(1L)
        val weights = words.map { word -> word.count { it.isLetterOrDigit() }.coerceAtLeast(1) }
        val total = weights.sum().toLong().coerceAtLeast(1L)
        var cursor = startMs
        var used = 0
        return words.mapIndexed { index, word ->
            used += weights[index]
            val end = if (index == words.lastIndex) safeEnd else startMs + span * used / total
            val wordEnd = end.coerceAtLeast(cursor)
            LyricWord(word, cursor, wordEnd).also { cursor = wordEnd }
        }
    }
}

object LrcParser {
    private val leadingStamp = Regex("""\[(\d+):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordStamp = Regex("""<(\d+):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""(?i)^\[offset:\s*([+-]?\d+)\s*]$""")
    private val metaTag = Regex("""^\[[A-Za-z]+:.*]$""")

    fun parse(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        var offset = 0L
        val drafts = mutableListOf<Draft>()
        raw.replace("\uFEFF", "").split('\n').forEach { original ->
            val line = original.trim().trimEnd('\r')
            if (line.isEmpty()) return@forEach
            offsetTag.matchEntire(line)?.let { match ->
                offset = match.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            if (metaTag.matches(line) && leadingStamp.matchAt(line, 0) == null) return@forEach
            var rest = line.trimStart()
            val starts = mutableListOf<Long>()
            while (true) {
                val match = leadingStamp.matchAt(rest, 0) ?: break
                starts += timestampMs(match.groupValues)
                rest = rest.substring(match.range.last + 1).trimStart()
            }
            if (starts.isEmpty()) return@forEach
            val body = splitWords(rest.trim())
            starts.forEach { start ->
                drafts += Draft(start, body.text, body.prefix, body.words)
            }
        }
        val shifted = drafts.map { draft ->
            draft.copy(
                startMs = draft.startMs + offset,
                words = draft.words.map { (time, text) -> (time + offset) to text },
            )
        }.sortedBy { it.startMs }
        return shifted.mapIndexed { index, draft ->
            val next = shifted.subList(index + 1, shifted.size).firstOrNull { it.startMs > draft.startMs }
            val end = (next?.startMs ?: (draft.startMs + 4_000L)).coerceAtLeast(draft.startMs)
            val explicit = buildList {
                if (draft.prefix.isNotBlank()) add(draft.startMs to draft.prefix)
                addAll(draft.words)
            }
            val words = if (explicit.isEmpty()) {
                LyricsSync.interpolate(draft.text, draft.startMs, end)
            } else {
                closeWords(explicit, end)
            }
            LyricLine(draft.startMs, end, draft.text, words)
        }.filter { it.text.isNotBlank() }
    }

    private data class Draft(
        val startMs: Long,
        val text: String,
        val prefix: String,
        val words: List<Pair<Long, String>>,
    )

    private data class Body(
        val text: String,
        val prefix: String,
        val words: List<Pair<Long, String>>,
    )

    private fun splitWords(body: String): Body {
        if ('<' !in body) return Body(body, "", emptyList())
        val marks = wordStamp.findAll(body).toList()
        if (marks.isEmpty()) return Body(body, "", emptyList())
        val prefix = body.substring(0, marks.first().range.first).trim()
        val words = marks.mapIndexedNotNull { index, mark ->
            val from = mark.range.last + 1
            val to = if (index + 1 < marks.size) marks[index + 1].range.first else body.length
            val text = body.substring(from, to).trim()
            if (text.isEmpty()) null else timestampMs(mark.groupValues) to text
        }
        val text = buildList {
            if (prefix.isNotEmpty()) add(prefix)
            words.forEach { add(it.second) }
        }.joinToString(" ")
        return Body(text, prefix, words)
    }

    private fun closeWords(words: List<Pair<Long, String>>, lineEnd: Long): List<LyricWord> {
        return words.mapIndexed { index, (start, text) ->
            val next = words.getOrNull(index + 1)?.first
            val end = when {
                next != null && next > start -> next
                next != null -> start + 1
                else -> lineEnd.coerceAtLeast(start)
            }
            LyricWord(text, start, if (end == start) start + 1 else end)
        }
    }

    private fun timestampMs(groups: List<String>): Long {
        val minutes = groups[1].toLongOrNull() ?: 0L
        val seconds = groups[2].toLongOrNull() ?: 0L
        val fraction = groups.getOrNull(3).orEmpty()
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
