package aoc

import kotlin.math.abs
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

// read a file and store it in a memory buffer
@OptIn(ExperimentalForeignApi::class)
fun readAll(filename: String): String {
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

// helpers para puntos
data class Vec2(val x: Int, val y: Int) : Comparable<Vec2> {

    fun distanceTo(otherX: Int, otherY: Int): Int = abs(x - otherX) + abs(y - otherY)

    fun distanceTo(other: Vec2): Int = distanceTo(other.x, other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    inline fun up()    = Vec2(x, y - 1)
    inline fun down()  = Vec2(x, y + 1)
    inline fun left()  = Vec2(x - 1, y)
    inline fun right() = Vec2(x + 1, y)

    fun cardinalNeighbors(allowNegative: Boolean = true): List<Vec2> =
        listOf(
                Vec2(x, y - 1),
                Vec2(x - 1, y),
                Vec2(x + 1, y),
                Vec2(x, y + 1),
            )
            .filter { allowNegative || it.x >= 0 && it.y >= 0 }

    fun neighbors(allowNegative: Boolean = true): List<Vec2> =
        listOf(
                Vec2(x - 1, y - 1),
                Vec2(x, y - 1),
                Vec2(x + 1, y - 1),
                Vec2(x - 1, y),
                Vec2(x + 1, y),
                Vec2(x - 1, y + 1),
                Vec2(x, y + 1),
                Vec2(x + 1, y + 1),
            )
            .filter { allowNegative || it.x >= 0 && it.y >= 0 }

    override fun compareTo(other: Vec2): Int =
        when {
            y < other.y -> -1
            y > other.y -> 1
            x < other.x -> -1
            x > other.x -> 1
            else -> 0
        }

    companion object {
        val origin = Vec2(0, 0)

        fun of(input: String): Vec2 =
            input.split(",").map { it.trim().toInt() }.run { Vec2(this[0], this[1]) }
    }
}

// algunas cosas que pueden ser utiles para regex / parsing
// Se puede usar con Regex(numero) y debería ser util para matchear numneros con decimal, 1232.23
/*
    val numberRegex = Regex(numero)
    val match = numberRegex.matchEntire("23.45")
    println(match?.groupValues?.joinToString())
    $> 23.45
*/
val numero = """[+-]?\d+(?:\.\d+)?"""

// Los nodos pueden ser de cualquier tipo con equals/hashCode estables durante la búsqueda.
// BFS: distancia mínima en cantidad de aristas desde start a cada nodo alcanzable.
fun <N> bfs(start: N, neighbors: (N) -> Iterable<N>): Map<N, Int> {
    val distance = mutableMapOf(start to 0)
    val queue = ArrayDeque<N>()
    queue.addLast(start)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        val steps = distance.getValue(node) + 1
        for (next in neighbors(node)) {
            if (next in distance) continue
            distance[next] = steps
            queue.addLast(next)
        }
    }
    return distance
}

// DFS: nodos alcanzables, sin recursión. No calcula distancias mínimas.
fun <N> dfs(start: N, neighbors: (N) -> Iterable<N>): Set<N> {
    val visited = mutableSetOf<N>()
    val stack = ArrayDeque<N>()
    stack.addLast(start)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (!visited.add(node)) continue
        for (next in neighbors(node)) if (next !in visited) stack.addLast(next)
    }
    return visited
}

// DFS recursivo: la pila de llamadas crece con la profundidad del recorrido.
fun <N> dfsRecursive(start: N, neighbors: (N) -> Iterable<N>): Set<N> {
    val visited = mutableSetOf<N>()
    fun visit(node: N) {
        if (!visited.add(node)) return
        for (next in neighbors(node)) visit(next)
    }
    visit(start)
    return visited
}

// Para que la llamada sea de cola, guardamos las ramas pendientes en una pila explícita.
fun <N> dfsTailrec(start: N, neighbors: (N) -> Iterable<N>): Set<N> {
    val visited = mutableSetOf<N>()
    val stack = ArrayDeque<N>()
    stack.addLast(start)
    tailrec fun visitPending() {
        if (stack.isEmpty()) return
        val node = stack.removeLast()
        if (visited.add(node)) {
            for (next in neighbors(node)) if (next !in visited) stack.addLast(next)
        }
        visitPending()
    }
    visitPending()
    return visited
}

// Heap mínimo para Kotlin/Native: compara costos, no nodos.
private class MinHeap<N> {
    private val items = mutableListOf<Pair<N, Long>>()

    fun isNotEmpty() = items.isNotEmpty()

    fun push(entry: Pair<N, Long>) {
        var i = items.size
        items.add(entry)
        while (i > 0) {
            val parent = (i - 1) / 2
            if (items[parent].second <= entry.second) break
            items[i] = items[parent]
            i = parent
        }
        items[i] = entry
    }

    fun pop(): Pair<N, Long> {
        val first = items.first()
        val last = items.removeLast()
        if (items.isEmpty()) return first
        var i = 0
        while (i < items.size / 2) {
            var child = 2 * i + 1
            if (child + 1 < items.size && items[child + 1].second < items[child].second) child++
            if (last.second <= items[child].second) break
            items[i] = items[child]
            i = child
        }
        items[i] = last
        return first
    }
}

// Dijkstra: costo mínimo desde start. Cada vecino es un par (nodo, costo de la arista).
fun <N> dijkstra(start: N, neighbors: (N) -> Iterable<Pair<N, Long>>): Map<N, Long> {
    val distance = mutableMapOf(start to 0L)
    val pending = MinHeap<N>()
    pending.push(start to 0L)
    while (pending.isNotEmpty()) {
        val (node, cost) = pending.pop()
        if (cost != distance[node]) continue // Ya encontramos una ruta más barata.
        for ((next, weight) in neighbors(node)) {
            require(weight >= 0) { "Dijkstra requiere pesos no negativos" }
            val previous = distance[next]
            if (previous != null && weight > Long.MAX_VALUE - cost) continue
            require(weight <= Long.MAX_VALUE - cost) { "El costo excede Long.MAX_VALUE" }
            val candidate = cost + weight
            if (previous != null && previous <= candidate) continue
            distance[next] = candidate
            pending.push(next to candidate)
        }
    }
    return distance
}

/* Ejemplo: rutas dirigidas. También podés generar los vecinos sobre la marcha en una grilla.
    val rutas = mapOf(
        "A" to listOf("B" to 8L, "C" to 1L),
        "C" to listOf("B" to 2L)
    )
    fun vecinos(n: String) = rutas[n].orEmpty().map { it.first }

    val pasos = bfs("A", ::vecinos)
    println(pasos["B"])  // 1: A -> B
    println(pasos["Z"])  // null: inalcanzable
    println("B" in dfs("A", ::vecinos))  // true
    println(dfsRecursive("A", ::vecinos) == dfsTailrec("A", ::vecinos)) // mismo conjunto
    val costos = dijkstra("A") { rutas[it].orEmpty() }
    println(costos["B"]) // 3: A -> C -> B
*/

/* Ejemplo de grilla: '.' = piso, 'R' = rampa, '#' = pared.
    val mapa = listOf(".R.", "...", "##.")
    val inicio = Vec2(0, 0)
    val destino = Vec2(2, 0)
    val direcciones = listOf(Vec2(1, 0), Vec2(-1, 0), Vec2(0, 1), Vec2(0, -1))

    fun vecinosTransitables(p: Vec2): List<Vec2> = direcciones.map { p + it }.filter { v ->
        v.y in mapa.indices && v.x in mapa[v.y].indices && mapa[v.y][v.x] != '#'
    }
    fun esRampa(p: Vec2) = mapa[p.y][p.x] == 'R'

    fun vecinosConCosto(actual: Vec2) =
        vecinosTransitables(actual).map { siguiente ->
            siguiente to (if (esRampa(siguiente)) 4L else 1L)
        }

    // Una búsqueda por origen, aunque consultes varios destinos. No mutes las claves Vec2.
    // Si cambiás el mapa o los costos, llamá a cache.clear() antes de volver a consultar.
    val cache = mutableMapOf<Vec2, Map<Vec2, Long>>()
    fun costoMinimo(origen: Vec2, hasta: Vec2): Long? =
        cache.getOrPut(origen) { dijkstra(origen, ::vecinosConCosto) }[hasta]

    println(costoMinimo(inicio, destino))    // 4: rodear por el piso es más barato.
    println(costoMinimo(inicio, Vec2(0, 1))) // 1: mismo inicio, reutiliza los costos.
*/
