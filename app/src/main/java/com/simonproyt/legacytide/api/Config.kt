package com.simonproyt.legacytide.api

class Config(
    var quality: String = "LOSSLESS",
    var videoQuality: String = "HIGH",
    var itemLimit: Int = 1000,
    var alac: Boolean = true
) {
    var apiOauth2Token: String = "https://auth.tidal.com/v1/oauth2/token"
    var apiPkceAuth: String = "https://login.tidal.com/authorize"
    var apiV1Location: String = "https://api.tidal.com/v1/"
    
    // Constants ported from python-tidal
    val clientId: String = "fX2JxdmntZWK0ixT"
    val clientSecret: String = "1Nn9AfDAjxrgJFJbKNWLeAyKGVGmINuXPPLHVXAvxAg="

    // PKCE credentials
    var clientUniqueKey: String = ""
    var codeVerifier: String = ""
    var codeChallenge: String = ""
    var clientIdPkce: String = "6BDSRdpK9hqEBTgU"
    var pkceUriRedirect: String = "https://tidal.com/android/login/auth"

    init {}
}
