package com.pryvn.audiophile.np.transition

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect

class SharedElementBounds(val source: Rect, val target: Rect)

class SharedElementRegistry {
    private val sourceBounds = mutableStateMapOf<String, Rect>()
    private val targetBounds = mutableStateMapOf<String, Rect>()

    fun registerSource(key: String, rect: Rect): Boolean = write(sourceBounds, key, rect)
    fun registerTarget(key: String, rect: Rect): Boolean = write(targetBounds, key, rect)

    fun lookup(key: String): SharedElementBounds? {
        val src = sourceBounds[key] ?: return null
        val tgt = targetBounds[key] ?: return null
        if (!isValid(src) || !isValid(tgt)) return null
        return SharedElementBounds(src, tgt)
    }

    fun remove(key: String) { sourceBounds.remove(key); targetBounds.remove(key) }
    fun bothPresent(key: String) = sourceBounds.containsKey(key) && targetBounds.containsKey(key)

    private fun write(map: MutableMap<String, Rect>, key: String, candidate: Rect): Boolean {
        if (!isValid(candidate)) return false
        val existing = map[key]
        if (existing != null && approximatelyEquals(existing, candidate)) return false
        map[key] = candidate; return true
    }

    companion object {
        const val KEY_ALBUM_COVER = "album-cover"
        const val KEY_ALBUM_PAGE = "now-playing-page-album"
        fun isValid(r: Rect) = r.width > 1f && r.height > 1f
        fun approximatelyEquals(a: Rect, b: Rect) =
            abs(a.left - b.left) <= 0.5f && abs(a.top - b.top) <= 0.5f &&
            abs(a.right - b.right) <= 0.5f && abs(a.bottom - b.bottom) <= 0.5f
        private fun abs(v: Float) = kotlin.math.abs(v)
    }
}
