package com.anzupop.saki.android.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * A [ShuffleOrder] that places [anchorIndex] first, then shuffles the rest
 * deterministically using [seed]. Same seed + count + anchor always produces
 * the same order.
 */
@UnstableApi
class SakiShuffleOrder(
    private val shuffled: IntArray,
    private val indexInShuffled: IntArray,
) : ShuffleOrder {

    constructor(length: Int, seed: Long, anchorIndex: Int) : this(
        buildShuffledArray(length, seed, anchorIndex.coerceIn(0, (length - 1).coerceAtLeast(0))),
    )

    private constructor(shuffled: IntArray) : this(
        shuffled = shuffled,
        indexInShuffled = IntArray(shuffled.size).also { inv ->
            for (i in shuffled.indices) inv[shuffled[i]] = i
        },
    )

    override fun getLength(): Int = shuffled.size

    override fun getFirstIndex(): Int =
        if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled[0]

    override fun getLastIndex(): Int =
        if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled[shuffled.lastIndex]

    override fun getNextIndex(index: Int): Int {
        val pos = indexInShuffled.getOrElse(index) { return C.INDEX_UNSET }
        return if (pos + 1 < shuffled.size) shuffled[pos + 1] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val pos = indexInShuffled.getOrElse(index) { return C.INDEX_UNSET }
        return if (pos > 0) shuffled[pos - 1] else C.INDEX_UNSET
    }

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val newLength = shuffled.size + insertionCount
        val newShuffled = IntArray(newLength)
        var destPos = 0
        // Copy existing, shifting indices >= insertionIndex
        for (originalIndex in shuffled) {
            newShuffled[destPos++] = if (originalIndex >= insertionIndex) originalIndex + insertionCount else originalIndex
        }
        // Append new indices at the end of shuffle order
        for (i in 0 until insertionCount) {
            newShuffled[destPos++] = insertionIndex + i
        }
        return SakiShuffleOrder(newShuffled)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removeCount = indexToExclusive - indexFrom
        val newShuffled = shuffled
            .filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - removeCount else it }
            .toIntArray()
        return SakiShuffleOrder(newShuffled)
    }

    override fun cloneAndClear(): ShuffleOrder = SakiShuffleOrder(IntArray(0))

    /** Traverses the shuffle order and returns the list of player indices in display order. */
    fun toDisplayOrder(): List<Int> {
        return shuffled.toList()
    }

    companion object {
        private fun buildShuffledArray(length: Int, seed: Long, anchorIndex: Int): IntArray {
            if (length == 0) return IntArray(0)
            val rest = (0 until length).filter { it != anchorIndex }
                .shuffled(kotlin.random.Random(seed))
            return IntArray(length) { i ->
                if (i == 0) anchorIndex else rest[i - 1]
            }
        }
    }
}
