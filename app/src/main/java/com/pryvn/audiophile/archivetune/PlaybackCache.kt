package com.pryvn.audiophile.archivetune

import com.tencent.mmkv.MMKV

object PlaybackCache {
    private val mmkv by lazy { MMKV.mmkvWithID("yos_playback_cache") }

    private const val KEY_VISITOR_DATA = "cached_visitor_data"
    private const val KEY_COOKIE = "cached_cookie"
    private const val KEY_DATA_SYNC_ID = "cached_data_sync_id"

    var visitorData: String?
        get() = mmkv.decodeString(KEY_VISITOR_DATA)
        set(value) {
            if (value != null) mmkv.encode(KEY_VISITOR_DATA, value)
            else mmkv.removeValueForKey(KEY_VISITOR_DATA)
        }

    var cookie: String?
        get() = mmkv.decodeString(KEY_COOKIE)
        set(value) {
            if (value != null) mmkv.encode(KEY_COOKIE, value)
            else mmkv.removeValueForKey(KEY_COOKIE)
        }

    var dataSyncId: String?
        get() = mmkv.decodeString(KEY_DATA_SYNC_ID)
        set(value) {
            if (value != null) mmkv.encode(KEY_DATA_SYNC_ID, value)
            else mmkv.removeValueForKey(KEY_DATA_SYNC_ID)
        }

    fun clear() {
        mmkv.clearAll()
    }
}
