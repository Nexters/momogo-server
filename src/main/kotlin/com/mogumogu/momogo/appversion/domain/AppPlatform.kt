package com.mogumogu.momogo.appversion.domain

enum class AppPlatform {
    IOS,
    ANDROID,
    ;

    companion object {
        fun from(value: String): AppPlatform? = entries.firstOrNull { platform ->
            platform.name == value
        }
    }
}
