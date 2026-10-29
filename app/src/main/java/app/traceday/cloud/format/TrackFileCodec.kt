package app.traceday.cloud.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PointDto(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val acc: Float? = null,
    val p: String? = null
)

class TrackFileCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
) {

    fun encode(points: List<PointDto>): ByteArray {
        val body = points.sortedBy { it.t }.joinToString("\n") { json.encodeToString(it) }
        return body.plus("\n").toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<PointDto> =
        bytes.decodeToString().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                runCatching { json.decodeFromString(PointDto.serializer(), line) }.getOrNull()
            }
            .toList()
}
