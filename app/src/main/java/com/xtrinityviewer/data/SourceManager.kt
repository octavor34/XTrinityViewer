package com.xtrinityviewer.data


object SourceManager {
    private val r34 = R34Module()
    private val e621 = E621Module()
    private val verComics = VerComicsLegacyModule()
    private val realbooru = RealbooruModuleWrapper()
    private val fourChan = FourChanModuleWrapper()
    private val reddit = RedditModuleWrapper()
    private val eHentai = EHentaiModuleWrapper()
    private val modules = mapOf(
        SourceType.R34 to r34,
        SourceType.E621 to e621,
        SourceType.VERCOMICS to verComics,
        SourceType.REALBOORU to realbooru,
        SourceType.CHAN to fourChan,
        SourceType.REDDIT to reddit,
        SourceType.EHENTAI to eHentai
    )

    fun getModule(source: SourceType): SiteModule {
        return modules[source] ?: r34 // Fallback a R34 si algo falla
    }

    fun updateCredentials(r34User: String, r34Key: String, e621User: String, e621Key: String) {
        r34.userId = r34User
        r34.apiKey = r34Key
        e621.user = e621User
        e621.apiKey = e621Key
    }
}