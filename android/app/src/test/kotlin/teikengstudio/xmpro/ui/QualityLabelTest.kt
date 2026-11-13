package teikengstudio.xmpro.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class QualityLabelTest {

    @Test
    fun `quality types map to a readable codec label`() {
        assertEquals("AAC 128k", formatQuality("m4a_128"))
        assertEquals("AAC 64k", formatQuality("m4a_64"))
        assertEquals("MP3 64k", formatQuality("mp3_64"))
        assertEquals("MP3 32k", formatQuality("mp3_32"))
    }

    @Test
    fun `unknown types stay silent instead of printing raw keys`() {
        assertEquals("", formatQuality(""))
        assertEquals("", formatQuality("auto"))
    }
}
