package com.rmm.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationIdentityTest {
    @Test
    fun packageNameUsesRmmNamespace() {
        assertEquals("com.rmm.app", ApplicationIdentityTest::class.java.packageName)
    }
}

