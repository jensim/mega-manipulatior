package com.github.jensim.megamanipulator.actions.search.sourcegraph

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.actions.search.SearchResult
import com.github.jensim.megamanipulator.actions.search.SearchTypes
import com.github.jensim.megamanipulator.http.HttpClientProvider
import com.github.jensim.megamanipulator.settings.SearchHostSettings.SourceGraphSettings
import com.intellij.notification.NotificationType
import com.jetbrains.rd.util.printlnError
import io.ktor.client.features.timeout
import io.ktor.client.request.post

class SourcegraphSearchClient(
    private val httpClientProvider: HttpClientProvider,
    private val notificationsOperator: NotificationsOperator,
) {

    companion object {

        val instance by lazy {
            SourcegraphSearchClient(
                httpClientProvider = HttpClientProvider.instance,
                notificationsOperator = NotificationsOperator.instance,
            )
        }
    }

    suspend fun search(searchHostName: String, settings: SourceGraphSettings, search: String): Set<SearchResult> {
        try {
            val baseUrl = settings.baseUrl
            /*
            TODO
             * Paginate on result
             */
            val client = httpClientProvider.getClient(searchHostName, settings)
            val response: SearchTypes.GraphQLResponse = client.post("$baseUrl/.api/graphql?Search=") {
                body = SearchTypes.GraphQlRequest(SearchTypes.SearchVaraibles(search))
                timeout {
                    socketTimeoutMillis = 5 * 60 * 1000
                    requestTimeoutMillis = 5 * 60 * 1000
                }
            }
            response.errors?.let {
                printlnError("ERROR $it")
            }
            return response.data?.search?.results?.results.orEmpty().mapNotNull {
                internalNameToSearchResult(searchHostName, it.repository?.name)
            }.toSet()
        } catch (e: Exception) {
            notificationsOperator.show(
                title = "Search failed",
                body = e.message ?: "Exception running search",
                type = NotificationType.ERROR,
            )
            e.printStackTrace()
            return emptySet()
        }
    }

    private fun internalNameToSearchResult(searchHostName: String, name: String?): SearchResult? {
        val parts = name?.split('/')
        return if (parts?.size != 3) {
            null
        } else {
            SearchResult(searchHostName = searchHostName, codeHostName = parts[0], project = parts[1], repo = parts[2])
        }
    }
}