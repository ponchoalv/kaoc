import kotlinizer.*
import aoc.Aoc
import greeter.*



fun String.Bold(): String = "<b>$this</b>"

fun main(args: Array<String>) {
    println("Hello, ${"world".kotlinized}!")
    println("this should be bold".Bold())
    println("the file input is")
    println(Aoc.readAll("input.txt"))
    println(Aoc.readLines("input.txt"))
    println(greet("Alfonso"))                    // Hi Alfonso
    println(greet("Mr. H"))                    // Hi Mr. H
    println(greet(null))                         // Hi stranger
    val arr = arrayOf(1, 2, 3, 4, 5)
    println("array: ${arr.joinToString()}")
}
