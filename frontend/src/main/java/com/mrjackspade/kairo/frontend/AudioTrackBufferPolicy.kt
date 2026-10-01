// SPDX-License-Identifier: GPL-2.0-or-later
package com.mrjackspade.kairo.frontend

import android.media.AudioTrack

/**
 * Bounds PCM output queuing without changing its allocation or playback clock.
 * Use only from the track's writer thread, before play() and after each write.
 * Any observed underrun restores the original device-negotiated size for the
 * rest of this track's lifetime. A new track may try the smaller queue again.
 */
class AudioTrackBufferPolicy(private val track: AudioTrack) {
    private val originalSize = track.bufferSizeInFrames
    private var lastUnderruns = track.underrunCount
    private var nextCheck = System.nanoTime() + 1_000_000_000L
    private var reduced = false

    init {
        // Fifty milliseconds matched the measured RGDS workload. Never enlarge
        // an already smaller device buffer; retain capacity for safe fallback.
        val wanted = minOf(originalSize, track.sampleRate / 20)
        if (wanted > 0 && wanted < originalSize) {
            reduced = track.setBufferSizeInFrames(wanted) in 1 until originalSize
        }
    }

    fun checkAfterWrite() {
        if (!reduced) return
        val now = System.nanoTime()
        if (now < nextCheck) return
        nextCheck = now + 1_000_000_000L
        val underruns = track.underrunCount
        if (underruns > lastUnderruns) {
            // No retry loop that repeatedly shrinks a buffer on a struggling
            // device. If resizing fails, keep checking so fallback can recover.
            if (track.setBufferSizeInFrames(originalSize) >= originalSize) reduced = false
        } else {
            lastUnderruns = underruns
        }
    }
}
