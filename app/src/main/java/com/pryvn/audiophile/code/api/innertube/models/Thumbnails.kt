/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.pryvn.audiophile.code.api.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>,
)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int?,
    val height: Int?,
) {
    val normalizedUrl: String get() = if (url.startsWith("//")) "https:$url" else url

    /**
     * Surface area used to rank thumbnails by quality. When dimensions are
     * missing we treat the thumbnail as the lowest possible quality so that
     * any sized variant is preferred over an unsized one.
     */
    val area: Long get() = ((width ?: 0).toLong()) * ((height ?: 0).toLong())
}

/**
 * Returns the canonical highest-resolution artwork URL for this list of
 * thumbnails. Selection prefers the thumbnail with the greatest width × height;
 * if dimensions are unavailable it falls back to the last entry (YouTube lists
 * thumbnails small → large). Never returns a low-resolution entry when a larger
 * one is present.
 */
fun Thumbnails.bestUrl(): String? {
    if (thumbnails.isEmpty()) return null
    val sized = thumbnails.filter { (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
    val candidates = if (sized.isEmpty()) thumbnails else sized
    return candidates.maxByOrNull { it.area }?.normalizedUrl
        ?: thumbnails.lastOrNull()?.normalizedUrl
}
