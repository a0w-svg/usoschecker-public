package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LecturerTest {
    @Test
    fun directoryCombinesNamesAndHandlesUnavailablePeople() {
        val names = UsosParser.lecturerNames(
            JSONObject(
                """
                {"1":{"first_name":" Anna ","last_name":" Kowalska "},"2":null,
                 "3":{"first_name":null,"last_name":null},"4":{"first_name":"Łukasz","last_name":"Żółć"}}
                """.trimIndent()
            )
        )
        assertEquals("Anna Kowalska", names["1"])
        assertEquals("Łukasz Żółć", names["4"])
        assertFalse(names.containsKey("2"))
        assertFalse(names.containsKey("3"))
    }

    @Test
    fun lecturerIdsAreDeduplicatedAndNullsAreIgnored() {
        val rows = JSONArray(
            """
            [{"lecturer_ids":[2,1,2,null]},{"lecturer_ids":[1,3]},{"lecturer_ids":null}]
            """.trimIndent()
        )
        assertEquals(setOf("1", "2", "3"), UsosParser.lecturerIds(rows))
    }

    @Test
    fun timetableShowsFullNamesAndExplicitFallbackAndIsOrderIndependent() {
        val activity = JSONObject(
            """
            {"type":"classgroup2","sm_id":99,"start_time":"2026-10-14 09:00:00","end_time":"2026-10-14 10:30:00",
             "name":{"pl":"Statystyka"},"lecturer_ids":[2,1,null]}
            """.trimIndent()
        )
        val old = Snapshot("2026-10-13", "2026-11-10")
        val now = Snapshot(old.start, old.end)
        UsosParser.timetable(old, JSONArray().put(activity), mapOf("1" to "Anna Kowalska"))
        val value = old.records["plan:sm:99"]!!.value
        assertTrue(value.contains("Prowadzący: Anna Kowalska, ID: 2"))
        assertFalse(value.contains("null"))
        activity.put("lecturer_ids", JSONArray().put(1).put(2))
        UsosParser.timetable(now, JSONArray().put(activity), mapOf("1" to "Anna Kowalska"))
        assertTrue(Diff.between(old, now, 1, "2026-10-13").isEmpty())
    }
}
