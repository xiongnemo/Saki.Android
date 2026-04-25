package org.hdhmc.saki.playback

import org.hdhmc.saki.domain.model.SongLyrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class LyricsHolder @Inject constructor() {
    private val _lyrics = MutableStateFlow<SongLyrics?>(null)
    val lyrics: StateFlow<SongLyrics?> = _lyrics

    fun update(lyrics: SongLyrics?) {
        _lyrics.value = lyrics
    }
}
