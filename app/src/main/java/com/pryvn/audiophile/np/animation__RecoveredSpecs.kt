package com.pryvn.audiophile.np.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring

object RecoveredSpecs {
    val FAST_OUT_SLOW_IN: CubicBezierEasing =
        CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    const val POPUP_OPEN_TWEEN_MS = 300
    const val POPUP_OPEN_DELAY_MS = 430
    const val POPUP_SCALE_VISIBLE = 225f
    const val POPUP_SCALE_HIDDEN = 0f
    const val POPUP_ALPHA_SPRING_DAMPING = 0.7f
    const val POPUP_ALPHA_SPRING_STIFFNESS = 340f
    const val POPUP_ALPHA_THRESHOLD = 1e-4f
    const val POPUP_TRANSLATE_TWEEN_MS = 220
    const val POPUP_TRANSLATE_DELTA_DP = 14f
    const val MENU_BACKGROUND_COLOR = 0xFA161616L
    const val MENU_SCRIM_COLOR = 0xB3000000L
    const val MENU_LEVEL_TRANSITION_LABEL = "NowPlayingMoreMenu_level_transition"
    const val POPUP_ALPHA_LABEL = "nowplaying popup animation"
    const val POPUP_TRANSLATE_LABEL = "nowplaying popup translateY"

    const val VOLUME_SPRING_DAMPING = 0.6f
    const val VOLUME_SPRING_STIFFNESS = 200f
    const val VOLUME_SCALE_VISIBLE = 1.05f
    const val VOLUME_BACKDROP_ALPHA_VISIBLE = 0.62f
    const val VOLUME_BACKDROP_ALPHA_HIDDEN = 0.45f
    const val VOLUME_OFFSET_VISIBLE_DP = 12f
    const val VOLUME_OFFSET_HIDDEN_DP = 7f
    const val SLIDER_REST_SPRING_DAMPING = Spring.DampingRatioNoBouncy.toFloat()
    const val SLIDER_REST_SPRING_STIFFNESS = 50f
    const val SLIDER_REST_THRESHOLD = 0.001f

    const val MSS_PRESSED_SPRING_DAMPING = 0.6f
    const val MSS_PRESSED_SPRING_STIFFNESS = 200f
    const val MSS_PRESSED_SCALE_TARGET = 1.35f
    const val MSS_BADGE_SPRING_DAMPING = 0.72f
    const val MSS_BADGE_SPRING_STIFFNESS = 220f
    const val MSS_BADGE_TARGET_DP = 24f
    const val MSS_VISIBILITY_SPRING_DAMPING = Spring.DampingRatioNoBouncy.toFloat()
    const val MSS_VISIBILITY_SPRING_STIFFNESS = 405f
    const val MSS_STAGGER_SPRING_DAMPING = 0.72f
    const val MSS_STAGGER_SPRING_STIFFNESS = 250f
    const val MSS_SHOW_TWEEN_MS = 520
    const val MSS_HIDE_TWEEN_MS = 240
    const val MSS_SLIDE_DISTANCE_DP = 50f
    const val MSS_CONTROL_SCALE_LABEL = "MssControlScale"
    const val MSS_CONTROL_VISIBILITY_LABEL = "MssControlVisibility"

    const val HEADER_CROSSFADE_SPRING_DAMPING = 1.2f
    const val HEADER_CROSSFADE_SPRING_STIFFNESS = 800f
    const val HEADER_CROSSFADE_THRESHOLD = 0.001f

    const val MINI_ARTWORK_TWEEN_MS = 480
    const val MINI_ARTWORK_PULSE_DAMPING = 0.65f
    const val MINI_ARTWORK_PULSE_STIFFNESS = 150f
    const val MINI_ARTWORK_PULSE_THRESHOLD = 0.001f
    const val MINI_ARTWORK_SIZE_BASE_DP = 9f
    const val MINI_ARTWORK_SIZE_RANGE_DP = 37f
    const val MINI_ARTWORK_MAX_ALPHA_DIVISOR_START = 0.08f
    const val MINI_ARTWORK_MAX_ALPHA_DIVISOR_SPAN = 0.82f
    const val MINI_LAYER_MIN_SCALE = 0.9f
    const val MINI_LAYER_SCALE_SPAN = 0.1f

    const val PLAY_PAUSE_SIZE_FULL_DP = 56f
    const val PLAY_PAUSE_SIZE_ALT_DP = 49f
    const val PLAY_PAUSE_ICON_PADDING_FULL_DP = 10f
    const val PLAY_PAUSE_ICON_PADDING_ALT_DP = 9f
    const val PLAY_PAUSE_ICON_INSET_ALT_DP = 2f
    const val MINI_PLAY_PAUSE_ICON_SIZE_DP = 21.5f

    const val PREVIOUS_WEIGHT = 1.0f
    const val NEXT_WEIGHT = 0.4f
    const val TRANSPORT_TOUCH_TARGET_DP = 60.5f
    const val TRANSPORT_ICON_PADDING_DP = 10f

    const val TAB_TOGGLE_SIZE_DP = 36f
    const val TAB_TOGGLE_HORIZONTAL_PADDING_DP = 8f
    const val MINI_TOGGLE_SIZE_DP = 33f
    const val CENTER_CONTROL_SIZE_DP = 54f
    const val ACTION_ROW_ICON_SIZE_DP = 28f
    const val VOLUME_ICON_SIZE_DP = 20f

    const val WATCH_POPUP_SCALE_FACTOR = 0.4f
    const val LANDSCAPE_EXPANDED_MIN_SMALLEST_WIDTH_DP = 600
    const val LYRIC_FOLLOW_RESET_DELAY_MS = 2500L

    const val POSITION_SEEK_JUMP_THRESHOLD_MS = 1000L
    const val POSITION_FORCE_REFRESH_INTERVAL_MS = 2000L

    const val STATIC_ALBUM_WIDTH_FRACTION = 0.595f
    const val ALBUM_PAGE_PADDING_DP = 22f
    const val ALBUM_ARTWORK_PADDING_DP = 26f

    const val DIVIDER_GOLDEN_WIDTH_FRACTION = 0.618f
    const val DIVIDER_HEIGHT_FRACTION = 0.65f

    const val ARTIST_SEPARATOR = "\u2009&\u2009"

    const val AIRPLAY_ICON_OFFSET_DEFAULT_DP = 15f
    const val AIRPLAY_ICON_OFFSET_COMPACT_DP = 14.5f
    const val EARPHONE_ICON_OFFSET_DEFAULT_DP = 8f
    const val EARPHONE_ICON_OFFSET_COMPACT_DP = 9f

    const val SHARED_ELEMENT_KEY_ALBUM_PAGE = "now-playing-page-album"

    const val PADDING_FALLBACK_WHEN_HIDDEN_DP = 12
    const val POPUP_ANCHOR_GAP_DP = 8

    const val PANEL_SPRING_TAP_DAMPING = 1.0f
    const val PANEL_SPRING_TAP_STIFFNESS = 400.0f
    const val PANEL_SPRING_TAP_THRESHOLD = 1.0f
    const val PANEL_SPRING_DRAG_A_DAMPING = 1.0f
    const val PANEL_SPRING_DRAG_A_STIFFNESS = 400.0f
    const val PANEL_SPRING_DRAG_A_THRESHOLD = 0.5f
    const val PANEL_SPRING_DRAG_B_DAMPING = 1.0f
    const val PANEL_SPRING_DRAG_B_STIFFNESS = 1400.0f
    const val PANEL_SPRING_DRAG_B_THRESHOLD = 0.5f
    const val FLING_SPRING_DAMPING = 1.0f
    const val FLING_SPRING_STIFFNESS = 1500.0f
    const val EXPANSION_GATE_TIMEOUT_MS = 600L
    const val TRACK_CHANGE_AUTO_EXPAND_DELAY_MS = 500L
    const val HORIZONTAL_SWIPE_DISTANCE_FRACTION = 0.18f
    const val HORIZONTAL_SWIPE_VELOCITY_THRESHOLD = 1400.0f
    const val PANEL_CORNER_BASE_DP = 12f
    const val PANEL_CORNER_BASE_DP_INT = 12
    const val PANEL_SHADOW_GATE_FRACTION = 5.0E-4f
    const val PANEL_SHADOW_ELEVATION_DP = 5f
    const val MINI_INSET_DP = 7.5f
    const val BLUR_GATE_COLLAPSE_FRACTION = 0.3f
    const val TITLE_BLUR_TWEEN_MS = 100
    const val PARENT_HEIGHT_SAVEABLE_KEY = "MainActivity_parentHeight"
    const val SHARED_ELEMENT_CAPTURE_GATE_MIN = 0.05f
    const val SHARED_ELEMENT_CAPTURE_GATE_MAX = 0.95f
    const val SHARED_ELEMENT_TRANSFORM_ACTIVE_MIN = 0.001f
    const val SHARED_ELEMENT_TRANSFORM_ACTIVE_MAX = 0.999f
    const val SHARED_ELEMENT_BOUNDS_TOLERANCE_PX = 0.5f
    const val SHARED_ELEMENT_DIM_ALPHA = 0.6f
    const val DRAG_FOLLOW_EPSILON = 0.5f
}


