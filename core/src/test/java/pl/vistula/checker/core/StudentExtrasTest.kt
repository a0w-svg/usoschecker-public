package pl.vistula.checker.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.vistula.checker.DemoStudentServices

class StudentExtrasTest {
    @Test
    fun plainTextRemovesMarkupScriptsAndDecodesEntities() {
        val text = StudentExtras.plainText("<p>Hello&nbsp;<b>world</b></p><script>alert(1)</script><p>a &amp; b &lt;3</p>")
        assertEquals("Hello world\na & b <3", text)
        assertFalse(text.contains("alert"))
    }

    @Test
    fun surveysKeepOnlyAllowedFieldsAndStripHtml() {
        val rows = JSONArray("""[
            {"id":"7","survey_type":"course","name":{"pl":"Ankieta","en":"Survey","secret":1},
             "headline_html":"<p>Short <i>intro</i></p>","start_date":"2026-01-01","end_date":"2026-02-01",
             "can_i_fill_out":true,"did_i_fill_out":false,"group":{"course_name":{"en":"Maths"},"group_number":2,"participants":[{"id":"1"}]}},
            {"id":"","name":"skipped"},
            {"id":"8","can_i_fill_out":"yes"}
        ]""")
        val safe = StudentExtras.safeSurveys(rows)
        assertEquals(2, safe.length())
        val first = safe.getJSONObject(0)
        assertEquals("Short intro", first.getString("headline"))
        assertTrue(first.getBoolean("can_i_fill_out"))
        assertFalse(first.getBoolean("did_i_fill_out"))
        assertEquals("2", first.getString("group_number"))
        assertFalse(first.getJSONObject("name").has("secret"))
        assertFalse(first.toString().contains("participants"))
        // A malformed boolean must not leak into the cache as a string.
        assertFalse(safe.getJSONObject(1).has("can_i_fill_out"))
    }

    @Test
    fun surveyDetailLimitsQuestionsAndKeepsStructure() {
        val questions = JSONArray()
        for (i in 1..(StudentExtras.MAX_QUESTIONS + 5)) {
            questions.put(JSONObject().put("number", "$i").put("level", 0).put("display_text_html", "<b>Q$i</b>")
                .put("possible_answers", JSONArray().put(JSONObject().put("display_text_html", "<i>Yes</i>"))).put("allow_comment", true))
        }
        val safe = StudentExtras.safeSurveyDetail(JSONObject()
            .put("id", "7").put("name", "N").put("lecturer", JSONObject().put("first_name", "Anna").put("last_name", "Kowalska"))
            .put("filled_out_count", 3).put("entitled_count", 10).put("questions", questions))
        assertEquals(StudentExtras.MAX_QUESTIONS, safe.getJSONArray("questions").length())
        assertTrue(safe.getBoolean("questions_truncated"))
        assertEquals("Q1", safe.getJSONArray("questions").getJSONObject(0).getString("text"))
        assertEquals("Yes", safe.getJSONArray("questions").getJSONObject(0).getJSONArray("answers").getString(0))
        assertEquals("Anna Kowalska", safe.getString("lecturer"))
        assertEquals(10, safe.getInt("entitled_count"))
    }

    @Test
    fun mailMessageStripsHtmlTruncatesAndRejectsUnsafeAttachmentLinks() {
        val body = "x".repeat(StudentExtras.MAX_MESSAGE_CHARS + 10)
        val safe = StudentExtras.safeMailMessage(JSONObject()
            .put("id", "5").put("subject", "Hi").put("status", "sent").put("content_format", "plain").put("content", body)
            .put("recipients_count", 4)
            .put("attachments", JSONArray()
                .put(JSONObject().put("id", "a1").put("filename", "ok.pdf").put("size", 10).put("url", "https://usosapps.vistula.edu.pl/a"))
                .put(JSONObject().put("id", "a2").put("filename", "bad.pdf").put("url", "javascript:alert(1)"))))
        assertEquals(StudentExtras.MAX_MESSAGE_CHARS, safe.getString("content").length)
        assertTrue(safe.getBoolean("content_truncated"))
        assertEquals(4, safe.getInt("recipients_count"))
        val attachments = safe.getJSONArray("attachments")
        assertEquals("https://usosapps.vistula.edu.pl/a", attachments.getJSONObject(0).getString("url"))
        assertEquals("", attachments.getJSONObject(1).getString("url"))

        val html = StudentExtras.safeMailMessage(JSONObject().put("id", "6").put("content_format", "html")
            .put("content", "<div>Hello<br>team</div><script>x()</script>"))
        assertEquals("Hello\nteam", html.getString("content"))
    }

    @Test
    fun communicationsIncludeSurveysAndStayBackwardCompatible() {
        val old = LecturerServices.safeCommunications(JSONArray(), JSONArray(), JSONArray(), JSONObject())
        assertEquals(0, old.getJSONArray("surveys").length())
        val withSurvey = LecturerServices.safeCommunications(JSONArray(), JSONArray(), JSONArray(), JSONObject(),
            JSONArray().put(JSONObject().put("id", "9").put("name", "S")))
        assertEquals("9", withSurvey.getJSONArray("surveys").getJSONObject(0).getString("id"))
    }

    @Test
    fun remittanceDetailKeepsFacultyAndDropsUnknownFields() {
        val safe = PaymentServices.remittanceDetail(JSONObject()
            .put("id", "3").put("amount", 10.5).put("remaining", 0).put("is_settled", true).put("currency", "PLN")
            .put("description", JSONObject().put("pl", "Zwrot")).put("faculty", JSONObject().put("id", "F").put("name", JSONObject().put("en", "Law")))
            .put("user", JSONObject().put("id", "private")))
        assertEquals("3", safe.getString("id"))
        assertTrue(safe.getBoolean("is_settled"))
        assertEquals("Law", safe.getJSONObject("faculty_name").getString("en"))
        assertFalse(safe.has("user"))
    }

    @Test
    fun featureAccessExtrasIncludeSurveyFilling() {
        val scopes = FeatureAccess.change("grades|studies", "extras", true)
        assertTrue(StudentServices.hasScope(scopes, "surveys_filling"))
        assertFalse(StudentServices.hasScope(FeatureAccess.change(scopes, "extras", false), "surveys_filling"))
    }

    @Test
    fun demoServicesExposeSurveysAndDetails() {
        val communications = DemoStudentServices.data().getJSONObject("communications").getJSONObject("data")
        val survey = communications.getJSONArray("surveys").getJSONObject(0)
        val detail = DemoStudentServices.demoSurveyDetail(survey.getString("id"))
        assertTrue(detail.getJSONArray("questions").length() > 0)
        val message = DemoStudentServices.demoMailMessage(communications.getJSONArray("messages").getJSONObject(0).getString("id"))
        assertTrue(message.getString("content").isNotEmpty())
        assertEquals("PLN", DemoStudentServices.demoRemittanceDetail("1").getString("currency"))
    }

    @Test
    fun safeMailMessagesAndAttachmentsMapHandleDictionaries() {
        val messagesMap = JSONObject().put("m1", JSONObject().put("id", "m1").put("subject", "Sub 1").put("content", "Body 1"))
            .put("m2", JSONObject().put("id", "m2").put("subject", "Sub 2").put("content", "Body 2"))
        val safeMessages = StudentExtras.safeMailMessages(messagesMap)
        assertEquals(2, safeMessages.length())
        assertEquals("Sub 1", safeMessages.getJSONObject(0).getString("subject"))

        val attachmentsMap = JSONObject().put("a1", JSONObject().put("id", "a1").put("filename", "test.pdf").put("size", 1024))
            .put("a2", JSONObject().put("id", "a2").put("filename", "doc.docx"))
        val safeAttachments = StudentExtras.safeMailAttachmentsMap(attachmentsMap)
        assertEquals(2, safeAttachments.length())
        assertEquals("test.pdf", safeAttachments.getJSONObject(0).getString("filename"))
        assertEquals(1024, safeAttachments.getJSONObject(0).getInt("size"))
    }
}
