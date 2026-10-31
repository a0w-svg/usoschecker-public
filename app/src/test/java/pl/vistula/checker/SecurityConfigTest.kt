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
    fun pendingIntentsAreNeverMutable() {
        File(moduleDir, "src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertFalse(file.name + " uses FLAG_MUTABLE", file.readText().contains("FLAG_MUTABLE"))
        }
    }
}
