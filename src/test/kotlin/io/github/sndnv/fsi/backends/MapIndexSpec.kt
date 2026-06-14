package io.github.sndnv.fsi.backends

import io.github.sndnv.fsi.Schemes
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.core.spec.style.WordSpec

class MapIndexSpec : WordSpec({
    "A MapIndex" should {
        "store path-like strings without normalization by default" {
            val index = MapIndex.mutable<Int>()
            index.put("/a/b", 1)
            index.put("a/b", 2)
            index.put("//a//b//", 3)
            index.put("photos:/a/b", 4)
            index.put("fs:/a/b", 5)

            index.size should be(5)
            index.keys should be(setOf("/a/b", "a/b", "//a//b//", "photos:/a/b", "fs:/a/b"))
        }

        "apply a custom scheme mapper to keys" {
            val index = MapIndex.mutable<Int>(schemeMapper = Schemes.aliases("fs", "file"))
            index.put("fs:/a/b", 1)
            index.put("file:/a/b", 2)
            index.put("photos:/a/b", 3)

            index.size should be(2)
            index.get("/a/b") should be(2)
            index.keys should be(setOf("/a/b", "photos:/a/b"))
        }
    }

    "A concurrent MapIndex" should {
        "return thread-safe indices" {
            withClue("filter") {
                val index = MapIndex.concurrent<Int>()
                index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

                val filtered = index.filter { _, _ -> true }

                shouldNotThrow<ConcurrentModificationException> {
                    val iterator = filtered.keys.iterator()
                    iterator.next()
                    filtered.put("/x", 42)
                    if (iterator.hasNext()) iterator.next()
                }
            }

            withClue("mapValues") {
                val index = MapIndex.concurrent<Int>()
                index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

                val mapped = index.mapValues { _, v -> v * 2 }

                shouldNotThrow<ConcurrentModificationException> {
                    val iterator = mapped.keys.iterator()
                    iterator.next()
                    mapped.put("/x", 42)
                    if (iterator.hasNext()) iterator.next()
                }
            }

            withClue("mapValuesNotNull") {
                val index = MapIndex.concurrent<Int>()
                index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

                val mapped = index.mapValuesNotNull { _, v -> v * 2 }

                shouldNotThrow<ConcurrentModificationException> {
                    val iterator = mapped.keys.iterator()
                    iterator.next()
                    mapped.put("/x", 42)
                    if (iterator.hasNext()) iterator.next()
                }
            }
        }
    }
})
