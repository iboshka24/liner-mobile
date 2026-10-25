package dev.lyra.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dev.lyra.player.model.AlbumGroup
import dev.lyra.player.model.ArtistGroup
import dev.lyra.player.model.MusicIndex
import dev.lyra.player.model.Track
import dev.lyra.player.model.TrackSource

class LibraryRepository(private val context: Context) {
    fun load(): MusicIndex {
        val tracks = try {
            query(filterSystemFolders = true)
        } catch (error: RuntimeException) {
            Log.w(TAG, "library query retried without folder filter", error)
            query(filterSystemFolders = false)
        }
        return index(tracks)
    }

    private fun query(filterSystemFolders: Boolean): List<Track> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = buildString {
            append(MediaStore.Audio.Media.IS_MUSIC)
            append(" != 0 AND ")
            append(MediaStore.Audio.Media.DURATION)
            append(" >= 5000")
            if (filterSystemFolders && Build.VERSION.SDK_INT >= 29) {
                val path = MediaStore.Audio.Media.RELATIVE_PATH
                append(" AND (")
                append(path)
                append(" IS NULL OR (")
                append(path)
                append(" NOT LIKE 'Ringtones/%' AND ")
                append(path)
                append(" NOT LIKE 'Notifications/%' AND ")
                append(path)
                append(" NOT LIKE 'Alarms/%'))")
            }
        }
        val tracks = ArrayList<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = clean(cursor.getString(titleCol), "Untitled")
                val artist = clean(cursor.getString(artistCol), "Unknown")
                val album = clean(cursor.getString(albumCol), "")
                val albumId = cursor.getLong(albumIdCol)
                val content = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                ).toString()
                val art = if (albumId > 0) {
                    ContentUris.withAppendedId(ALBUM_ART, albumId).toString()
                } else {
                    null
                }
                tracks += Track(
                    id = "local:$id",
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = cursor.getLong(durationCol).coerceAtLeast(0),
                    source = TrackSource.LOCAL,
                    uri = content,
                    artworkUri = art,
                    albumKey = if (albumId > 0) {
                        "album:$albumId"
                    } else {
                        "name:${album.lowercase()}|${artist.lowercase()}"
                    },
                    dateAdded = cursor.getLong(addedCol),
                    cacheable = false,
                )
            }
        }
        return tracks
    }

    private fun index(tracks: List<Track>): MusicIndex {
        val albums = tracks.groupBy { it.albumKey }.map { (key, songs) ->
            val ordered = songs.sortedBy { it.title.lowercase() }
            val first = ordered.first()
            AlbumGroup(
                key = key,
                title = first.album.ifBlank { "Unknown album" },
                artist = first.artist,
                artworkUri = ordered.firstNotNullOfOrNull { it.artworkUri },
                tracks = ordered,
            )
        }.sortedBy { it.title.lowercase() }
        val artists = tracks.groupBy { it.artist }.map { (name, songs) ->
            ArtistGroup(name, songs.sortedBy { it.title.lowercase() })
        }.sortedBy { it.name.lowercase() }
        val recent = tracks.sortedByDescending { it.dateAdded }.take(40)
        val recentAlbums = albums.sortedByDescending { album ->
            album.tracks.maxOfOrNull { it.dateAdded } ?: 0L
        }.take(12)
        return MusicIndex(
            tracks = tracks.sortedBy { it.title.lowercase() },
            albums = albums,
            artists = artists,
            recent = recent,
            recentAlbums = recentAlbums,
        )
    }

    private fun clean(raw: String?, fallback: String): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.equals("<unknown>", true)) return fallback
        return value
    }

    private companion object {
        const val TAG = "Lyra"
        val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
