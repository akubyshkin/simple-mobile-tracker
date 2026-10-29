package app.traceday.cloud

object CloudFolderPath {
    const val FOLDER_NAME = "TraceDay"

    fun normalizeParent(raw: String?): String = raw.orEmpty()
        .trim()
        .replace("\\", "/")
        .split("/")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("/")

    fun fullFolderPath(normalizedParent: String): String =
        if (normalizedParent.isEmpty()) FOLDER_NAME
        else "$normalizedParent/$FOLDER_NAME"

    fun driveSegments(fullPath: String): List<String> =
        fullPath.split("/").filter { it.isNotBlank() }

    fun diskPath(fullPath: String): String = "/" + fullPath
}
