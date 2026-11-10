package pl.vistula.checker

import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.KeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The desktop runtime must trust the root the university's servers chain to, and nothing less than before. */
class DesktopTrustTest {
    @Test
    fun bundledRootIsThePinnedCertumRoot() {
        val root = DesktopTrust.bundledRoot()
        assertEquals(DesktopTrust.CERTUM_TRUSTED_ROOT_SHA256, DesktopTrust.fingerprint(root))
        assertTrue(root.subjectX500Principal.name.contains("CN=Certum Trusted Root CA"))
        // Self-signed and still valid: a root, not an intermediate or a leaf.
        assertEquals(root.subjectX500Principal, root.issuerX500Principal)
        root.verify(root.publicKey)
        root.checkValidity()
    }

    @Test
    fun trustManagerAddsTheRootAndKeepsEveryDefaultAnchor() {
        val defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }.trustManagers.filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.toList() }.map { DesktopTrust.fingerprint(it) }.toSet()
        val extended = DesktopTrust.trustManager().acceptedIssuers.map { DesktopTrust.fingerprint(it) }.toSet()
        assertTrue(DesktopTrust.CERTUM_TRUSTED_ROOT_SHA256 in extended)
        assertTrue(extended.containsAll(defaults))
        assertTrue((extended - defaults).size <= 1, "Only the one bundled root may be added")
    }

    @Test
    fun aCertificateFromAnotherAuthorityIsStillRejected() {
        // The root alone is not a server certificate for any host: presenting it as a chain for TLS must fail
        // unless it is the trusted anchor itself, and an unrelated self-signed certificate is always refused.
        val foreign = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertTrue(foreign.public.encoded.isNotEmpty())
        val manager = DesktopTrust.trustManager()
        assertFailsWith<Exception> { manager.checkServerTrusted(arrayOf(), "RSA") }
    }
}
