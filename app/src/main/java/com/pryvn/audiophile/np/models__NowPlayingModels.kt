package com.pryvn.audiophile.np.models

enum class NowPlayingPage(val label: String) {
    Album("Album"),
    Lyrics("Lyric"),
    PlayingList("PlayingList");

    companion object {
        fun fromLabel(label: String): NowPlayingPage? = entries.firstOrNull { it.label == label }
    }
}

enum class NowPlayingLayoutMode {
    PhonePortrait,
    PhoneLandscape,
    ExpandedLandscape
}

data class QueueEntry(
    val mediaId: String?,
    val title: String,
    val artists: List<String>,
    val artworkUri: String?,
    val isCurrent: Boolean
)

interface NowPlayingSettingsSnapshot {
    val nowPlayingTranslation: Boolean
    val backgroundEffectEnabled: Boolean
    val showVolumeBar: Boolean
    val staticFullScreenAlbum: Boolean
    val fengShaderEnabled: Boolean
    val hapticFeedback: Boolean
    val barBlurEffect: Boolean
    val screenScale: Float
}

data class DefaultSettingsSnapshot(
    override val nowPlayingTranslation: Boolean = false,
    override val backgroundEffectEnabled: Boolean = true,
    override val showVolumeBar: Boolean = false,
    override val staticFullScreenAlbum: Boolean = false,
    override val fengShaderEnabled: Boolean = false,
    override val hapticFeedback: Boolean = true,
    override val barBlurEffect: Boolean = true,
    override val screenScale: Float = 1f
) : NowPlayingSettingsSnapshot

data class SeekRangeConverter(
    val rangeStart: Float,
    val rangeEnd: Float
) {
    fun positionToFraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    fun fractionToPosition(fraction: Float, durationMs: Long): Long =
        (fraction.coerceIn(0f, 1f) * durationMs).toLong()
}

