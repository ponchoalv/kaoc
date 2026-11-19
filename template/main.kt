import aoc.*
import kotlin.time.measureTime

// 	we have this functions available
//  val point1 = Vec2(3,4)
// 	val point2 = Vec2(5,6)

// 	println("distance bewteen $point1 and $point2 is ${point1.manhatanDistnace(point2)}")
// 	println("$point1 + $point2 ${point1 + point2}")
// 	println("$point1 - $point2 ${point1 - point2}")

fun solvePart1(filePath: String) {
	// Read Input to a block of String
	val input = readAll(filePath)

	println("part 1 result: ${input}")
}

fun solvePart2(filePath: String) {
	// Read Input in lines
	val lines = readLines(filePath)
	println("part 2 result ${lines.joinToString()}")
}

fun main(args: Array<String>) {
	if (args.isEmpty()) {
        fail("Error: No input provided, please provide one.")
    }

	val input = args.first()

	val elapsedPart1 = measureTime {
        solvePart1(input)
    }
    println("Part 1 took: $elapsedPart1")

	val elapsedPart2 = measureTime {
        solvePart2(input)
    }
    println("Part 2 took: $elapsedPart2")
}