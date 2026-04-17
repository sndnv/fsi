package io.github.sndnv.fsi.backends

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec

class MapIndexSpec : WordSpec({
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
