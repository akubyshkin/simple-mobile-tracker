package app.traceday.cloud.google

import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

@Serializable
data class DriveFileDto(
    val id: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val parents: List<String>? = null,
    val webViewLink: String? = null,
    val modifiedTime: String? = null
)

@Serializable
data class DriveFileListDto(
    val files: List<DriveFileDto> = emptyList()
)

@Serializable
data class DrivePermissionDto(
    val role: String = "reader",
    val type: String = "anyone"
)

interface DriveApi {

    @GET("drive/v3/files")
    suspend fun list(
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id,name,modifiedTime,webViewLink)",
        @Query("pageSize") pageSize: Int = 100
    ): DriveFileListDto

    @POST("drive/v3/files")
    suspend fun create(
        @Body meta: DriveFileDto,
        @Query("fields") fields: String = "id,name"
    ): DriveFileDto

    @GET("drive/v3/files/{id}")
    suspend fun get(
        @Path("id") id: String,
        @Query("fields") fields: String = "id,name,webViewLink"
    ): DriveFileDto

    @DELETE("drive/v3/files/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    @POST("drive/v3/files/{id}/permissions")
    suspend fun grantPermission(
        @Path("id") id: String,
        @Query("sendNotificationEmail") sendNotificationEmail: Boolean = false,
        @Body permission: DrivePermissionDto
    ): Response<Unit>

    @PUT
    suspend fun putMedia(
        @Url url: String,
        @Body body: RequestBody
    ): Response<Unit>

    @GET
    suspend fun getMedia(@Url url: String): Response<ResponseBody>
}
