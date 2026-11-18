package aoc

import kotlin.system.exitProcess
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fprintf
import platform.posix.stderr
import kotlin.math.abs

// read a file and store it in a memory buffer
@OptIn(ExperimentalForeignApi::class)
fun readAll(filename: String): String {
    val file = fopen(filename, "r") ?: error("cannot open file: $filename")
    try {
        return buildString {
            memScoped {
                val bufferSize = 4096
                val buffer     = allocArray<ByteVar>(bufferSize)
                while (fgets(buffer, bufferSize, file) != null) {
                    append(buffer.toKString())
                }
            }
        }
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun readLines(filePath: String): List<String> {
    val file = fopen(filePath, "r") ?: error("Cannot open file: $filePath")
    val lines = mutableListOf<String>()

    try {
        memScoped {
            val bufferSize = 64 * 1024
            val buffer = allocArray<ByteVar>(bufferSize)
            while (true) {
                val line = fgets(buffer, bufferSize, file)?.toKString() ?: break
                lines.add(line.trimEnd('\r', '\n'))
            }
        }
    } finally {
        fclose(file)
    }
    return lines
}

@OptIn(ExperimentalForeignApi::class)
fun fail(message: String, status: Int = 1): Nothing {
    fprintf(stderr, "%s\n", message)
    exitProcess(status)
}

data class Vec2(var x: Int = 0, var y: Int = 0)
fun Vec2.manhatanDistnace(other: Vec2): Int = abs(x - other.x) + abs(y - other.y)
operator fun Vec2.minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
operator fun Vec2.plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
