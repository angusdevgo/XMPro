package teikengstudio.xmpro.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeHintTest {

    @Test
    fun `reads the episode number out of a history title`() {
        assertEquals(2347, episodeHint("《神秘复苏》 第2347集 时机的把握"))
        assertEquals(12, episodeHint("青山 第12集 归零"))
        assertEquals(7, episodeHint("最强系统 第 7 集 对峙"))
    }

    @Test
    fun `titles without a number are left alone`() {
        assertNull(episodeHint("青山 归零"))
        assertNull(episodeHint(""))
        assertNull(episodeHint("第集"))
    }

    @Test
    fun `other separators are accepted too`() {
        assertEquals(5, episodeHint("第5话 开端"))
        assertEquals(3, episodeHint("第3回 归途"))
    }
}
