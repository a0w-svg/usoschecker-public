package pl.vistula.checker

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The university's servers (`*.vistula.edu.pl`) use a certificate issued under "Certum Trusted Root CA". Windows,
 * Android and browsers trust that root, but the Java runtime bundled with the desktop app does not ship it, so
 * every request to USOS failed the TLS handshake. This adds that one public root to the runtime's own trust anchors.
 *
 * Nothing is relaxed: host names and the whole chain are still verified, and the bundled file is used only when
 * its SHA-256 fingerprint equals [CERTUM_TRUSTED_ROOT_SHA256].
 */
object DesktopTrust {
    const val CERTUM_TRUSTED_ROOT_SHA256 = "FE7696573855773E37A95E7AD4D9CC96C30157C15D31765BA9B15704E1AE78FD"
    private const val RESOURCE = "/certs/certum_trusted_root_ca.crt"

    /** Makes the extended trust anchors the default for every HTTPS connection of this process. */
    @JvmStatic
    fun install() {
        val context = context()
        SSLContext.setDefault(context)
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
    }

    @JvmStatic
    fun bundledRoot(): X509Certificate {
        val stream = DesktopTrust::class.java.getResourceAsStream(RESOURCE)
            ?: throw IllegalStateException("Bundled root certificate is missing")
        val certificate = stream.use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
        check(fingerprint(certificate) == CERTUM_TRUSTED_ROOT_SHA256) { "Bundled root certificate was replaced" }
        return certificate
    }

    @JvmStatic
    fun fingerprint(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02X".format(it) }

    /** The runtime's default anchors plus the bundled root. */
    @JvmStatic
    fun trustManager(): X509TrustManager {
        val anchors = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        val defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
        var index = 0
        for (manager in defaults.trustManagers) {
            if (manager is X509TrustManager) for (issuer in manager.acceptedIssuers) anchors.setCertificateEntry("default-${index++}", issuer)
        }
        anchors.setCertificateEntry("certum-trusted-root-ca", bundledRoot())
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(anchors) }
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    @JvmStatic
    fun context(): SSLContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager()), null) }
}
