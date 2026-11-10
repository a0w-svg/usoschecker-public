package pl.vistula.checker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Guards security-relevant configuration so a careless edit fails the build instead of shipping. */
class SecurityConfigTest {
    private val moduleDir: File = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").exists() }
    private val android = "http://schemas.android.com/apk/res/android"

    private fun manifestElements(tag: String): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(moduleDir, "src/main/AndroidManifest.xml"))
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        val exported = (manifestElements("activity") + manifestElements("receiver") + manifestElements("service") + manifestElements("provider"))
            .filter { it.getAttributeNS(android, "exported") == "true" }
            .map { it.getAttributeNS(android, "name") }
        assertEquals(listOf(".MainActivity"), exported)
    }

    @Test
    fun everyComponentWithAnIntentFilterDeclaresExportedExplicitly() {
        (manifestElements("activity") + manifestElements("receiver") + manifestElements("service")).forEach { element ->
            assertTrue(
                element.getAttributeNS(android, "name") + " must set android:exported",
                element.hasAttributeNS(android, "exported")
            )
        }
    }

    @Test
    fun applicationDisablesBackupAndCleartextTraffic() {
        val application = manifestElements("application").single()
        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
        assertEquals("false", application.getAttributeNS(android, "usesCleartextTraffic"))
        assertFalse(application.getAttributeNS(android, "debuggable") == "true")
    }

    @Test
    fun networkSecurityConfigNeverPermitsCleartext() {
        val text = File(moduleDir, "src/main/res/xml/network_security_config.xml").readText()
        assertFalse(text.contains("cleartextTrafficPermitted=\"true\""))
        assertFalse(text.contains("src=\"user\""))
    }

    @Test
    fun bundledTrustAnchorIsThePinnedCertumRootAndOnlyForUniversityHosts() {
        val pem = File(moduleDir, "src/main/res/raw/certum_trusted_root_ca.crt")
        val certificate = pem.inputStream().use {
            java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(it) as java.security.cert.X509Certificate
        }
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02X".format(it) }
        assertEquals("FE7696573855773E37A95E7AD4D9CC96C30157C15D31765BA9B15704E1AE78FD", fingerprint)
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(moduleDir, "src/main/res/xml/network_security_config.xml"))
        val anchors = doc.getElementsByTagName("certificates")
        val bundled = (0 until anchors.length).map { anchors.item(it) as Element }.filter { it.getAttribute("src").startsWith("@raw/") }
        assertEquals(1, bundled.size)
        assertEquals("@raw/certum_trusted_root_ca", bundled.single().getAttribute("src"))
        val config = bundled.single().parentNode.parentNode as Element
        assertEquals("domain-config", config.tagName)
        val domains = config.getElementsByTagName("domain")
        assertEquals(listOf("vistula.edu.pl"), (0 until domains.length).map { domains.item(it).textContent.trim() })
    }

    @Test
    fun pendingIntentsAreNeverMutable() {
        File(moduleDir, "src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertFalse(file.name + " uses FLAG_MUTABLE", file.readText().contains("FLAG_MUTABLE"))
        }
    }
}
