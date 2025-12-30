package com.xtrinityviewer.util

object Optimizer {

    fun optimize(url: String): String {
        // 1. Si es Video, no tocar
        if (url.endsWith(".mp4", true) ||
            url.endsWith(".webm", true) ||
            url.endsWith(".gif", true)) { // <--- AÑADIDO: Salvar a los GIFs
            return url
        }

        // 2. LISTA BLANCA (WHITELIST)
        // AÑADIDO: "vercomicsporno" para evitar que el proxy rompa las imágenes protegidas
        if (url.contains("ehgt.org") ||
            url.contains("hath.network") ||
            url.contains("e-hentai.org") ||
            url.contains("e621.net") ||        // E621 directo
            url.contains("realbooru") ||       // Realbooru directo
            url.contains("rule34.xxx") ||      // R34 directo (a veces viene como dominio)
            url.contains("wibone") ||          // CDN de R34
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