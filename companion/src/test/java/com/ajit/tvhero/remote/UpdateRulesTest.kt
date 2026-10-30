package com.ajit.tvhero.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {
    @Test fun versionsCompareNumerically() {
        assertTrue(UpdateRules.compareVersions("2.10.0", "2.9.1") > 0)
        assertEquals(0, UpdateRules.compareVersions("v2.6.0", "2.6"))
        assertTrue(UpdateRules.compareVersions("2.6.0", "2.6.1") < 0)
        assertTrue(UpdateRules.compareVersions("3.0.0-beta", "2.99") > 0)
    }

    @Test fun onlyGithubOverHttps() {
        assertTrue(UpdateRules.allowed(UpdateRules.MANIFEST))
        assertTrue(UpdateRules.allowed("https://objects.githubusercontent.com/x/y.apk?sig=1"))
        assertFalse(UpdateRules.allowed("http://github.com/x"))
        assertFalse(UpdateRules.allowed("https://github.com.evil.example/x"))
        assertFalse(UpdateRules.allowed("https://user@github.com/x"))
        assertFalse(UpdateRules.allowed("https://github.com:8443/x"))
        assertFalse(UpdateRules.allowed("file:///sdcard/x.apk"))
    }

    @Test fun acceptsOnlyOurSignedPackageAndVersion() {
        val ok = UpdateRules.RELEASE_CERT_SHA256
        assertNull(UpdateRules.acceptable("com.ajit.tvhero", 17, "com.ajit.tvhero", 17, listOf(ok), ok))
        assertNull(UpdateRules.acceptable("com.ajit.tvhero", 17, "com.ajit.tvhero", 17, listOf(ok.uppercase()), null))
        assertNotNull(UpdateRules.acceptable("com.ajit.tvhero", 17, "com.evil", 17, listOf(ok), ok))
        assertNotNull(UpdateRules.acceptable("com.ajit.tvhero", 17, "com.ajit.tvhero", 16, listOf(ok), ok))
        assertNotNull(UpdateRules.acceptable("com.ajit.tvhero", 17, "com.ajit.tvhero", 17, listOf("00".repeat(32)), ok))
        assertNotNull(UpdateRules.acceptable("com.ajit.tvhero", 17, "com.ajit.tvhero", 17, emptyList(), ok))
        assertNotNull(UpdateRules.acceptable("com.ajit.tvhero", 17, "com.ajit.tvhero", 17, listOf(ok), "11".repeat(32)))
    }
}
