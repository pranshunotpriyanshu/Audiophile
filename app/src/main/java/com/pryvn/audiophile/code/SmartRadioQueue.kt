package com.pryvn.audiophile.code

import android.util.Log
import com.pryvn.audiophile.code.api.innertube.YouTube
import com.pryvn.audiophile.code.api.innertube.SearchFilter
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.models.WatchEndpoint

object SmartRadioQueue {
    private const val TAG = "SmartRadioQueue"
    private const val MIN_QUEUE_SIZE = 50
    private const val PREFERRED_QUEUE_SIZE = 75

    @Volatile
    var currentRadioSeedId: String? = null
        private set

    fun startNewRadioSession(seedVideoId: String) {
        currentRadioSeedId = seedVideoId
    }

    fun isCurrentRadioSession(seedVideoId: String): Boolean {
        return currentRadioSeedId == seedVideoId
    }

    suspend fun fetchRecommendations(
        seedVideoId: String,
        seedArtistIds: List<String?>,
        seedArtistNames: List<String>,
    ): List<SongItem> {
        Log.d(TAG, "fetchRecommendations: seed=$seedVideoId artists=$seedArtistNames")

        val relatedSongs = tryFetchRelatedSongs(seedVideoId)
        if (relatedSongs.size >= MIN_QUEUE_SIZE) {
            Log.d(TAG, "Using related endpoint: ${relatedSongs.size} songs")
            return relatedSongs.take(PREFERRED_QUEUE_SIZE)
        }

        val artistSongs = tryFetchArtistTopSongs(seedArtistIds, seedArtistNames)
        if (artistSongs.size >= MIN_QUEUE_SIZE) {
            Log.d(TAG, "Using artist top songs: ${artistSongs.size} songs")
            return artistSongs.take(PREFERRED_QUEUE_SIZE)
        }

        val combined = (relatedSongs + artistSongs).distinctBy { it.id }
        if (combined.size >= MIN_QUEUE_SIZE) {
            Log.d(TAG, "Using combined related+artist: ${combined.size} songs")
            return combined.take(PREFERRED_QUEUE_SIZE)
        }

        val excludeIds = combined.map { it.id }.toSet() + seedVideoId
        val searchSongs = tryFetchSearchBased(seedArtistNames, excludeIds)
        val finalList = (combined + searchSongs).distinctBy { it.id }
        if (finalList.isNotEmpty()) {
            Log.d(TAG, "Using combined+search: ${finalList.size} songs")
            return finalList.take(PREFERRED_QUEUE_SIZE)
        }

        return emptyList()
    }

    private suspend fun tryFetchRelatedSongs(seedVideoId: String): List<SongItem> {
        return runCatching {
            val watchEndpoint = WatchEndpoint(videoId = seedVideoId)
            val nextResult = YouTube.next(watchEndpoint).getOrNull() ?: return@runCatching emptyList()
            val relatedEndpoint = nextResult.relatedEndpoint ?: return@runCatching emptyList()
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return@runCatching emptyList()
            relatedPage.songs
        }.getOrElse { e ->
            Log.e(TAG, "tryFetchRelatedSongs failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun tryFetchArtistTopSongs(
        artistIds: List<String?>,
        artistNames: List<String>,
    ): List<SongItem> {
        val artistId = artistIds.firstOrNull { !it.isNullOrBlank() } ?: return emptyList()
        return runCatching {
            val page = YouTube.artist(artistId).getOrNull() ?: return@runCatching emptyList()
            val topSongsSection = page.sections.firstOrNull { section ->
                section.title.contains("Top songs", ignoreCase = true) ||
                section.title.contains("Popular", ignoreCase = true) ||
                section.title.contains("Songs", ignoreCase = true)
            } ?: return@runCatching emptyList()
            topSongsSection.items.filterIsInstance<SongItem>()
        }.getOrElse { e ->
            Log.e(TAG, "tryFetchArtistTopSongs failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun tryFetchSearchBased(
        artistNames: List<String>,
        excludeIds: Set<String>,
    ): List<SongItem> {
        val query = artistNames.firstOrNull() ?: return emptyList()
        return runCatching {
            val searchResult = YouTube.search(query, SearchFilter.FILTER_SONG).getOrNull() ?: return@runCatching emptyList()
            searchResult.items
                .filterIsInstance<SongItem>()
                .filter { it.id !in excludeIds }
        }.getOrElse { e ->
            Log.e(TAG, "tryFetchSearchBased failed: ${e.message}")
            emptyList()
        }
    }
}
