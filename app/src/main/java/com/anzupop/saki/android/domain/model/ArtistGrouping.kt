package com.anzupop.saki.android.domain.model

import android.icu.text.AlphabeticIndex
import java.util.Locale

/**
 * Re-groups artists from server-provided sections into locale-aware buckets
 * using [AlphabeticIndex]. This correctly handles CJK (pinyin/kana/jamo),
 * Latin with diacritics, Cyrillic, and other scripts.
 */
fun LibraryIndexes.regroupByLocale(locale: Locale = Locale.getDefault()): LibraryIndexes {
    val allArtists = sections.flatMap { it.artists }
    if (allArtists.isEmpty()) return this

    val index = AlphabeticIndex<Nothing>(locale)
        .addLabels(Locale.ENGLISH)
        .addLabels(Locale.JAPANESE)
        .addLabels(Locale.CHINESE)
        .addLabels(Locale.KOREAN)
        .setOverflowLabel("#")
        .setUnderflowLabel("#")

    val immutable = index.buildImmutableIndex()

    val bucketArtists = Array(immutable.bucketCount) { mutableListOf<ArtistSummary>() }
    val bucketLabels = Array(immutable.bucketCount) { immutable.getBucket(it).label }

    allArtists.forEach { artist ->
        val idx = immutable.getBucketIndex(artist.name)
        bucketArtists[idx].add(artist)
    }

    // Merge buckets with the same label (e.g. underflow "#" and overflow "#")
    val merged = linkedMapOf<String, MutableList<ArtistSummary>>()
    bucketLabels.indices.forEach { i ->
        if (bucketArtists[i].isNotEmpty()) {
            merged.getOrPut(bucketLabels[i]) { mutableListOf() }.addAll(bucketArtists[i])
        }
    }

    val newSections = merged.map { (label, artists) -> ArtistSection(name = label, artists = artists) }
    return copy(sections = newSections)
}
