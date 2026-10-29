package app.traceday.cloud

import app.traceday.core.link.TrackLink

interface CloudClient {
    val provider: CloudProvider

    suspend fun ensureStorage()

    suspend fun listDayFiles(): List<RemoteDayFile>

    suspend fun uploadDayFile(dayKey: String, content: ByteArray)

    suspend fun publishDayFile(dayKey: String): String

    suspend fun downloadShared(link: TrackLink): ByteArray

    suspend fun deleteFilesOlderThan(cutoffMillis: Long): Int
}
