package com.pryvn.audiophile.data.libraries

import androidx.compose.runtime.Stable
import com.funny.data_saver.core.mutableDataSaverListStateOf
import com.funny.data_saver.core.mutableDataSaverStateOf
import com.pryvn.audiophile.data.SettingsSaver

@Stable
object SettingsLibrary {

    @Stable
    var FollowedArtists by mutableDataSaverListStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_followed_artists",
        initialValue = emptyList<String>(),
    )

    fun isArtistFollowed(artistName: String): Boolean {
        return FollowedArtists.contains(artistName)
    }

    fun followArtist(artistName: String)
    {
        if (!isArtistFollowed(artistName)) {
            FollowedArtists = FollowedArtists + artistName
        }
    }

    fun unfollowArtist(artistName: String)
    {
        FollowedArtists = FollowedArtists - artistName
    }

    fun toggleArtistFollowed(artistName: String)
    {
        if (isArtistFollowed(artistName)) {
            unfollowArtist(artistName)
        } else {
            followArtist(artistName)
        }
    }

    @Stable
    var ArtistSplitSeparators by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_artist_split_separators",
        initialValue = ""
    )

    @Stable
    var NowPlayingShowVolumeBar by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_show_volume_bar",
        initialValue = true
    )

    @Stable
    var CustomTheme by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_theme",
        initialValue = "Auto"
    )

    @Stable
    var ScreenCornerSet by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner_set",
        initialValue = true
    )

    @Stable
    var ScreenCorner by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner",
        initialValue = "30"
    )

    @Stable
    var SongSort by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_song_sort",
        initialValue = SongSortEnum.MUSIC_TITLE.ordinal
    )

    @Stable
    enum class SongSortEnum {
        MUSIC_TITLE, MUSIC_DURATION, ARTIST_NAME, MODIFIED_DATE
    }

    @Stable
    var EnableDescending by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_enable_descending",
        initialValue = false
    )

    @Stable
    var NowPlayingTranslation by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "now_playing_translation",
        initialValue = true
    )

    @Stable
    var RefreshEveryTime by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_refresh_everytime",
        initialValue = true
    )

    @Stable
    var LyricFontWeight by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_font_weight",
        initialValue = "ExtraBold"
    )

    @Stable
    var LyricFontSize by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_font_size",
        initialValue = 30.5f
    )

    @Stable
    var LyricLineBalance by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_line_balance",
        initialValue = true
    )

    @Stable
    var LyricBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_blur_effect",
        initialValue = true
    )

    @Stable
    var LyricGlowAmount by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_glow_amount",
        initialValue = 0.2f
    )

    @Stable
    var LyricBounceAmount by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_bounce_amount",
        initialValue = 0.22f
    )

    @Stable
    var LyricSmartWbw by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_smart_wbw_lyric",
        initialValue = false
    )

    @Stable
    var LyricBlendMode by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_blend_mode",
        initialValue = "Plus"
    )

    @Stable
    var StatusBarLyricEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_statusbar_enabled",
        initialValue = false
    )

    @Stable
    var StatusBarLyricHooked by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_statusbar_hooked",
        initialValue = false
    )

    @Stable
    var NowplayingBackgroundEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_background_effect",
        initialValue = false
    )

    @Stable
    var BarBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_blur_effect",
        initialValue = false
    )

    @Stable
    var NotificationEnableIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_enable_icon",
        initialValue = true
    )

    @Stable
    var NotificationSmallerIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_smaller_icon",
        initialValue = false
    )

    @Stable
    var FadePlay by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_fade_in_out",
        initialValue = true
    )

    @Stable
    var ListenHistory by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_play_history",
        initialValue = true
    )

    @Stable
    var AudioAttributes by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_attributes",
        initialValue = true
    )

    @Stable
    var Codec by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_codec",
        initialValue = "Auto"
    )

    @Stable
    var HardwareAudioTrackPlayBackParams by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_hardware_audio_track_playback_params",
        initialValue = false
    )

    @Stable
    var AudioFloatOutput by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_float_output",
        initialValue = false
    )

    @Stable
    var NowPlayingBackground by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_nowplaying_background",
        initialValue = "Blurred"
    )

    @Stable
    var EnableExcludeSongsUnderOneMinute by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_enable_exclude_songs_under_one_minute",
        initialValue = true
    )

    var NowplayingFullScreenStaticArtwork by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_nowplaying_full_screen_static_artwork",
        initialValue = false
    )

    // ---------- YT Music Account ----------
    @Stable
    var YtMusicCookie by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_cookie",
        initialValue = ""
    )

    @Stable
    var YtMusicVisitorData by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_visitor_data",
        initialValue = ""
    )

    @Stable
    var YtMusicDataSyncId by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_data_sync_id",
        initialValue = ""
    )

    @Stable
    var YtMusicAccountName by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_account_name",
        initialValue = ""
    )

    @Stable
    var YtMusicAccountEmail by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_account_email",
        initialValue = ""
    )

    @Stable
    var YtMusicAvatarUrl by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_avatar_url",
        initialValue = ""
    )

    @Stable
    var YtMusicChannelHandle by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_channel_handle",
        initialValue = ""
    )

    @Stable
    var YtMusicSyncEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_sync_enabled",
        initialValue = true
    )

    @Stable
    var YtMusicLastSyncTime by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_last_sync_time",
        initialValue = 0L
    )

    @Stable
    var YtMusicPlaylistsJson by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "ytmusic_playlists_json",
        initialValue = ""
    )

    val isYtMusicLoggedIn: Boolean
        get() = YtMusicCookie.isNotBlank() && YtMusicCookie.contains("SAPISID")

    // ---------- First Run ----------
    @Stable
    var isFirstRunComplete by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "is_first_run_complete",
        initialValue = false
    )

    // ---------- Local Music ----------
    @Stable
    var LocalMusicEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "local_music_enabled",
        initialValue = true
    )

    // ---------- Auto Queue (Smart Radio) ----------
    @Stable
    var AutoQueueEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "auto_queue_enabled",
        initialValue = true
    )

    // ---------- Shazam Integration ----------
    @Stable
    var ShazamEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "shazam_enabled",
        initialValue = false
    )

    // ---------- SponsorBlock ----------
    @Stable
    var SponsorBlockEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_enabled",
        initialValue = false
    )

    @Stable
    var SponsorBlockSkipSponsor by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_sponsor",
        initialValue = true
    )

    @Stable
    var SponsorBlockSkipIntro by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_intro",
        initialValue = false
    )

    @Stable
    var SponsorBlockSkipOutro by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_outro",
        initialValue = false
    )

    @Stable
    var SponsorBlockSkipSelfPromo by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_selfpromo",
        initialValue = false
    )

    @Stable
    var SponsorBlockSkipMusicOfftopic by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_music_offtopic",
        initialValue = true
    )

    @Stable
    var SponsorBlockSkipPreview by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_preview",
        initialValue = false
    )

    @Stable
    var SponsorBlockSkipFiller by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_filler",
        initialValue = false
    )

    @Stable
    var SponsorBlockSkipInteraction by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sponsorblock_skip_interaction",
        initialValue = false
    )

    val sponsorBlockEnabledCategories: List<String>
        get() = buildList {
            if (SponsorBlockSkipSponsor) add("sponsor")
            if (SponsorBlockSkipIntro) add("intro")
            if (SponsorBlockSkipOutro) add("outro")
            if (SponsorBlockSkipSelfPromo) add("selfpromo")
            if (SponsorBlockSkipMusicOfftopic) add("music_offtopic")
            if (SponsorBlockSkipPreview) add("preview")
            if (SponsorBlockSkipFiller) add("filler")
            if (SponsorBlockSkipInteraction) add("interaction")
        }

    // ---------- Profile Picture ----------
    @Stable
    var ProfilePictureUri by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "profile_picture_uri",
        initialValue = ""
    )

    @Stable
    var ProfileDisplayName by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "profile_display_name",
        initialValue = ""
    )

    // ---------- Typography / App Font ----------
    @Stable
    var AppFontSize by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_app_font_size",
        initialValue = 16f
    )

    @Stable
    var AppFontWeight by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_app_font_weight",
        initialValue = "Regular"
    )

    // ---------- Animated Album Covers ----------
    @Stable
    var AnimatedAlbumCovers by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "animated_album_covers",
        initialValue = false
    )

    @Stable
    var AnimatedAlbumCoversUseApi by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "animated_album_covers_use_api",
        initialValue = true
    )

    @Stable
    var AnimatedAlbumCoverBlacklist by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "animated_album_cover_blacklist",
        initialValue = ""
    )

    @Stable
    var AnimatedAlbumCoversLocalFolder by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "animated_album_covers_local_folder",
        initialValue = ""
    )

    fun isAnimatedAlbumCoverBlacklisted(albumName: String): Boolean {
        if (AnimatedAlbumCoverBlacklist.isBlank()) return false
        return AnimatedAlbumCoverBlacklist.split("\n").any { it.trim().equals(albumName.trim(), ignoreCase = true) }
    }

    fun toggleAnimatedAlbumCoverBlacklist(albumName: String) {
        val current = AnimatedAlbumCoverBlacklist.split("\n").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        val normalized = albumName.trim()
        val index = current.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(normalized)
        }
        AnimatedAlbumCoverBlacklist = current.joinToString("\n")
    }

    // ---------- Sleep Timer ----------
    @Stable
    var SleepTimerFadeDurationMs by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "sleep_timer_fade_duration_ms",
        initialValue = 0L
    )

    // ---------- Playback Settings ----------
    @Stable
    var PlaybackSpeed by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "playback_speed",
        initialValue = 1.0f
    )

    @Stable
    var PlaybackPitch by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "playback_pitch",
        initialValue = 0.0f
    )

    @Stable
    var SkipSilence by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "skip_silence",
        initialValue = false
    )

    @Stable
    var NormalizeVolume by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "normalize_volume",
        initialValue = false
    )
}
