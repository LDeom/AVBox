package com.github.avbox.osc.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSourceTreePermissionTest {

    @Test
    fun rememberedTreeRequiresExactPersistedReadGrant() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        assertTrue(hasPersistedReadGrant(tree, setOf(tree)))
        assertFalse(hasPersistedReadGrant(tree, emptySet()))
        assertFalse(
            hasPersistedReadGrant(
                tree,
                setOf("content://com.android.externalstorage.documents/tree/primary%3ADocuments"),
            ),
        )
    }
}
