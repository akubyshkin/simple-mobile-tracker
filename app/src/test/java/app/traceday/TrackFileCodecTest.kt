package app.traceday

import app.traceday.cloud.format.PointDto
import app.traceday.cloud.format.TrackFileCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFileCodecTest {

    private val codec = TrackFileCodec()

    @Test
    fun roundtrip_keepsAllPointsSorted() {
        val points = listOf(
            PointDto(t = 300, lat = 3.0, lon = 3.0, acc = 5f, p = "gps"),
            PointDto(t = 100, lat = 1.0, lon = 1.0),
            PointDto(t = 200, lat = 2.0, lon = 2.0)
        )
        val decoded = codec.decode(codec.encode(points))
        assertEquals(3, decoded.size)
        assertEquals(listOf(100L, 200L, 300L), decoded.map { it.t })
        assertEquals(1.0, decoded[0].lat, 0.0)
        assertEquals(5f, decoded[2].acc)
        assertEquals("gps", decoded[2].p)
    }

    @Test
    fun decode_skipsCorruptAndEmptyLines() {
        val content = """
            {"t":1,"lat":1.0,"lon":1.0}
            not json at all
            {"t":2,"lat":2.0}
            
            {"t":3,"lat":3.0,"lon":3.0,"unknown":"x"}
        """.trimIndent()
        val decoded = codec.decode(content.toByteArray())
        assertEquals(2, decoded.size)
        assertEquals(listOf(1L, 3L), decoded.map { it.t })
    }

    @Test
    fun encode_omitsOptionalNullFields() {
        val bytes = codec.encode(listOf(PointDto(t = 1, lat = 1.0, lon = 2.0)))
        val text = bytes.decodeToString()
        assertTrue(text.startsWith("{\"t\":1,\"lat\":1.0,\"lon\":2.0}"))
        assertNull(codec.decode(bytes).first().acc)
    }

    @Test
    fun decode_emptyInput_returnsEmptyList() {
        assertTrue(codec.decode(ByteArray(0)).isEmpty())
        assertTrue(codec.decode("\n\n".toByteArray()).isEmpty())
    }
}
