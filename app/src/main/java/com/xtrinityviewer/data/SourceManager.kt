package com.xtrinityviewer.data

import android.content.Context

object SourceManager {
    private val r34 = R34Module()
    private val e621 = E621Module()
    private val verComics = VerComicsLegacyModule()
    private val realbooru = RealbooruModuleWrapper()
    private val fourChan = FourChanModuleWrapper()
    private val reddit = RedditModuleWrapper()
    private val eHentai = EHentaiModuleWrapper()
    private val gelbooru = GelbooruModule()
    private val xbooru = XbooruModule()

    private val modules = mapOf(
        SourceType.R34 to r34,
        SourceType.E621 to e621,
        SourceType.VERCOMICS to verComics,
        SourceType.REALBOORU to realbooru,
        SourceType.CHAN to fourChan,
        SourceType.REDDIT to reddit,
        SourceType.EHENTAI to eHentai,
        SourceType.GELBOORU to gelbooru,
        SourceType.XBOORU to xbooru
    )

    fun getModule(source: SourceType): SiteModule {
        return modules[source] ?: r34
    }

    fun refreshCredentials(context: Context) {

        r34.userId = SettingsStore.getSecureCredential(context, "r34_user")
        r34.apiKey = SettingsStore.getSecureCredential(context, "r34_key")

        e621.user = SettingsStore.getSecureCredential(context, "e621_user")
        e621.apiKey = SettingsStore.getSecureCredential(context, "e621_key")

        val boorus = listOf(
            SourceType.GELBOORU to gelbooru,
            SourceType.XBOORU to xbooru
        )

        boorus.forEach { (type, module) ->
            val userKey = "${type.name}_user"
            val passKey = "${type.name}_key"
            module.userId = SettingsStore.getSecureCredential(context, userKey)
            module.apiKey = SettingsStore.getSecureCredential(context, passKey)
        }
    }
}