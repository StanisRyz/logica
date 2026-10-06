package com.stanisryz.logica.ui

import com.stanisryz.logica.ui.components.LICENSE_NOTICES
import com.stanisryz.logica.ui.components.licenseNoticesFor
import com.stanisryz.logica.ui.components.reflowLicense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The shared «Licences» list: every licence text it opens exists, and Web leaves out Android-only SDKs. */
class LicensesTest {
    private val resources =
        listOf(
            File("../shared-ui"),
            File("shared-ui"),
        ).first { it.isDirectory }.resolve("src/commonMain/composeResources")

    @Test
    fun everyLicenseTextFileIsBundled() {
        LICENSE_NOTICES.mapNotNull { it.textFile }.toSet().forEach { file ->
            assertTrue(file, resources.resolve(file).isFile)
        }
    }

    @Test
    fun theWebListLeavesOutTheAndroidAdvertisingSdk() {
        assertTrue(licenseNoticesFor(android = true).any { it.name == "Yandex Mobile Ads SDK" })
        assertTrue(licenseNoticesFor(android = false).none { it.androidOnly })
        assertEquals(LICENSE_NOTICES.size, licenseNoticesFor(android = true).size)
    }

    @Test
    fun theWebListShowsNoAddresses() {
        val shown = licenseNoticesFor(android = false).flatMap { listOfNotNull(it.name, it.holder, it.license, it.url, it.licenseUrl) }
        assertTrue(shown.none { text -> listOf("http", "www.", ".org", ".com", ".net").any { it in text } })
        assertTrue(licenseNoticesFor(android = true).all { it.url != null })
    }

    @Test
    fun reflowJoinsTheLinesOfAParagraphAndKeepsParagraphs() {
        assertEquals(
            "MIT License\n\nPermission is hereby granted, free of charge.",
            reflowLicense("MIT License\r\n\r\nPermission is hereby\n  granted, free of charge.\n"),
        )
    }
}
