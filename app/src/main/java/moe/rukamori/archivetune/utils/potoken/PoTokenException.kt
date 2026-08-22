package moe.rukamori.archivetune.utils.potoken

class PoTokenException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class BrokenWebViewException(
    message: String,
) : Exception(message)

fun classifyJsError(error: String): Exception =
    if (error.contains("SyntaxError")) {
        BrokenWebViewException(error)
    } else {
        PoTokenException(error)
    }
