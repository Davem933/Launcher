package com.example.carlauncher.data.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Groq REST (OpenAI-kompatibilní) přes HttpURLConnection — stejně jako zbytek appky, bez nové
 * síťové knihovny. Bezstavové; [apiKey] z BuildConfig.GROQ_API_KEY (viz VoiceNotesModule).
 */
class GroqClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : NoteAi {

    override suspend fun transcribe(audio: File): String = withContext(Dispatchers.IO) {
        val boundary = "----CarLauncher${System.nanoTime()}"
        val body = post("audio/transcriptions", "multipart/form-data; boundary=$boundary") { out ->
            fun field(name: String, value: String) = out.write(
                "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
                    .toByteArray(),
            )
            field("model", TRANSCRIBE_MODEL)
            field("language", "cs")
            field("response_format", "json")
            out.write(
                ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
                    "filename=\"${audio.name}\"\r\nContent-Type: audio/mp4\r\n\r\n").toByteArray(),
            )
            audio.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        runCatching { JSONObject(body).getString("text").trim() }
            .getOrElse { throw GroqError.Server(200) }
    }

    override suspend fun refine(rawTranscript: String): RefinedNote = withContext(Dispatchers.IO) {
        val request = JSONObject()
            .put("model", REFINE_MODEL)
            .put("temperature", 0.2)
            .put("reasoning_effort", "low")
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", rawTranscript)),
            )
            .put("response_format", responseFormat())
        val body = post("chat/completions", "application/json") {
            it.write(request.toString().toByteArray())
        }
        val content = runCatching {
            JSONObject(body).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }.getOrNull()
        RefinedNoteParser.parse(content, rawTranscript)
    }

    private fun post(path: String, contentType: String, writeBody: (OutputStream) -> Unit): String {
        val conn = try {
            URL("$baseUrl/$path").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw GroqError.Network(e)
        }
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", contentType)
            conn.outputStream.use(writeBody)
            val code = conn.responseCode
            if (code in 200..299) return conn.inputStream.bufferedReader().use { it.readText() }
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw when {
                code == 401 || code == 403 -> GroqError.Auth(code)
                code == 429 -> GroqError.RateLimited()
                code >= 500 -> GroqError.Server(code)
                else -> GroqError.Client(code, error)
            }
        } catch (e: IOException) {
            throw GroqError.Network(e)
        } finally {
            conn.disconnect()
        }
    }

    private fun responseFormat(): JSONObject {
        val categories = JSONArray().apply { NoteCategory.entries.forEach { put(it.name) } }
        val schema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("title", JSONObject().put("type", "string"))
                    .put("text", JSONObject().put("type", "string"))
                    .put("category", JSONObject().put("type", "string").put("enum", categories)),
            )
            .put("required", JSONArray().put("title").put("text").put("category"))
            .put("additionalProperties", false)
        return JSONObject()
            .put("type", "json_schema")
            .put(
                "json_schema",
                JSONObject().put("name", "voice_note").put("strict", true).put("schema", schema),
            )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val TRANSCRIBE_MODEL = "whisper-large-v3"
        const val REFINE_MODEL = "openai/gpt-oss-120b"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000

        private val SYSTEM_PROMPT = """
            Jsi asistent, který upravuje diktované poznámky řidiče. Dostaneš surový přepis řeči v češtině.
            Vrať JSON s poli:
            - "title": výstižný nadpis, nejvýše 6 slov, bez tečky na konci.
            - "text": učesaný text poznámky — odstraň vycpávková slova (ehm, jako, prostě…), opakování
              a přeřeknutí, doplň interpunkci a velká písmena. Význam neměň, nic nepřidávej.
            - "category": IDEA (nápad, myšlenka), TASK (úkol, něco udělat, zavolat), SHOPPING (koupit),
              CAR (auto, servis, tankování, pneumatiky), OTHER (vše ostatní).
        """.trimIndent()
    }
}
