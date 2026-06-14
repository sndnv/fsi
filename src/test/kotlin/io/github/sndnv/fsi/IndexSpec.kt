package io.github.sndnv.fsi

import io.github.sndnv.fsi.backends.MapIndex
import io.github.sndnv.fsi.backends.SharedIndex
import io.github.sndnv.fsi.backends.TrieIndex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.spec.style.wordSpec
import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

class IndexSpec : WordSpec({
    include(indexSpec(type = "map-mutable"))
    include(indexSpec(type = "map-concurrent"))
    include(indexSpec(type = "map-custom"))
    include(indexSpec(type = "trie-mutable"))
    include(indexSpec(type = "shared"))

    listOf("/", "\\").forEach { separator ->
        include(schemeSpec(type = "map-mutable", separator = separator))
        include(schemeSpec(type = "map-concurrent", separator = separator))
        include(schemeSpec(type = "map-custom", separator = separator))
        include(schemeSpec(type = "trie-mutable", separator = separator))
        include(schemeSpec(type = "shared", separator = separator))
    }
}) {
    @Suppress("LargeClass")
    companion object {
        fun indexSpec(type: String) = wordSpec {
            fun createIndex(): Index<Int> = when (type) {
                "map-mutable" -> MapIndex.mutable()
                "map-concurrent" -> MapIndex.concurrent()
                "map-custom" -> MapIndex.custom(map = HashMap())
                "trie-mutable" -> TrieIndex.mutable(separator = "/")
                "shared" -> SharedIndex.default()
                else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
            }

            "An Index ($type)" should {
                "have support for basic retrieval and update operations" {
                    val index = createIndex()

                    index.size should be(0)

                    withClue("put") {
                        index.put(path = "/a/b/c", value = 1)
                        index.size should be(1)
                        index.get(path = "/a/b/c") should be(1)
                    }

                    withClue("putAndAccumulate") {
                        index.put(path = "/a/b/c", value = 5) { _, existing, current -> (existing ?: 0) + current }
                        index.put(path = "/a/b/d", value = 5) { _, existing, current -> (existing ?: 0) + current }

                        index.size should be(2)
                        index.get(path = "/a/b/c") should be(6)
                        index.get(path = "/a/b/d") should be(5)
                    }

                    withClue("putAll") {
                        index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 1, "/a/b" to 1))

                        index.size should be(3)
                        index.get(path = "/a/b/c") should be(1)
                        index.get(path = "/a/b/d") should be(1)
                        index.get(path = "/a/b") should be(1)
                    }

                    withClue("putAllF") {
                        index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/e" to 1, "/a/f" to 1)) { _, existing, current ->
                            (existing ?: 0) + current
                        }

                        index.size should be(5)
                        index.get(path = "/a/b/c") should be(2)
                        index.get(path = "/a/b/d") should be(1)
                        index.get(path = "/a/b/e") should be(1)
                        index.get(path = "/a/b") should be(1)
                        index.get(path = "/a/f") should be(1)
                    }

                    withClue("putAllKeysWithF") {
                        index.putAll(paths = listOf("/a/b/c", "/a/b/d", "/a/b/g")) { path, existing ->
                            if (path == "/a/b/c" || path == "/a/b/d") {
                                (existing ?: 0) + 1
                            } else {
                                999
                            }
                        }

                        index.size should be(6)
                        index.get(path = "/a/b/c") should be(3)
                        index.get(path = "/a/b/d") should be(2)
                        index.get(path = "/a/b/e") should be(1)
                        index.get(path = "/a/b/g") should be(999)
                        index.get(path = "/a/b") should be(1)
                        index.get(path = "/a/f") should be(1)
                    }

                    withClue("contains") {
                        index.contains(path = "/a/b/c") should be(true)
                        index.contains(path = "/a/b/d") should be(true)
                        index.contains(path = "/a/b/e") should be(true)
                        index.contains(path = "/a/b/g") should be(true)
                        index.contains(path = "/a/b") should be(true)
                        index.contains(path = "/a/f") should be(true)
                        index.contains(path = "/a/x") should be(false)
                        index.contains(path = "/a") should be(false)
                        index.contains(path = "/") should be(false)
                        index.contains(path = "") should be(false)
                    }

                    withClue("remove") {
                        index.remove(path = "/a/b/d")

                        index.size should be(5)
                        index.get(path = "/a/b/c") should be(3)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(1)
                        index.get(path = "/a/b/g") should be(999)
                        index.get(path = "/a/b") should be(1)
                        index.get(path = "/a/f") should be(1)

                        index.remove(path = "/a/b")

                        index.size should be(4)
                        index.get(path = "/a/b/c") should be(3)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(1)
                        index.get(path = "/a/b/g") should be(999)
                        index.get(path = "/a/b") should be(null)
                        index.get(path = "/a/f") should be(1)

                        index.remove(path = "/a/b")

                        index.size should be(4)
                        index.get(path = "/a/b/c") should be(3)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(1)
                        index.get(path = "/a/b/g") should be(999)
                        index.get(path = "/a/b") should be(null)
                        index.get(path = "/a/f") should be(1)

                        index.remove(path = "/a/x")

                        index.size should be(4)
                        index.get(path = "/a/b/c") should be(3)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(1)
                        index.get(path = "/a/b/g") should be(999)
                        index.get(path = "/a/b") should be(null)
                        index.get(path = "/a/f") should be(1)

                        index.remove(path = "/a/b/g")

                        index.size should be(3)
                        index.get(path = "/a/b/c") should be(3)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(1)
                        index.get(path = "/a/b/g") should be(null)
                        index.get(path = "/a/b") should be(null)
                        index.get(path = "/a/f") should be(1)

                        index.remove(path = "/a/b/e")

                        index.size should be(2)
                        index.get(path = "/a/b/c") should be(3)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(null)
                        index.get(path = "/a/b/g") should be(null)
                        index.get(path = "/a/b") should be(null)
                        index.get(path = "/a/f") should be(1)

                        index.remove(path = "/a/b/c")

                        index.size should be(1)
                        index.get(path = "/a/b/c") should be(null)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(null)
                        index.get(path = "/a/b/g") should be(null)
                        index.get(path = "/a/b") should be(null)
                        index.get(path = "/a/f") should be(1)

                        index.remove(path = "/a/f")

                        index.size should be(0)
                        index.get(path = "/a/b/c") should be(null)
                        index.get(path = "/a/b/d") should be(null)
                        index.get(path = "/a/b/e") should be(null)
                        index.get(path = "/a/b/g") should be(null)
                        index.get(path = "/a/b") should be(null)
                        index.get(path = "/a/f") should be(null)
                    }

                    withClue("clear") {
                        index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 1, "/a/b" to 1, "/" to 99))
                        index.size should be(4)

                        val count = AtomicInteger(0)

                        index.clear()
                        index.size should be(0)

                        index.forEach { _, _ -> count.incrementAndGet() }
                        count.get() should be(0)
                        index.get("/") should be(null)
                    }
                }

                "have support for filter and search operations" {
                    val index = createIndex()

                    index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3)) { _, existing, current ->
                        (existing ?: 0) + current
                    }

                    index.size should be(3)
                    index.get(path = "/a/b/c") should be(1)
                    index.get(path = "/a/b/d") should be(2)
                    index.get(path = "/a/b") should be(3)

                    withClue("filter") {
                        val result = index.filter { _, value -> value >= 2 }

                        result.size should be(2)
                        result.get(path = "/a/b/c") should be(null)
                        result.get(path = "/a/b/d") should be(2)
                        result.get(path = "/a/b") should be(3)
                    }

                    withClue("search") {
                        val result = index.search(expr = Pattern.compile(".*[c|d]$"))

                        result.size should be(2)
                        result["/a/b/c"] should be(1)
                        result["/a/b/d"] should be(2)
                        result["/a/b"] should be(null)
                    }
                }

                "have support for iteration operations" {
                    val index = createIndex()

                    index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3)) { _, existing, current ->
                        (existing ?: 0) + current
                    }

                    index.size should be(3)

                    val collected = mutableMapOf<String, Int>()

                    withClue("forEach") {
                        index.forEach { path, value -> collected[path] = value }

                        collected.size should be(3)
                        collected["/a/b/c"] should be(1)
                        collected["/a/b/d"] should be(2)
                        collected["/a/b"] should be(3)
                    }
                }

                "have support for transformation operations" {
                    val index = createIndex()

                    index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3)) { _, existing, current ->
                        (existing ?: 0) + current
                    }

                    index.size should be(3)
                    index.get(path = "/a/b/c") should be(1)
                    index.get(path = "/a/b/d") should be(2)
                    index.get(path = "/a/b") should be(3)

                    withClue("collect") {
                        val result = index.collect { path, value ->
                            if (path != "/a/b/d") {
                                path to "a_$value"
                            } else {
                                null
                            }
                        }.toMap()

                        result.size should be(2)
                        result["/a/b/c"] should be("a_1")
                        result["/a/b"] should be("a_3")
                    }

                    withClue("mapValues") {
                        val result = index.mapValues { _, value -> "b_$value" }

                        result.size should be(3)
                        result.get(path = "/a/b/c") should be("b_1")
                        result.get(path = "/a/b/d") should be("b_2")
                        result.get(path = "/a/b") should be("b_3")
                    }

                    withClue("mapValuesNotNull") {
                        val result = index.mapValuesNotNull { path, value -> if (path == "/a/b/d") null else "b_$value" }

                        result.size should be(2)
                        result.get(path = "/a/b/c") should be("b_1")
                        result.get(path = "/a/b/d") should be(null)
                        result.get(path = "/a/b") should be("b_3")
                    }

                    withClue("replaceAll") {
                        index.replaceAll { _, value -> value * 2 }

                        index.size should be(3)
                        index.get(path = "/a/b/c") should be(2)
                        index.get(path = "/a/b/d") should be(4)
                        index.get(path = "/a/b") should be(6)
                    }
                }

                "have support for conversion operations" {
                    val index = createIndex()

                    val original = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3)

                    index.putAll(entries = original) { _, existing, current ->
                        (existing ?: 0) + current
                    }

                    index.size should be(3)
                    index.get(path = "/a/b/c") should be(1)
                    index.get(path = "/a/b/d") should be(2)
                    index.get(path = "/a/b") should be(3)

                    withClue("toMap") {
                        index.toMap() should be(original)
                    }

                    withClue("toList") {
                        index.toList().sortedBy { it.second } should be(original.toList().sortedBy { it.second })
                    }
                }

                "have support for calculating storage size" {
                    val index = createIndex()

                    val entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3)

                    val expectedKeysSize: Long = when (type) {
                        "map-mutable", "map-concurrent", "map-custom" -> entries.keys.sumOf { it.length }.toLong()
                        "trie-mutable" -> 4 // +1 for "a", +1 "b", +1 for "c", +1 for "d"
                        "shared" -> 3 // +1 "b", +1 for "c", +1 for "d" ("a" has no value so it's not considered)
                        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
                    }

                    val expectedValuesSize: Long = Long.SIZE_BYTES.toLong() * 3 // *3, one for each value

                    index.putAll(entries = entries) { _, existing, current ->
                        (existing ?: 0) + current
                    }

                    index.size should be(3)

                    index.storage.estimatedSize { 0 } should be(expectedKeysSize)
                    index.storage.estimatedSize { Long.SIZE_BYTES.toLong() } should be(expectedKeysSize + expectedValuesSize)
                }

                "have support for encoding and decoding" {
                    val original = createIndex()

                    original.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

                    val encoded = original.encode { it }

                    val decodedFromEncoded = when (type) {
                        "map-mutable" -> MapIndex.decodedMutable(encoded) { it }
                        "map-concurrent" -> MapIndex.decodedConcurrent(encoded) { it }
                        "map-custom" -> MapIndex.decodedCustom(encoded, HashMap()) { it }
                        "trie-mutable" -> TrieIndex.decoded(encoded, "/") { it }
                        "shared" -> SharedIndex.decoded(encoded) { it }
                        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
                    }

                    original.sameElements(decodedFromEncoded) should be(true)
                }

                "fail to decode unexpected objects" {
                    val encoded = TestEncoded()

                    val e = shouldThrow<IllegalArgumentException> {
                        when (type) {
                            "map-mutable" -> MapIndex.decodedMutable(encoded) { it }
                            "map-concurrent" -> MapIndex.decodedConcurrent(encoded) { it }
                            "map-custom" -> MapIndex.decodedCustom(encoded, HashMap()) { it }
                            "trie-mutable" -> TrieIndex.decoded(encoded, "/") { it }
                            "shared" -> SharedIndex.decoded(encoded) { it }
                            else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
                        }
                    }

                    e.message shouldContain """Expected \[io.github.sndnv.fsi.backends.(Trie|Map)Index.Encoded(Node)?] but 
                        |\[io.github.sndnv.fsi.IndexSpec.TestEncoded] provided""".trimMargin().replace("\n", "").toRegex()
                }

                "provide all stored paths as keys" {
                    val index = createIndex()

                    val original = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3)

                    index.putAll(entries = original) { _, existing, current ->
                        (existing ?: 0) + current
                    }

                    index.size should be(3)
                    index.get(path = "/a/b/c") should be(1)
                    index.get(path = "/a/b/d") should be(2)
                    index.get(path = "/a/b") should be(3)

                    index.keys should be(original.keys)
                }

                "provide equals and hashCode support".config(enabledIf = { type != "shared" }) {
                    // Note: SharedIndex does not provide `equals` or `hashCode`

                    val original = createIndex()
                    original.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

                    original.size should be(3)
                    original.get(path = "/a/b/c") should be(1)
                    original.get(path = "/a/b/d") should be(2)
                    original.get(path = "/a/b") should be(3)

                    original.equals(original) should be(true)

                    val same = createIndex()
                    same.putAll(entries = original.toList().toMap()) // force a change of collection (list then map)

                    same.size should be(3)
                    same.get(path = "/a/b/c") should be(1)
                    same.get(path = "/a/b/d") should be(2)
                    same.get(path = "/a/b") should be(3)

                    same.equals(same) should be(true)

                    original should be(same)
                    original.hashCode() should be(same.hashCode())

                    val other1 = createIndex()
                    other1.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/e" to 4))

                    other1.size should be(3)
                    other1.get(path = "/a/b/c") should be(1)
                    other1.get(path = "/a/b/d") should be(2)
                    other1.get(path = "/a/e") should be(4)

                    other1.equals(other1) should be(true)

                    other1 shouldNot be(original)
                    other1 shouldNot be(same)

                    other1.hashCode() shouldNot be(original.hashCode())
                    other1.hashCode() shouldNot be(same.hashCode())

                    val other2 = createIndex()
                    other2.putAll(entries = mapOf("/a" to 5))

                    other2.size should be(1)
                    other2.get(path = "/a") should be(5)

                    other2.equals(other2) should be(true)

                    other2 shouldNot be(original)
                    other2 shouldNot be(same)

                    other2.hashCode() shouldNot be(original.hashCode())
                    other2.hashCode() shouldNot be(same.hashCode())

                    val other3 = createIndex()
                    other3.putAll(entries = mapOf("/x" to 5))

                    other3.size should be(1)
                    other3.get(path = "/x") should be(5)

                    other3.equals(other3) should be(true)

                    other3 shouldNot be(original)
                    other3 shouldNot be(same)

                    other3.hashCode() shouldNot be(original.hashCode())
                    other3.hashCode() shouldNot be(same.hashCode())

                    other2 shouldNot be(other3)

                    original.equals("other") should be(false)
                    @Suppress("EqualsNullCall")
                    original.equals(null) should be(false)
                }

                "provide toString support" {
                    val index = createIndex()
                    index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

                    index.size should be(3)
                    index.get(path = "/a/b/c") should be(1)
                    index.get(path = "/a/b/d") should be(2)
                    index.get(path = "/a/b") should be(3)

                    when (type) {
                        "map-mutable" -> index.toString() should be("{/a/b/c=1, /a/b/d=2, /a/b=3}")
                        "map-concurrent" -> index.toString() should be("{/a/b=3, /a/b/c=1, /a/b/d=2}")
                        "map-custom" -> index.toString() should be("{/a/b=3, /a/b/c=1, /a/b/d=2}")
                        "trie-mutable" -> index.toString() should be("{/a/b=3, /a/b/c=1, /a/b/d=2}")
                        "shared" -> index.toString() should be("{/a/b=3, /a/b/c=1, /a/b/d=2}")

                        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
                    }
                }

                "provide sameElements support" {
                    val original = createIndex()
                    original.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

                    original.size should be(3)
                    original.get(path = "/a/b/c") should be(1)
                    original.get(path = "/a/b/d") should be(2)
                    original.get(path = "/a/b") should be(3)

                    original.sameElements(original) should be(true)

                    val same = createIndex()
                    same.putAll(entries = original.toList().toMap()) // force a change of collection (list then map)

                    same.size should be(3)
                    same.get(path = "/a/b/c") should be(1)
                    same.get(path = "/a/b/d") should be(2)
                    same.get(path = "/a/b") should be(3)

                    same.sameElements(same) should be(true)
                    original.sameElements(same) should be(true)

                    val other1 = createIndex()
                    other1.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/e" to 4))

                    other1.size should be(3)
                    other1.get(path = "/a/b/c") should be(1)
                    other1.get(path = "/a/b/d") should be(2)
                    other1.get(path = "/a/e") should be(4)

                    other1.sameElements(other1) should be(true)

                    other1.sameElements(original) should be(false)
                    other1.sameElements(same) should be(false)

                    val other2 = createIndex()
                    other2.putAll(entries = mapOf("/a" to 5))

                    other2.size should be(1)
                    other2.get(path = "/a") should be(5)

                    other2.sameElements(other2) should be(true)

                    other2.sameElements(original) should be(false)
                    other2.sameElements(same) should be(false)

                    val other3 = createIndex()
                    other3.putAll(entries = mapOf("/x" to 5))

                    other3.size should be(1)
                    other3.get(path = "/x") should be(5)

                    other3.sameElements(other3) should be(true)

                    other3.sameElements(original) should be(false)
                    other3.sameElements(same) should be(false)

                    other2 shouldNot be(other3)

                    val other4 = createIndex()
                    other4.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 99))

                    other4.sameElements(original) should be(false)
                    other4.sameElements(same) should be(false)
                    other3.sameElements(other4) should be(false)

                    val indexWithDifferentType: Index<Int> = when (type) {
                        "map-mutable", "map-concurrent", "map-custom" -> TrieIndex.mutable(separator = "/")
                        "trie-mutable" -> SharedIndex.default()
                        "shared" -> MapIndex.mutable()
                        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
                    }

                    indexWithDifferentType.putAll(entries = original.toMap())

                    original.sameElements(indexWithDifferentType) should be(true)
                }
            }
        }

        fun schemeSpec(type: String, separator: String) = wordSpec {
            fun createIndex(): Index<Int> = when (type) {
                "map-mutable" -> MapIndex.mutable()
                "map-concurrent" -> MapIndex.concurrent()
                "map-custom" -> MapIndex.custom(map = HashMap())
                "trie-mutable" -> TrieIndex.mutable(separator = separator)
                "shared" -> SharedIndex.custom(store = SharedIndex.SharedIndexStore(separator = separator))
                else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
            }

            fun createIndex(schemeMapper: SchemeMapper): Index<Int> = when (type) {
                "map-mutable" -> MapIndex.mutable(schemeMapper)
                "map-concurrent" -> MapIndex.concurrent(schemeMapper)
                "map-custom" -> MapIndex.custom(map = HashMap(), schemeMapper = schemeMapper)
                "trie-mutable" -> TrieIndex.mutable(separator = separator, schemeMapper = schemeMapper)
                "shared" -> SharedIndex.custom(
                    store = SharedIndex.SharedIndexStore(separator = separator, schemeMapper = schemeMapper)
                )

                else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
            }

            // builds a schemeless/local path, e.g. "/a/b/c" (or "\a\b\c")
            fun local(vararg segments: String): String =
                segments.joinToString(separator, prefix = separator)

            // builds a scheme-qualified path, e.g. "photos:/a/b/c" (or "photos:\a\b\c")
            fun scheme(name: String, vararg segments: String): String =
                name + ":" + segments.joinToString(separator, prefix = separator)

            "An Index ($type, separator=$separator)" should {
                "preserve schemes and keep them distinct from the bare path" {
                    val index = createIndex()
                    index.put(local("a", "b", "c"), 1)
                    index.put(scheme("fs", "a", "b", "c"), 2)
                    index.put(scheme("photos", "a", "b", "c"), 3)

                    index.size should be(3)
                    index.get(local("a", "b", "c")) should be(1)
                    index.get(scheme("fs", "a", "b", "c")) should be(2)
                    index.get(scheme("photos", "a", "b", "c")) should be(3)
                    index.get(scheme("file", "a", "b", "c")) should be(null)

                    index.keys should be(
                        setOf(
                            local("a", "b", "c"),
                            scheme("fs", "a", "b", "c"),
                            scheme("photos", "a", "b", "c")
                        )
                    )
                }

                "isolate different schemes sharing the same segments" {
                    val index = createIndex()
                    index.put(scheme("photos", "a", "b"), 1)
                    index.put(scheme("music", "a", "b"), 2)
                    index.put(local("a", "b"), 3)

                    index.size should be(3)

                    index.put(scheme("photos", "a", "b"), 10) { _, existing, current -> (existing ?: 0) + current }
                    index.get(scheme("photos", "a", "b")) should be(11)
                    index.get(scheme("music", "a", "b")) should be(2)
                    index.get(local("a", "b")) should be(3)

                    index.remove(scheme("photos", "a", "b"))
                    index.size should be(2)
                    index.get(scheme("photos", "a", "b")) should be(null)
                    index.get(scheme("music", "a", "b")) should be(2)
                    index.get(local("a", "b")) should be(3)
                }

                "support scheme roots and the bare root" {
                    val index = createIndex()
                    index.put(scheme("photos"), 1)
                    index.put(local(), 2)

                    index.size should be(2)
                    index.get(scheme("photos")) should be(1)
                    index.get(local()) should be(2)
                    index.keys should be(setOf(scheme("photos"), local()))
                }

                "not treat a colon inside a segment as a scheme" {
                    val index = createIndex()
                    index.put(local("a", "b:c", "d"), 1)
                    index.put(scheme("photos", "a", "b:c"), 2)

                    index.size should be(2)
                    index.get(local("a", "b:c", "d")) should be(1)
                    index.get(scheme("photos", "a", "b:c")) should be(2)
                    index.keys should be(
                        setOf(
                            local("a", "b:c", "d"),
                            scheme("photos", "a", "b:c")
                        )
                    )
                }

                "treat two-character schemes as schemes but single-character and numeric prefixes as plain paths" {
                    val index = createIndex()

                    index.put(scheme("ab", "x"), 1)
                    index.get(scheme("ab", "x")) should be(1)
                    index.keys should be(setOf(scheme("ab", "x")))

                    val drive = "c:" + separator + "x"
                    val numeric = "12:" + separator + "x"
                    index.put(drive, 2)
                    index.put(numeric, 3)

                    index.size should be(3)
                    index.get(drive) should be(2)
                    index.get(numeric) should be(3)
                    index.contains(drive) should be(true)
                }

                "support deep scheme-qualified paths" {
                    val index = createIndex()
                    val deep = scheme("photos", "a", "b", "c", "d", "e")
                    index.put(deep, 1)
                    index.put(scheme("photos", "a", "b"), 2)

                    index.size should be(2)
                    index.get(deep) should be(1)
                    index.get(scheme("photos", "a", "b")) should be(2)
                    index.contains(deep) should be(true)
                    index.contains(scheme("photos", "a", "b", "c")) should be(false)
                }

                "collapse aliased schemes with a custom mapper" {
                    val index = createIndex(Schemes.aliases("fs", "file"))

                    index.put(scheme("fs", "a", "b", "c"), 1)
                    index.size should be(1)
                    index.get(local("a", "b", "c")) should be(1)
                    index.get(scheme("file", "a", "b", "c")) should be(1)
                    index.get(scheme("FS", "a", "b", "c")) should be(1)

                    index.put(scheme("file", "a", "b", "c"), 5) { _, existing, current -> (existing ?: 0) + current }
                    index.get(local("a", "b", "c")) should be(6)
                    index.size should be(1)

                    index.put(scheme("photos", "a", "b", "c"), 2)
                    index.size should be(2)
                    index.get(scheme("photos", "a", "b", "c")) should be(2)

                    index.keys should be(
                        setOf(
                            local("a", "b", "c"),
                            scheme("photos", "a", "b", "c")
                        )
                    )
                }

                "round-trip scheme-qualified paths through encoding" {
                    val original = createIndex()
                    original.putAll(
                        entries = mapOf(
                            scheme("photos", "a", "b", "c") to 1,
                            scheme("music", "a", "b", "c") to 2,
                            local("a", "b", "c") to 3
                        )
                    )

                    val encoded = original.encode { it }

                    val decoded = when (type) {
                        "map-mutable" -> MapIndex.decodedMutable(encoded) { it }
                        "map-concurrent" -> MapIndex.decodedConcurrent(encoded) { it }
                        "map-custom" -> MapIndex.decodedCustom(encoded, HashMap()) { it }
                        "trie-mutable" -> TrieIndex.decoded(encoded, separator) { it }
                        "shared" -> SharedIndex.decodedCustom(
                            encoded = encoded, store = SharedIndex.SharedIndexStore(separator = separator)
                        ) { it }

                        else -> throw IllegalArgumentException("Unexpected type provided: [$type]")
                    }

                    decoded.get(scheme("photos", "a", "b", "c")) should be(1)
                    decoded.get(scheme("music", "a", "b", "c")) should be(2)
                    decoded.get(local("a", "b", "c")) should be(3)
                    decoded.toMap() should be(original.toMap())
                }

                "round-trip arbitrary path-like strings" {
                    val paths = listOf(
                        // unix, short and long
                        "/", "/a", "/a/b/c", "/a/b/c/d/e/f/g/h/i/j",
                        // windows
                        "C:\\Users\\foo", "C:/Users/foo", "C:foo", "\\\\server\\share\\file", "\\\\?\\C:\\very\\long",
                        // schemes, short and long
                        "photos:", "photos:/", "photos:/a", "photos:/a/b/c", "music:/x/y", "x-y.z+1:/a/b",
                        // windows + scheme
                        "fs:C:\\Users\\foo", "file:\\a\\b\\c", "photos:C:/x/y", "fs:\\\\server\\share\\file",
                        // malformed / repeated / empty separators
                        "", "//", "////", "///a///b///", "photos://///", "photos://a//b", "C://\\/\\", ":/a", "://x", "/a/b/",
                        // special characters
                        "/a b/c d", "/а/电/é", "/📁/x", "/a-b_c.d/e@f/g&h", "/a%20b/c(1)", "photos:/a b/π"
                    )

                    paths.forEach { path ->
                        withClue("path=[$path]") {
                            val index = createIndex()

                            index.put(path, 1)
                            index.get(path) should be(1)
                            index.contains(path) should be(true)
                            index.size should be(1)

                            index.remove(path)
                            index.get(path) should be(null)
                            index.contains(path) should be(false)
                            index.size should be(0)
                        }
                    }
                }
            }
        }
    }

    internal class TestEncoded : Index.Encoded<Int>
}
