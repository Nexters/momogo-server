package com.mogumogu.momogo.global.token

fun interface AccessTokenProvider {

    fun issue(userId: Long): String
}
