package com.pryvn.audiophile.data.libraries

import androidx.compose.runtime.Stable
import com.funny.data_saver.core.mutableDataSaverListStateOf
import com.funny.data_saver.core.mutableDataSaverStateOf
import com.pryvn.audiophile.data.SettingsSaver

@Stable
object SettingsLibrary {

    /**
     * 关注的歌手列表
     */
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

    /**
     * 是否显示音量条
     */
    @Stable
    var NowPlayingShowVolumeBar by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_show_volume_bar",
        initialValue = true
    )

    /**
     * 应用主题
     */
    @Stable
    var CustomTheme by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_theme",
        initialValue = "Auto"
    )

    /**
     * 是否已设置过屏幕圆角大小
     */
    @Stable
    var ScreenCornerSet by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner_set",
        initialValue = true
    )

    /**
     * 屏幕圆角大小
     */
    @Stable
    var ScreenCorner by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner",
        initialValue = "30"
    )

    /**
     * 歌曲排序
     */
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

    /**
     * 启用降序
     */
    @Stable
    var EnableDescending by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_enable_descending",
        initialValue = false
    )

    /**
     * 歌词界面 - 翻译
     */
    @Stable
    var NowPlayingTranslation by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "now_playing_translation",
        initialValue = true
    )

    /**
     * 每次启动时刷新媒体库
     */
    @Stable
    var RefreshEveryTime by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_refresh_everytime",
        initialValue = false
    )

    /**
     * 歌词字体字重
     */
    @Stable
    var LyricFontWeight by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_font_weight",
        initialValue = "ExtraBold"
    )

    /**
     * 歌词字体大小 (sp)
     */
    @Stable
    var LyricFontSize by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_font_size",
        initialValue = 26f
    )

    /**
     * 歌词平衡行模式
     */
    @Stable
    var LyricLineBalance by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_line_balance",
        initialValue = true
    )

    /**
     * 歌词模糊效果
     */
    @Stable
    var LyricBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_blur_effect",
        initialValue = true
    )

    /**
     * 状态栏歌词启用
     */
    @Stable
    var StatusBarLyricEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_statusbar_enabled",
        initialValue = false
    )

    /**
     * 状态栏歌词已挂钩
     */
    @Stable
    var StatusBarLyricHooked by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_statusbar_hooked",
        initialValue = false
    )

    /**
     * 播放界面背景动态效果
     */
    @Stable
    var NowplayingBackgroundEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_background_effect",
        initialValue = false
    )

    /**
     * 界面工具栏模糊效果
     */
    @Stable
    var BarBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_blur_effect",
        initialValue = false
    )

    /**
     * 媒体通知-额外的媒体图标
     */
    @Stable
    var NotificationEnableIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_enable_icon",
        initialValue = true
    )

    /**
     * 媒体通知-小一号图标
     */
    @Stable
    var NotificationSmallerIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_smaller_icon",
        initialValue = false
    )

    /**
     * 渐入渐出播放
     */
    @Stable
    var FadePlay by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_fade_in_out",
        initialValue = true
    )

    /**
     * 播放历史
     */
    @Stable
    var ListenHistory by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_play_history",
        initialValue = true
    )

    /**
     * ExoPlayer行为 - 音频属性
     */
    @Stable
    var AudioAttributes by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_attributes",
        initialValue = true
    )

    /**
     * ExoPlayer解码 - 编解码器
     */
    @Stable
    var Codec by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_codec",
        initialValue = "Auto"
    )

    /**
     * ExoPlayer解码 - 硬件音频轨道播放参数
     */
    @Stable
    var HardwareAudioTrackPlayBackParams by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_hardware_audio_track_playback_params",
        initialValue = false
    )

    /**
     * ExoPlayer解码 - 音频浮点输出
     */
    @Stable
    var AudioFloatOutput by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_float_output",
        initialValue = false
    )

    /**
     * ExoPlayer播放界面 - 全屏专辑封面（模糊背景融合）
     */
    @Stable
    var FullScreenAlbumArtwork by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_full_screen_album_artwork",
        initialValue = false
    )

    /**
     * 排除一分钟以内的歌曲
     */
    @Stable
    var EnableExcludeSongsUnderOneMinute by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_enable_exclude_songs_under_one_minute",
        initialValue = true
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
        initialValue = false
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

    // ---------- Animated Album Covers ----------
    @Stable
    var AnimatedAlbumCovers by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "animated_album_covers",
        initialValue = true
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
