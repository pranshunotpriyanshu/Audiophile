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

    val area: Long get() = ((width ?: 0).toLong()) * ((height ?: 0).toLong())
}

fun Thumbnails.bestUrl(): String? {
    if (thumbnails.isEmpty()) return null
    val sized = thumbnails.filter { (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
    val candidates = if (sized.isEmpty()) thumbnails else sized
    return candidates.maxByOrNull { it.area }?.normalizedUrl
        ?: thumbnails.lastOrNull()?.normalizedUrl
}
