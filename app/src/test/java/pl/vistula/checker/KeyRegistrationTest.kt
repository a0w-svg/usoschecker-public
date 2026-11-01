package pl.vistula.checker

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KeyRegistrationTest {
    private class Fake(var body: String, var status: Int = 200) : HttpURLConnection(URL(UsosClient.BASE)) {
        val sent = ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream(): InputStream = inputStream
        override fun getOutputStream(): OutputStream = sent
        override fun getHeaderFields(): Map<String, List<String>> = mapOf("Set-Cookie" to listOf("csrftoken=tok123; Path=/"))
    }

    @Test
    fun postsTheFormAndReturnsTheIssuedKeys() {
        val fake = Fake("""{"status":"success","consumer_key":"abc","consumer_secret":"def"}""")
        val address = ArrayList<String>()
        val keys = KeyRegistration.register("My app", "", "me@example.com", UsosClient.ConnectionSource {
            address.add(it)
            if (it.endsWith("developers/")) Fake("<html>") else fake
        })
        assertEquals(listOf(UsosClient.BASE + "developers/", UsosClient.BASE + "developers/submit"), address)
        assertEquals("tok123", fake.getRequestProperty("X-CSRFToken"))
        assertEquals("csrftoken=tok123", fake.getRequestProperty("Cookie"))
        assertEquals("POST", fake.requestMethod)
        assertEquals("appname=My+app&appurl=&email=me%40example.com", fake.sent.toString())
        assertEquals("abc", keys.key)
        assertEquals("def", keys.secret)
    }

    @Test
    fun serverRefusalSurfacesItsOwnMessage() {
        val fake = Fake("""{"status":"error","message":"Bad email"}""", 400)
        try {
            KeyRegistration.register("a", "", "x", UsosClient.ConnectionSource { fake })
            fail()
        } catch (e: KeyRegistration.Rejected) {
            assertEquals("Bad email", e.message)
        }
    }

    @Test
    fun nonJsonAnswerIsAnIoFailure() {
        try {
            KeyRegistration.parse("<html>")
            fail()
        } catch (e: java.io.IOException) {
            assertTrue(e !is KeyRegistration.Rejected)
        }
    }

    @Test
    fun revokeSendsTheKeyPairAndReadsTheAnswer() {
        val address = arrayOf("")
        val sent = KeyRegistration.revoke("k 1", "s&2", UsosClient.ConnectionSource { address[0] = it; Fake("true") })
        assertTrue(sent)
        assertTrue(address[0].endsWith("revoke_consumer_key?consumer_key=k+1&consumer_secret=s%262"))
        assertEquals(false, KeyRegistration.revoke("a", "b", UsosClient.ConnectionSource { Fake("false") }))
        assertEquals(false, KeyRegistration.revoke("a", "b", UsosClient.ConnectionSource { Fake("""{"message": "Key-pair not found."}""", 400) }))
    }
}
