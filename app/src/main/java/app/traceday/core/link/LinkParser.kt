package app.traceday.core.link

sealed interface TrackLink {
    data class GoogleDrive(val fileId: String) : TrackLink
    data class YandexDisk(val publicKeyUrl: String) : TrackLink
    data class Raw(val url: String) : TrackLink
}

object LinkParser {

    private val driveFile = Regex("drive\\.google\\.com/file/d/([\\w-]+)")
    private val driveOpen = Regex("drive\\.google\\.com/open\\?id=([\\w-]+)")
    private val driveUc = Regex("drive\\.google\\.com/uc\\?[^#]*id=([\\w-]+)")
    private val yandex = Regex("(?:disk\\.yandex\\.(?:ru|com)|360\\.yandex\\.ru|yadi\\.sk)/d/([\\w-]+)")

    fun parse(input: String): TrackLink? {
        val s = input.trim()
        if (s.isEmpty()) return null
        driveFile.find(s)?.let { return TrackLink.GoogleDrive(it.groupValues[1]) }
        driveOpen.find(s)?.let { return TrackLink.GoogleDrive(it.groupValues[1]) }
        driveUc.find(s)?.let { return TrackLink.GoogleDrive(it.groupValues[1]) }
        yandex.find(s)?.let {
            return TrackLink.YandexDisk("https://disk.yandex.ru/d/${it.groupValues[1]}")
        }
        if (s.startsWith("http://") || s.startsWith("https://")) return TrackLink.Raw(s)
        return null
    }
}
