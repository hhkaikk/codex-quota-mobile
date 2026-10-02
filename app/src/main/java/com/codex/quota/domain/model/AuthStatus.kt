package com.codex.quota.domain.model

enum class AuthStatus(val isError: Boolean, val userMessage: String) {
    AUTHENTICATED(false, "已连接官网账户"),
    REFRESHING(false, "正在更新额度…"),
    OFFLINE(false, "网络离线，显示上次缓存"),
    TEMPORARY_ERROR(true, "查询暂时失败，显示上次缓存"),
    AUTHENTICATION_REQUIRED(true, "登录已失效，请重新在官网授权"),
    UNKNOWN(false, "尚未获取数据");

    val isSignedOut: Boolean
        get() = this == AUTHENTICATION_REQUIRED
}
