package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId

class MyAnimeExtractor : MainAPI() {
    override var mainUrl = "https://animepahe.ru" // Stable 3rd party video source provider
    override var name = "My Custom Anime Engine"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    // 1. Searches the source site for titles matching user input
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/api?m=search&q=$query"
        val response = app.get(searchUrl).parsed<SearchData>()
        
        return response.data.map { anime ->
            newAnimeSearchResponse(anime.title, "$mainUrl/anime/${anime.session}", TvType.Anime) {
                this.posterUrl = anime.poster
            }
        }
    }

    // 2. Grabs data and handles subbed/dubbed filtering separations
    override suspend fun load(url: String): LoadResponse {
        val htmlContent = app.get(url).document
        val animeTitle = htmlContent.selectFirst("h1")?.text() ?: "Unknown Anime"
        val poster = htmlContent.selectFirst(".poster")?.attr("src")

        val episodes = mutableListOf<Episode>()
        // Scrapes the source tables for individual episode components
        htmlContent.select(".episode-list a").forEachIndexed { index, element ->
            val epUrl = fixUrl(element.attr("href"))
            val rawType = element.select(".type").text().lowercase() // Reads if link text says 'sub' or 'dub'
            
            val isDubbed = rawType.contains("dub") || rawType.contains("english")
            
            episodes.add(
                newEpisode(epUrl) {
                    this.name = "Episode ${index + 1}"
                    this.episode = index + 1
                    // Directly tags the stream track type mapping inside CloudStream's layout engine
                    this.description = if (isDubbed) "English Audio (Dub)" else "Japanese Audio (Sub)"
                }
            )
        }

        return newAnimeLoadResponse(animeTitle, url, TvType.Anime) {
            this.posterUrl = poster
            this.episodes = episodes
        }
    }

    // Data structures mapped for handling API parsing tasks
    data class SearchData(val data: List<AnimeInfo>)
    data class AnimeInfo(val title: String, val session: String, val poster: String)
}
