package app.traceday

import app.traceday.core.link.LinkParser
import app.traceday.core.link.TrackLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {

    @Test
    fun parsesGoogleDriveFileLink() {
        val link = LinkParser.parse("https://drive.google.com/file/d/1AbC_dEfGh/view?usp=sharing")
        assertEquals(TrackLink.GoogleDrive("1AbC_dEfGh"), link)
    }

    @Test
    fun parsesGoogleDriveOpenLink() {
        val link = LinkParser.parse("https://drive.google.com/open?id=xyz123")
        assertEquals(TrackLink.GoogleDrive("xyz123"), link)
    }

    @Test
    fun parsesGoogleDriveUcLink() {
        val link = LinkParser.parse("https://drive.google.com/uc?export=download&id=abc")
        assertEquals(TrackLink.GoogleDrive("abc"), link)
    }

    @Test
    fun parsesYandexDiskLink() {
        val link = LinkParser.parse("https://disk.yandex.ru/d/abc123key")
        assertEquals(TrackLink.YandexDisk("https://disk.yandex.ru/d/abc123key"), link)
    }

    @Test
    fun parsesYadiSkLink() {
        val link = LinkParser.parse("Посмотри трек: https://yadi.sk/d/zzz999")
        assertEquals(TrackLink.YandexDisk("https://disk.yandex.ru/d/zzz999"), link)
    }

    @Test
    fun parsesRawUrl() {
        val link = LinkParser.parse("https://example.com/tracks/today.jsonl")
        assertEquals(TrackLink.Raw("https://example.com/tracks/today.jsonl"), link)
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull(LinkParser.parse(""))
        assertNull(LinkParser.parse("   "))
        assertNull(LinkParser.parse("просто текст без ссылки"))
        assertNull(LinkParser.parse("ftp://example.com/file"))
        assertTrue(LinkParser.parse("example.com/no/scheme") == null)
    }
}
