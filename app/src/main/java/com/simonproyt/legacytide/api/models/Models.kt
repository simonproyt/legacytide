package com.simonproyt.legacytide.api.models

import com.google.gson.annotations.SerializedName

data class LinkLogin(
    @SerializedName("expiresIn") val expiresIn: Int,
    @SerializedName("userCode") val userCode: String,
    @SerializedName("verificationUri") val verificationUri: String,
    @SerializedName("verificationUriComplete") val verificationUriComplete: String,
    @SerializedName("interval") val interval: Int,
    @SerializedName("deviceCode") val deviceCode: String
)

data class TidalSession(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("countryCode") val countryCode: String,
    @SerializedName("userId") val userId: Long
)

data class OAuthTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("error") val error: String?,
    @SerializedName("error_description") val errorDescription: String?
)

data class Playlist(
    val uuid: String,
    val title: String,
    val description: String?,
    val numberOfTracks: Int,
    val duration: Int,
    val image: String?, // uuid of image
    val squareImage: String?,
    val isHeader: Boolean = false
)

data class Track(
    val id: Long,
    val title: String,
    val duration: Int,
    val trackNumber: Int,
    val volumeNumber: Int,
    val artist: Artist?,
    val artists: List<Artist>?,
    val album: Album?,
    val audioQuality: String?
)

data class Artist(
    val name: String?
)

data class Album(
    val cover: String? // uuid of cover
)

data class PlaybackInfo(
    val trackId: Long,
    val audioMode: String,
    val audioQuality: String,
    val manifestMimeType: String,
    val manifestHash: String,
    val manifest: String // Base64 encoded
)

data class Lyrics(
    val lyricsProvider: String?,
    val providerLyricsId: String?,
    val lyrics: String?,
    val subtitles: String?
)

data class TimedLyric(
    val timestampMs: Long,
    val text: String
)
