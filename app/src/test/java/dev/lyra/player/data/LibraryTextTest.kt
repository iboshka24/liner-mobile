package dev.lyra.player.data

import dev.lyra.player.model.CachePolicy
import dev.lyra.player.model.QueryText
import dev.lyra.player.model.Track
import dev.lyra.player.model.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTextTest {
    @Test
    fun m3uMatchesFileNamesAndExtinfTitles() {
        val library = listOf(
            track("1", "Study in Brass"),
            track("2", "Other Song"),
        )
        val text = """
            #EXTM3U
            #EXTINF:20,Lyra - Study in Brass
            music/Study in Brass.mp3
            #EXTINF:10,Missing
            no-such-file.mp3
            #EXTINF:10,Other Song
            elsewhere/renamed.mp3
        """.trimIndent()
        assertEquals(listOf("1", "2"), M3u.match(text, library).map { it.id })
    }

    @Test
    fun m3uIgnoresCommentsAndDuplicates() {
        val library = listOf(track("1", "Alpha"))
        val text = "# comment\n\nAlpha.mp3\nfolder/Alpha.mp3\n"
        assertEquals(listOf("1"), M3u.match(text, library).map { it.id })
        assertTrue(M3u.match("", library).isEmpty())
    }

    @Test
    fun cachePolicyKeepsAudiusStreamingUnlessDownloadable() {
        assertFalse(CachePolicy.shouldCache("https://api.audius.co/v1/tracks/abc/stream?app_name=lyra"))
        assertTrue(CachePolicy.shouldCache("https://api.audius.co/v1/tracks/abc/stream?app_name=lyra#lyra-cache"))
        assertTrue(CachePolicy.shouldCache("lyra://archive/opensource-tape"))
        assertTrue(CachePolicy.shouldCache("https://archive.org/download/opensource-tape/a.mp3"))
        assertFalse(CachePolicy.shouldCache("content://media/external/audio/media/3"))
    }

    @Test
    fun luceneStripsOperatorsAndIdsStayPathSafe() {
        assertEquals("nocturne", QueryText.lucene("nocturne!"))
        assertEquals("night drive", QueryText.lucene("night && drive"))
        assertTrue(QueryText.ARCHIVE_ID.matches("opus-13.-no.-1"))
        assertFalse(QueryText.ARCHIVE_ID.matches("../etc"))
        assertTrue(QueryText.AUDIUS_ID.matches("O6bw9pR"))
        assertFalse(QueryText.AUDIUS_ID.matches("a/b"))
    }

    @Test
    fun playbackUriAddsCacheFragmentOnce() {
        val remote = track("audius:1", "A").copy(
            source = TrackSource.AUDIUS,
            uri = "https://api.audius.co/v1/tracks/abc/stream?app_name=lyra",
            cacheable = true,
        )
        val once = remote.playbackUri()
        assertTrue(once.endsWith("#lyra-cache"))
        assertEquals(once, remote.copy(uri = once).playbackUri())
        assertFalse(track("local:1", "A").playbackUri().contains("lyra-cache"))
    }

    private fun track(id: String, title: String) = Track(
        id = id,
        title = title,
        artist = "Lyra",
        uri = "content://media/$id",
    )
}
