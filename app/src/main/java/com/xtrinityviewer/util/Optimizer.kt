package com.xtrinityviewer.util

object Optimizer {

    fun optimize(url: String): String {
        if (url.endsWith(".mp4", true) ||
            url.endsWith(".webm", true) ||
            url.endsWith(".gif", true)) {
            return url
        }

        if (url.contains("ehgt.org") ||
            url.contains("hath.network") ||
            url.contains("e-hentai.org") ||
            url.contains("e621.net") ||
            url.contains("realbooru") ||
            url.contains("rule34.xxx") ||
            url.contains("wibone") ||
            url.contains("redd.it") ||
            url.contains("reddit.com") ||
            url.contains("imgur.com") ||
            url.contains("vercomicsporno") ||
            url.contains("toonx") ||
            url.contains("redgifs.com")) {
            return url
        }

        val cleanUrl = url
            .replace("https://", "")
            .replace("http://", "")

        return "https://i0.wp.com/$cleanUrl"
    }
}