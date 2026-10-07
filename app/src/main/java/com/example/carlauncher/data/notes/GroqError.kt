package com.example.carlauncher.data.notes

/** Chyby volání Groq. `message` je česky — zobrazuje se u poznámky ve stavu FAILED. */
sealed class GroqError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : GroqError("Bez připojení ke Groq", cause)
    class RateLimited : GroqError("Překročen denní limit Groq (429)")
    class Server(val code: Int) : GroqError("Chyba serveru Groq ($code)")
    class Auth(val code: Int) : GroqError("Neplatný Groq API klíč ($code)")
    class Client(val code: Int, body: String) : GroqError("Chyba požadavku Groq ($code): ${body.take(200)}")

    /** Síť, rate limit a 5xx se zkouší znovu (WorkManager backoff); klíč a 4xx ne. */
    val isRetryable: Boolean get() = this is Network || this is RateLimited || this is Server
}
