package com.codex.quota.auth
data class OAuthTokenResult(val accessToken: String, val refreshToken: String?, val idToken: String?,
    val expiresInSeconds: Long?, val decodedInfo: DecodedTokenInfo?)
