package com.pryvn.audiophile.code.api.innertube.pages

import com.pryvn.audiophile.code.api.innertube.models.Artist
import com.pryvn.audiophile.code.api.innertube.models.SongItem
import com.pryvn.audiophile.code.api.innertube.models.AlbumItem
import com.pryvn.audiophile.code.api.innertube.models.ArtistItem
import com.pryvn.audiophile.code.api.innertube.models.PlaylistItem

data class ArtistPageData(
    val artist: ArtistItem,
    val header: ArtistHeader?,
    val topSongs: List<SongItem>,
    val essentialAlbums: List<AlbumItem>,
    val singlesEPs: List<AlbumItem>,
    val description: String?,
    val relatedArtists: List<ArtistItem>,
    val latestRelease: AlbumItem?,
)

data class ArtistHeader(
    val thumbnail: String?,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val subscriberCount: String?,
    val monthlyListeners: String?,
    val browseId: String?,
) {
    companion object {
        fun fromArtistItem(item: ArtistItem): ArtistHeader {
            return ArtistHeader(
                thumbnail = item.thumbnail,
                title = item.title,
                subtitle = null,
                description = null,
                subscriberCount = item.subscriberCountText,
                monthlyListeners = item.monthlyListenerCountText,
                browseId = item.id,
            )
        }
    }
}

data class SectionData(
    val title: String,
    val items: List<Any>,
    val moreEndpoint: String?,
    val layout: SectionLayout,
) {
    enum class SectionLayout {
        LIST, GRID, CAROUSEL
    }
}