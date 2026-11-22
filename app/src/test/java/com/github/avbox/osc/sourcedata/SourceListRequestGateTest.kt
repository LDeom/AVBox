package com.github.avbox.osc.sourcedata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceListRequestGateTest {

    @Test
    fun acceptsOnlyTheActiveListRequest() {
        assertTrue(isCurrentSourceListRequest(12L, 12L))
        assertFalse(isCurrentSourceListRequest(11L, 12L))
    }

    @Test
    fun acceptsOnlyTheActiveSortRequest() {
        assertTrue(isCurrentSourceSortRequest(4L, 4L))
        assertFalse(isCurrentSourceSortRequest(3L, 4L))
    }
}
