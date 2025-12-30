package com.xtrinityviewer.data

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TrinityApi {
    // --- RULE 34 ---
    @GET("index.php?page=dapi&s=post&q=index&json=1")
    suspend fun getR34Posts(
        @Query("limit") limit: Int = 20,
        @Query("pid") page: Int = 0,
        @Query("tags") tags: String = "",
        @Query("api_key") apiKey: String,
        @Query("user_id") userId: String
    ): List<R34Dto>

    @GET("autocomplete.php")
    suspend fun getAutocomplete(@Query("q") query: String): List<AutocompleteDto>

    // --- E621 ---
    @GET("posts.json")
    suspend fun getE621Posts(
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("tags") tags: String = "",
        @Query("login") login: String,
        @Query("api_key") apiKey: String
    ): E621Response

    // --- 4CHAN ---
    @GET("{board}/{page}.json")
    suspend fun get4ChanPage(
        @Path("board") board: String,
        @Path("page") page: Int
    ): FourChanPageDto

    @GET("{board}/thread/{threadId}.json")
    suspend fun get4ChanThread(
        @Path("board") board: String,
        @Path("threadId") threadId: Long
    ): FourChanThreadContainer

    @GET("boards.json")
    suspend fun get4ChanBoards(): FourChanBoardsResponse

    // --- REDDIT ---
    @GET("r/{subreddit}/hot.json")
    suspend fun getRedditPosts(
        @Path("subreddit") subreddit: String,
        @Query("limit") limit: Int = 25,
        @Query("after") after: String? = null,
        @Query("raw_json") rawJson: Int = 1
    ): RedditResponse

    // BÚSQUEDA (Sin valores por defecto para evitar errores de Retrofit)
    @GET("r/{subreddit}/search.json")
    suspend fun searchRedditPosts(
        @Path("subreddit") subreddit: String,
        @Query("q") query: String,
        @Query("restrict_sr") restrictSr: String,
        @Query("include_over_18") nsfw: String,
        @Query("sort") sort: String,
        @Query("limit") limit: Int,
        @Query("after") after: String? = null,
        @Query("raw_json") rawJson: Int = 1
    ): RedditResponse

    @GET("api/subreddit_autocomplete_v2.json")
    suspend fun getRedditAutocomplete(
        @Query("query") query: String,
        @Query("include_over_18") nsfw: Boolean = true,
        @Query("include_profiles") profiles: Boolean = false,
        @Query("limit") limit: Int = 10
    ): RedditSearchResponse
}

object NetworkModule {
    private const val R34_BASE = "https://api.rule34.xxx/"
    private const val E621_BASE = "https://e621.net/"
    private const val CHAN_BASE = "https://a.4cdn.org/"
    private const val REDDIT_BASE = "https://www.reddit.com/"

    private val gson = GsonBuilder().setLenient().create()

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "TrinityViewer/1.0 (by Octavor34)")
                .build()
            chain.proceed(request)
        }
        .build()

    val api: TrinityApi by lazy {
        Retrofit.Builder().baseUrl(R34_BASE).client(client).addConverterFactory(GsonConverterFactory.create(gson)).build().create(TrinityApi::class.java)
    }

    val apiE621: TrinityApi by lazy {
        Retrofit.Builder().baseUrl(E621_BASE).client(client).addConverterFactory(GsonConverterFactory.create(gson)).build().create(TrinityApi::class.java)
    }

    val api4Chan: TrinityApi by lazy {
        Retrofit.Builder().baseUrl(CHAN_BASE).client(client).addConverterFactory(GsonConverterFactory.create(gson)).build().create(TrinityApi::class.java)
    }

    val apiReddit: TrinityApi by lazy {
        Retrofit.Builder().baseUrl(REDDIT_BASE).client(client).addConverterFactory(GsonConverterFactory.create(gson)).build().create(TrinityApi::class.java)
    }
}