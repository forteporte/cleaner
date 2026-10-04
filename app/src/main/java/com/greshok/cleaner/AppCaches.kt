package com.greshok.cleaner

/** Пакеты мессенджеров, кеш которых чистим через системный экран. */
object AppCaches {
    val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")
    val TELEGRAM = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "org.thunderdog.challegram",
    )
}
