package greeter

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
fun greet(name: String?): String? {
    val ptr = say_my_name(name) ?: return null
    try {
        return ptr.toKString()
    } finally {
        free_greeting(ptr)
    }
}