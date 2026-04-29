package org.hdhmc.saki.presentation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class DeviceCapabilityTier {
    LOW,
    LEGACY,
    STANDARD,
    HIGH,
}

data class DeviceCapability(
    val tier: DeviceCapabilityTier,
    val isLowRamDevice: Boolean,
    val memoryClassMb: Int,
    val mediaPerformanceClass: Int,
)

data class VisualEffectsPolicy(
    val deviceCapability: DeviceCapability,
    val useNowPlayingDynamicArtworkColors: Boolean,
    val useNowPlayingGradientBackground: Boolean,
    val useNowPlayingArtworkMotion: Boolean,
    val nowPlayingArtworkPrewarmRadius: Int,
)

@Composable
fun rememberVisualEffectsPolicy(): VisualEffectsPolicy {
    val context = LocalContext.current.applicationContext
    return remember(context) { detectVisualEffectsPolicy(context) }
}

private fun detectVisualEffectsPolicy(context: Context): VisualEffectsPolicy {
    val capability = detectDeviceCapability(context)
    val richNowPlayingEffects = when (capability.tier) {
        DeviceCapabilityTier.LOW, DeviceCapabilityTier.LEGACY -> false

        DeviceCapabilityTier.STANDARD, DeviceCapabilityTier.HIGH -> true
    }
    return VisualEffectsPolicy(
        deviceCapability = capability,
        useNowPlayingDynamicArtworkColors = richNowPlayingEffects,
        useNowPlayingGradientBackground = richNowPlayingEffects,
        useNowPlayingArtworkMotion = capability.tier != DeviceCapabilityTier.LOW,
        nowPlayingArtworkPrewarmRadius = when (capability.tier) {
            DeviceCapabilityTier.LOW -> LOW_ARTWORK_PREWARM_RADIUS
            DeviceCapabilityTier.LEGACY -> LEGACY_ARTWORK_PREWARM_RADIUS
            DeviceCapabilityTier.STANDARD, DeviceCapabilityTier.HIGH -> RICH_ARTWORK_PREWARM_RADIUS
        },
    )
}

private fun detectDeviceCapability(context: Context): DeviceCapability {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val isLowRamDevice = activityManager?.isLowRamDevice == true
    val memoryClassMb = activityManager?.memoryClass ?: 0
    val mediaPerformanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.VERSION.MEDIA_PERFORMANCE_CLASS
    } else {
        0
    }
    val tier = when {
        isLowRamDevice || memoryClassMb in 1..LOW_MEMORY_CLASS_MB -> DeviceCapabilityTier.LOW
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> DeviceCapabilityTier.LEGACY
        mediaPerformanceClass >= Build.VERSION_CODES.TIRAMISU &&
            memoryClassMb >= HIGH_MEMORY_CLASS_MB -> DeviceCapabilityTier.HIGH
        else -> DeviceCapabilityTier.STANDARD
    }
    return DeviceCapability(
        tier = tier,
        isLowRamDevice = isLowRamDevice,
        memoryClassMb = memoryClassMb,
        mediaPerformanceClass = mediaPerformanceClass,
    )
}

private const val LOW_MEMORY_CLASS_MB = 128
private const val HIGH_MEMORY_CLASS_MB = 384
private const val LOW_ARTWORK_PREWARM_RADIUS = 0
private const val LEGACY_ARTWORK_PREWARM_RADIUS = 1
private const val RICH_ARTWORK_PREWARM_RADIUS = 3
