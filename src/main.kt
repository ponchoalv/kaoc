import kotlinizer.*
import aoc.Aoc

fun String.Bold(): String = "<b>$this</b>"

fun main(args: Array<String>) {
    println("Hello, ${"world".kotlinized}!")
    println("this should be bold".Bold())
    println("the file input is")
    println(Aoc.loadInput("input.txt"))
}
