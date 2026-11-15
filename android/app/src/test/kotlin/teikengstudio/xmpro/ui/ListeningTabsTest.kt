package teikengstudio.xmpro.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ListeningTabsTest {

    @Test
    fun `a fresh install shows every tab in the default order`() {
        assertEquals(ListeningTabs.normalize(emptyList()), ListeningTabs.defaultOrder)
        assertEquals(5, ListeningTabs.defaultOrder.size)
    }

    @Test
    fun `the saved order wins`() {
        val saved = listOf(ListeningTabs.HISTORY, ListeningTabs.UPDATES)
        assertEquals(
            listOf(
                ListeningTabs.HISTORY,
                ListeningTabs.UPDATES,
                ListeningTabs.SUBSCRIPTIONS,
                ListeningTabs.FAVORITES,
                ListeningTabs.BOOKMARKS,
            ),
            ListeningTabs.normalize(saved),
        )
    }

    @Test
    fun `unknown and repeated keys are dropped, never hiding a tab`() {
        val saved = listOf("gone", ListeningTabs.FAVORITES, ListeningTabs.FAVORITES)
        val order = ListeningTabs.normalize(saved)
        assertEquals(ListeningTabs.FAVORITES, order.first())
        assertEquals(5, order.size)
        assertEquals(order.size, order.distinct().size)
    }

    @Test
    fun `moving keeps every tab and swaps neighbours`() {
        val base = ListeningTabs.defaultOrder
        val moved = ListeningTabs.move(base, 0, 2)
        assertEquals(
            listOf(
                ListeningTabs.SUBSCRIPTIONS,
                ListeningTabs.HISTORY,
                ListeningTabs.UPDATES,
                ListeningTabs.FAVORITES,
                ListeningTabs.BOOKMARKS,
            ),
            moved,
        )
        assertEquals(base.toSet(), moved.toSet())
    }

    @Test
    fun `moves past either end are ignored`() {
        val base = ListeningTabs.defaultOrder
        assertEquals(base, ListeningTabs.move(base, 0, -1))
        assertEquals(base, ListeningTabs.move(base, base.lastIndex, 1))
        assertEquals(base, ListeningTabs.move(base, 9, 1))
    }
}
