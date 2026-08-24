package com.pryvn.audiophile.code.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class PaxsenixLyricsResponse(
    val lyrics: String,
    val source: String,
    val isWordSynced: Boolean = false,
    val trackId: String? = null,
    val durationMs: Long? = null,
)

@Serializable
data class PaxsenixSearchResult(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artistName: String? = null,
    val durationMs: Long? = null,
    val duration: Int? = null,
    val album: String? = null,
    val albumName: String? = null,
    val realId: String? = null,
)

@Serializable
data class PaxsenixSearchResponse(
    val results: List<PaxsenixSearchResult>? = null,
    val data: List<PaxsenixSearchResult>? = null,
    val items: List<PaxsenixSearchResult>? = null,
    val tracks: PaxsenixTracks? = null,
)

@Serializable
data class PaxsenixTracks(
    val items: List<PaxsenixSearchResult>? = null,
)

@Serializable
data class PaxsenixAppleMusicLyricsResponse(
    val lyrics: String? = null,
    val ttml: String? = null,
    val content: String? = null,
    val type: String? = null,
)

@Serializable
data class PaxsenixMusixmatchResponse(
    val lyrics: String? = null,
    val content: String? = null,
)

@Serializable
data class PaxsenixNetEaseLyricsResponse(
    val lrc: PaxsenixNetEaseLrc? = null,
    val tlyric: PaxsenixNetEaseLrc? = null,
    val klyric: PaxsenixNetEaseLrc? = null,
)

@Serializable
data class PaxsenixNetEaseLrc(
    val lyric: String? = null,
)

@Serializable
data class PaxsenixDeezerResponse(
    val lyrics: String? = null,
    val content: String? = null,
)

@Serializable
data class PaxsenixStatsResponse(
    val totalTracks: Long = 0,
    val totalLyrics: Long = 0,
)

data class ParsedWord(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isBackground: Boolean = false,
)

data class ParsedWordSyncedLine(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: List<ParsedWord> = emptyList(),
    val voice: String = "v1",
)

data class ParsedLineSynced(
    val startTimeMs: Long,
    val text: String,
)