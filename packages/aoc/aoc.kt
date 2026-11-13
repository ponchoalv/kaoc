package aoc

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen

// read a file and store it in a memory buffer
@OptIn(ExperimentalForeignApi::class)
internal fun load_input(filename: String): String {
    val file = fopen(filename, "r") ?: error("cannot open file: $filename")
    try {
        return buildString {
            memScoped {
                val bufferSize = 4096
                val buffer = allocArray<ByteVar>(bufferSize)
                while (fgets(buffer, bufferSize, file) != null) {
                    append(buffer.toKString())
                }
            }
        }
    } finally {
        fclose(file)
    }
}

object Aoc {
	fun loadInput(filename: String): String = load_input(filename)
}