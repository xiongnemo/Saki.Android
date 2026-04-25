package org.hdhmc.saki.presentation

import androidx.annotation.StringRes
import org.hdhmc.saki.R
import org.hdhmc.saki.domain.model.BufferStrategy
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
