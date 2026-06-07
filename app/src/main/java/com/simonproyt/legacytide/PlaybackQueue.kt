package com.simonproyt.legacytide

import com.simonproyt.legacytide.api.models.Track

object PlaybackQueue {
    var tracks: ArrayList<Track> = arrayListOf()
    var currentIndex: Int = -1

    fun getCurrentTrack(): Track? {
        if (currentIndex in tracks.indices) {
            return tracks[currentIndex]
        }
        return null
    }

    fun next(): Track? {
        if (currentIndex + 1 < tracks.size) {
            currentIndex++
            return tracks[currentIndex]
        }
        return null
    }

    fun previous(): Track? {
        if (currentIndex - 1 >= 0) {
            currentIndex--
            return tracks[currentIndex]
        }
        return null
    }
}
