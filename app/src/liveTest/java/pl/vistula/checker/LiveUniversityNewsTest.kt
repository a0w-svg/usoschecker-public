package pl.vistula.checker

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Public website only. Does not read or send application/account credentials. */
@RunWith(AndroidJUnit4::class)
class LiveUniversityNewsTest {
    @Test fun universityNewsLoadsOnAndroidWithoutUsosCredentials() {
        val result = UniversityNews.load()
        assertEquals("university", result.getString("source"))
        assertTrue(result.getJSONArray("pl").length() > 0)
        assertTrue(result.getJSONArray("en").length() > 0)
    }
}
