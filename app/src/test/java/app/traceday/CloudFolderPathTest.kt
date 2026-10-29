package app.traceday

import app.traceday.cloud.CloudFolderPath
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudFolderPathTest {

    @Test
    fun emptyParent_meansRoot() {
        assertEquals("", CloudFolderPath.normalizeParent(null))
        assertEquals("", CloudFolderPath.normalizeParent(""))
        assertEquals("", CloudFolderPath.normalizeParent("   "))
        assertEquals("", CloudFolderPath.normalizeParent(" / / "))
    }

    @Test
    fun parent_isNormalized() {
        assertEquals("Backups/Geo", CloudFolderPath.normalizeParent(" Backups // Geo / "))
        assertEquals("A/B", CloudFolderPath.normalizeParent("\\A\\B"))
        assertEquals("One", CloudFolderPath.normalizeParent("/One/"))
    }

    @Test
    fun fullPath_appendsTraceDayFolder() {
        assertEquals("TraceDay", CloudFolderPath.fullFolderPath(""))
        assertEquals("Backups/Geo/TraceDay", CloudFolderPath.fullFolderPath("Backups/Geo"))
    }

    @Test
    fun diskPath_hasLeadingSlash() {
        assertEquals("/TraceDay", CloudFolderPath.diskPath("TraceDay"))
        assertEquals("/Backups/TraceDay", CloudFolderPath.diskPath("Backups/TraceDay"))
    }

    @Test
    fun driveSegments_splitsFullPath() {
        assertEquals(
            listOf("Backups", "Geo", "TraceDay"),
            CloudFolderPath.driveSegments("Backups/Geo/TraceDay")
        )
        assertEquals(listOf("TraceDay"), CloudFolderPath.driveSegments("TraceDay"))
    }
}
