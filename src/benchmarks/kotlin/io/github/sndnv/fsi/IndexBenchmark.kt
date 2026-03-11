package io.github.sndnv.fsi

import io.github.sndnv.fsi.backends.MapIndex
import io.github.sndnv.fsi.backends.SharedIndex
import io.github.sndnv.fsi.backends.TrieIndex
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
class IndexBenchmark : IndexBenchmarkSetup() {
    @Param("map-mutable", "map-concurrent", "trie-mutable", "shared")
    var type: String = "map-mutable"

    private val index: Index<Int> = when (type) {
        "map-mutable" -> MapIndex.mutable()
        "map-concurrent" -> MapIndex.concurrent()
        "trie-mutable" -> TrieIndex.mutable(separator = "/")
        "shared" -> SharedIndex.default()
        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
    }

    private val other: Index<Int> = when (type) {
        "map-mutable" -> MapIndex.mutable()
        "map-concurrent" -> MapIndex.concurrent()
        "trie-mutable" -> TrieIndex.mutable(separator = "/")
        "shared" -> SharedIndex.default()
        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
    }

    private val otherWithDifferentType: Index<Int> = when (type) {
        "map-mutable", "map-concurrent" -> TrieIndex.mutable(separator = "/")
        "trie-mutable" -> SharedIndex.default()
        "shared" -> MapIndex.concurrent()
        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
    }

    @Setup
    fun before() {
        Generators.generatePaths(directoryLevels = directoryLevels, entitiesPerDirectory = entitiesPerDirectory)
            .forEach {
                index.put(it, 1)
                other.put(it, 2)
                otherWithDifferentType.put(it, 2)
            }
    }

    @TearDown
    fun after() {
        index.clear()
    }

    @Benchmark
    fun keys(): Set<String> {
        return index.keys
    }

    @Benchmark
    fun get(): Int? {
        return index.get(path = path2)
    }

    @Benchmark
    fun put(): Index<Int> {
        return index.put(path = path1, value = 1)
    }

    @Benchmark
    fun putAndAccumulate(): Index<Int> {
        return index.put(path = path2, value = 1) { _, _, _ -> 1 }
    }

    @Benchmark
    fun putAll(): Index<Int> {
        return index.putAll(entries = pathMap)
    }

    @Benchmark
    fun putAllF(): Index<Int> {
        return index.putAll(entries = pathMap) { _, _, _ -> 1 }
    }

    @Benchmark
    fun putAllKeysWithF(): Index<Int> {
        return index.putAll(paths = pathList) { _, _ -> 1 }
    }

    @Benchmark
    fun remove(): Index<Int> {
        return index.remove(path = path2)
    }

    @Benchmark
    fun contains(): Boolean {
        return index.contains(path = path2)
    }

    @Benchmark
    fun filterDropAll(): Index<Int> {
        return index.filter { _, _ -> false }
    }

    @Benchmark
    fun filterKeepAll(): Index<Int> {
        return index.filter { _, _ -> true }
    }

    @Benchmark
    fun searchNoMatch(): Map<String, Int> {
        return index.search(regexNonMatching)
    }

    @Benchmark
    fun searchMatch(): Map<String, Int> {
        return index.search(regexMatching)
    }

    @Benchmark
    fun collect(): List<Int> {
        return index.collect { _, _ -> 1 }
    }

    @Benchmark
    fun mapValues(): Index<Int> {
        return index.mapValues { _, _ -> 1 }
    }

    @Benchmark
    fun mapValuesNotNullDropAll(): Index<Int> {
        return index.mapValuesNotNull { _, _ -> null }
    }

    @Benchmark
    fun mapValuesNotNullKeepAll(): Index<Int> {
        return index.mapValuesNotNull { _, _ -> 1 }
    }

    @Benchmark
    fun forEach(): Int {
        var count: Int = 0
        index.forEach { _, _ -> count += 1 }
        return count
    }

    @Benchmark
    fun replaceAll(): Int {
        var count: Int = 0
        index.replaceAll { _, _ -> count += 1; count }
        return count
    }

    @Benchmark
    fun toMap(): Map<String, Int> {
        return index.toMap()
    }

    @Benchmark
    fun toList(): List<Pair<String, Int>> {
        return index.toList()
    }

    @Benchmark
    fun encode(): Index.Encoded<Int> {
        return index.encode { it }
    }

    @Benchmark
    fun storageEstimatedSize(): Long {
        return index.storage.estimatedSize { 0 }
    }

    @Benchmark
    fun javaEquals(): Boolean {
        return index.equals(other)
    }

    @Benchmark
    fun javaHashCode(): Int {
        return index.hashCode()
    }

    @Benchmark
    fun sameElements(): Boolean {
        return index.sameElements(other)
    }

    @Benchmark
    fun sameElementsWithOtherIndexType(): Boolean {
        return index.sameElements(otherWithDifferentType)
    }
}
