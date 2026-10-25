package dev.lyra.player.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrackSource {
    LOCAL,
    AUDIUS,
    ARCHIVE,
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val source: TrackSource = TrackSource.LOCAL,
    val uri: String,
    val artworkUri: String? = null,
    val albumKey: String = "",
    val dateAdded: Long = 0,
    val cacheable: Boolean = false,
) {
    fun playbackUri(): String {
        if (source == TrackSource.AUDIUS && cacheable && "#lyra-cache" !in uri) {
            return "$uri#lyra-cache"
        }
        return uri
    }
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = 0,
)

@Serializable
data class SavedSession(
    val tracks: List<Track> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
)

data class AlbumGroup(
    val key: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val tracks: List<Track>,
)

data class ArtistGroup(
    val name: String,
    val tracks: List<Track>,
)

data class MusicIndex(
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val recent: List<Track> = emptyList(),
    val recentAlbums: List<AlbumGroup> = emptyList(),
)

object LyraLinks {
    const val USER_AGENT = "Lyra/0.1.0 (https://github.com/iboshka24/liner-mobile)"
    const val AUDIUS_APP = "lyra"

    fun audiusStream(id: String): String =
        "https://api.audius.co/v1/tracks/$id/stream?app_name=$AUDIUS_APP"

    fun archiveUri(identifier: String): String = "lyra://archive/$identifier"
}

object CachePolicy {
    fun shouldCache(uri: String): Boolean {
        val fragment = uri.substringAfter('#', missingDelimiterValue = "")
        if (fragment == "lyra-cache") return true
        if (uri.startsWith("lyra://archive/")) return true
        val host = uri.substringAfter("://", "")
            .substringBefore('/')
            .substringBefore(':')
            .substringBefore('#')
        return host == "archive.org" || host.endsWith(".archive.org")
    }
}

object QueryText {
    val ARCHIVE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,120}$")
    val AUDIUS_ID = Regex("^[A-Za-z0-9_-]{2,64}$")

    fun lucene(raw: String): String =
        raw.replace(Regex("""[+\-!(){}\[\]^"~*?:\\/&|]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)

    fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

fun formatClock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
