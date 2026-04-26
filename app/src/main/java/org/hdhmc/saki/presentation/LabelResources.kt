package org.hdhmc.saki.presentation

import androidx.annotation.StringRes
import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.AlbumListType
import org.hdhmc.saki.domain.model.BufferStrategy
import org.hdhmc.saki.domain.model.DefaultBrowseTab
import org.hdhmc.saki.domain.model.SoundBalancingMode
import org.hdhmc.saki.domain.model.StreamQuality
import org.hdhmc.saki.domain.model.TextScale

@StringRes
fun StreamQuality.labelRes(): Int = when (this) {
    StreamQuality.ORIGINAL -> R.string.stream_quality_original
    StreamQuality.KBPS_320 -> R.string.stream_quality_320_kbps
    StreamQuality.KBPS_256 -> R.string.stream_quality_256_kbps
    StreamQuality.KBPS_192 -> R.string.stream_quality_192_kbps
    StreamQuality.KBPS_160 -> R.string.stream_quality_160_kbps
    StreamQuality.KBPS_128 -> R.string.stream_quality_128_kbps
    StreamQuality.KBPS_96 -> R.string.stream_quality_96_kbps
}

@StringRes
fun SoundBalancingMode.labelRes(): Int = when (this) {
    SoundBalancingMode.OFF -> R.string.sound_balancing_off
    SoundBalancingMode.LOW -> R.string.sound_balancing_low
    SoundBalancingMode.MEDIUM -> R.string.sound_balancing_medium
    SoundBalancingMode.HIGH -> R.string.sound_balancing_high
}

@StringRes
fun BufferStrategy.labelRes(): Int = when (this) {
    BufferStrategy.NORMAL -> R.string.buffer_strategy_normal
    BufferStrategy.AGGRESSIVE -> R.string.buffer_strategy_aggressive
    BufferStrategy.CUSTOM -> R.string.buffer_strategy_custom
}

@StringRes
fun TextScale.labelRes(): Int = when (this) {
    TextScale.EXTRA_SMALL -> R.string.text_scale_extra_small
    TextScale.SMALL -> R.string.text_scale_small
    TextScale.DEFAULT -> R.string.text_scale_default
    TextScale.LARGE -> R.string.text_scale_large
    TextScale.EXTRA_LARGE -> R.string.text_scale_extra_large
}

@StringRes
fun DefaultBrowseTab.labelRes(): Int = when (this) {
    DefaultBrowseTab.ARTISTS -> R.string.browse_artists
    DefaultBrowseTab.ALBUMS -> R.string.library_albums
    DefaultBrowseTab.PLAYLISTS -> R.string.browse_playlists
    DefaultBrowseTab.SONGS -> R.string.browse_songs
}

@StringRes
fun AlbumListType.labelRes(): Int = when (this) {
    AlbumListType.NEWEST -> R.string.album_feed_newest
    AlbumListType.RECENT -> R.string.album_feed_recent
    AlbumListType.RANDOM -> R.string.album_feed_random
    AlbumListType.HIGHEST -> R.string.album_feed_top_rated
    AlbumListType.FREQUENT -> R.string.album_feed_frequent
    AlbumListType.ALPHABETICAL_BY_NAME -> R.string.album_feed_a_z
    AlbumListType.ALPHABETICAL_BY_ARTIST -> R.string.album_feed_by_artist
    AlbumListType.STARRED -> R.string.album_feed_starred
    AlbumListType.BY_YEAR -> R.string.album_feed_by_year
    AlbumListType.BY_GENRE -> R.string.album_feed_by_genre
}
