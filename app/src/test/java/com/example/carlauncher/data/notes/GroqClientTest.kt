package com.example.carlauncher.data.notes

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GroqClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var client: GroqClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = GroqClient("test-key", server.url("/openai/v1").toString().trimEnd('/'))
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private var fileCounter = 0
    private fun audio(name: String = "note_${fileCounter++}.m4a") =
        tmp.newFile(name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun transcribe_sendsCzechMultipartAndReturnsTrimmedText() = runTest {
        server.enqueue(MockResponse().setBody("""{"text":"  Ahoj světe  "}"""))

        assertEquals("Ahoj světe", client.transcribe(audio("note_test.m4a")))

        val req = server.takeRequest()
        assertEquals("/openai/v1/audio/transcriptions", req.path)
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"model\"\r\n\r\nwhisper-large-v3\r\n"))
        assertTrue(body.contains("name=\"language\"\r\n\r\ncs\r\n"))
        assertTrue(body.contains("filename=\"note_test.m4a\""))
    }

    @Test fun refine_requestsStrictSchemaAndParsesContent() = runTest {
        val content = """{"title":"Servis","text":"Zavolat do servisu.","category":"TASK"}"""
        val response = JSONObject().put(
            "choices",
            JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))),
        )
        server.enqueue(MockResponse().setBody(response.toString()))

        val r = client.refine("zavolat ehm do servisu")

        assertEquals(RefinedNote("Servis", "Zavolat do servisu.", NoteCategory.TASK), r)
        val req = server.takeRequest()
        assertEquals("/openai/v1/chat/completions", req.path)
        val sent = JSONObject(req.body.readUtf8())
        assertEquals("openai/gpt-oss-120b", sent.getString("model"))
        val format = sent.getJSONObject("response_format")
        assertEquals("json_schema", format.getString("type"))
        assertTrue(format.getJSONObject("json_schema").getBoolean("strict"))
        assertEquals(
            "zavolat ehm do servisu",
            sent.getJSONArray("messages").getJSONObject(1).getString("content"),
        )
    }

    @Test fun refine_malformedResponse_fallsBackToTranscript() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        assertEquals(RefinedNoteParser.fallback("surový text"), client.refine("surový text"))
    }

    @Test fun httpErrors_mapToGroqErrors() = runTest {
        suspend fun errorFor(code: Int): Throwable? {
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"error":"x"}"""))
            return runCatching { client.transcribe(audio()) }.exceptionOrNull()
        }
        assertTrue(errorFor(401) is GroqError.Auth)
        assertTrue(errorFor(429) is GroqError.RateLimited)
        assertTrue(errorFor(503) is GroqError.Server)
        assertTrue(errorFor(400) is GroqError.Client)
    }

    @Test fun connectionFailure_isNetworkErrorAndRetryable() = runTest {
        server.shutdown()
        val e = runCatching { client.refine("x") }.exceptionOrNull()
        assertTrue("got $e", e is GroqError.Network)
        assertTrue((e as GroqError).isRetryable)
    }
}
