package app.traceday.cloud.yandex

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

@Serializable
data class YandexLinkDto(
    val href: String? = null,
    val method: String? = null,
    val templated: Boolean? = null
)

@Serializable
data class YandexResourceDto(
    val name: String? = null,
    val path: String? = null,
    val modified: String? = null,
    val public_url: String? = null,
    val mime_type: String? = null,
    val _embedded: YandexEmbeddedDto? = null
)

@Serializable
data class YandexEmbeddedDto(
    val items: List<YandexResourceDto> = emptyList()
)

interface YandexDiskApi {

    @GET("v1/disk/resources")
    suspend fun resources(
        @Query("path") path: String,
        @Query("limit") limit: Int = 200
    ): YandexResourceDto

    @PUT("v1/disk/resources")
    suspend fun createFolder(@Query("path") path: String): Response<Unit>

    @GET("v1/disk/resources/upload")
    suspend fun uploadLink(
        @Query("path") path: String,
        @Query("overwrite") overwrite: Boolean = true
    ): YandexLinkDto

    @PUT("v1/disk/resources/publish")
    suspend fun publish(@Query("path") path: String): Response<Unit>

    @DELETE("v1/disk/resources")
    suspend fun delete(@Query("path") path: String): Response<Unit>
}
