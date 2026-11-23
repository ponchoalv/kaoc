import aoc.*
import kotlin.time.measureTime

val movements = arrayOf(Vec2::up,Vec2::down,Vec2::right,Vec2::left)

fun getMoveIdx(coord: Char) : Int
{
    return when(coord) {
        'N' -> 0;
        'S' -> 1;
        'W' -> 2;
        'E' -> 3;
        else -> -1;
    }
}


fun buildGrid(regex: String): Map<Vec2, Int> {
	var current = Vec2(0,0)
    val stack = ArrayDeque<Vec2>();
    val grid = mutableMapOf(current to 0);

    regex.forEach {
        when(it) {
            '(' -> stack.addLast(current)
            ')' -> current = stack.removeLast()
            '|' -> current = stack.last()
            in "NSWE" -> {
            	grid[current]?.let { distance ->
            	   current = movements[getMoveIdx(it)].invoke(current)
            	   grid[current] = if (current in grid) minOf(distance + 1, grid[current]!!) else distance + 1
                }
            }
        }
    }
    return grid
}

fun solveDay(filePath: String) {
    // Read Input to a block of String
    val input = readAll(filePath)
   	val grid = buildGrid(input)
    var part1 = 0
    var part2 = 0

    grid.values.forEach {
        if (it > part1) part1 = it
        if (it >= 1000) part2++
    }
    println("part 1 result: $part1")
    println("part 2 result: $part2")
}


fun main(args: Array<String>) {
    if (args.isEmpty()) {
        fail("Error: No input provided, please provide one.")
    }

    val input = args.first()

    val timeElapsed = measureTime {
        solveDay(input)
    }
    println("solution took: $timeElapsed")
}
