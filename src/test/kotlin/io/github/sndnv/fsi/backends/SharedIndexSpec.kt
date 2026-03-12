package io.github.sndnv.fsi.backends

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import java.util.regex.Pattern

class SharedIndexSpec : WordSpec({
    "A SharedIndex" should {
        "provide a default SharedIndexStore" {
            SharedIndex.removeDefaultSharedIndexStore()
            val a = SharedIndex.getDefaultSharedIndexStore()

            SharedIndex.removeDefaultSharedIndexStore()
            val b = SharedIndex.getDefaultSharedIndexStore()

            a shouldNot be(b)
        }

        "support storage sharing" {
            val parent = SharedIndex.SharedIndexStore(separator = "/")

            val index1 = SharedIndex.custom<Int>(store = parent)
            val index2 = SharedIndex.custom<Int>(store = parent)
            val index3 = SharedIndex.custom<Int>(store = parent)

            parent.storage.size should be(0)
            index1.size should be(0)
            index2.size should be(0)
            index3.size should be(0)

            withClue("index1 -> insert '/a/b/c'") {
                index1.put(path = "/a/b/c", value = 1)

                parent.storage.size should be(1)
                when (val values = parent.storage.get(path = "/a/b/c")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(1)
                        (values[index1] as Int) should be(1)
                    }
                }

                index1.size should be(1)
                index1.get(path = "/a/b/c") should be(1)
                index2.size should be(0)
                index2.get(path = "/a/b/c") should be(null)
                index3.size should be(0)
                index3.get(path = "/a/b/c") should be(null)
            }

            withClue("index2 -> insert '/a/b/c'") {
                index2.put(path = "/a/b/c", value = 2)

                parent.storage.size should be(1)
                when (val values = parent.storage.get(path = "/a/b/c")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(2)
                        (values[index1] as Int) should be(1)
                        (values[index2] as Int) should be(2)
                    }
                }

                index1.size should be(1)
                index1.get(path = "/a/b/c") should be(1)
                index2.size should be(1)
                index2.get(path = "/a/b/c") should be(2)
                index3.size should be(0)
                index3.get(path = "/a/b/c") should be(null)
            }

            withClue("index3 -> insert '/a/b'") {
                index3.put(path = "/a/b", value = 3)

                parent.storage.size should be(2)
                when (val values = parent.storage.get(path = "/a/b")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(1)
                        (values[index3] as Int) should be(3)
                    }
                }

                index1.size should be(1)
                index1.get(path = "/a/b") should be(null)
                index2.size should be(1)
                index2.get(path = "/a/b") should be(null)
                index3.size should be(1)
                index3.get(path = "/a/b") should be(3)
            }

            withClue("index1 -> remove '/a/b/c'") {
                index1.remove(path = "/a/b/c")

                parent.storage.size should be(2)
                when (val values = parent.storage.get(path = "/a/b/c")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(1)
                        (values[index2] as Int) should be(2)
                    }
                }

                index1.collect { path, _ -> path } should be(emptyList())
                index1.search(Pattern.compile("/a/b/c")) should be(emptyMap())
                index2.collect { path, _ -> path } should be(listOf("/a/b/c"))
                index2.search(Pattern.compile("/a/b/c")) should be(mapOf("/a/b/c" to 2))
                index3.collect { path, _ -> path } should be(listOf("/a/b"))
                index3.search(Pattern.compile("/a/b")) should be(mapOf("/a/b" to 3))

                index1.size should be(0)
                index1.get(path = "/a/b/c") should be(null)
                index2.size should be(1)
                index2.get(path = "/a/b/c") should be(2)
                index3.size should be(1)
                index3.get(path = "/a/b/c") should be(null)
                index3.get(path = "/a/b") should be(3)
            }
        }

        "support discarding unused index data" {
            val parent = SharedIndex.SharedIndexStore(separator = "/")

            var index1: SharedIndex<Int>? = SharedIndex.custom(store = parent)
            var index2: SharedIndex<Int>? = SharedIndex.custom(store = parent)
            var index3: SharedIndex<Int>? = SharedIndex.custom(store = parent)

            parent.storage.size should be(0)
            index1!!.size should be(0)
            index2!!.size should be(0)
            index3!!.size should be(0)

            withClue("insert data") {
                index1.put(path = "/a/b/c", value = 1)
                index2.put(path = "/a/b/c", value = 2)
                index3.put(path = "/a/b", value = 3)
                index1.put(path = "/a/b", value = 4)

                System.gc()
                parent.trimNodes()

                parent.storage.size should be(2)

                when (val values = parent.storage.get(path = "/a/b")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(2)
                        (values[index3] as Int) should be(3)
                        (values[index1] as Int) should be(4)
                    }
                }

                when (val values = parent.storage.get(path = "/a/b/c")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(2)
                        (values[index1] as Int) should be(1)
                        (values[index2] as Int) should be(2)
                    }
                }
            }

            withClue("preserve indices with valid references") {
                System.gc()
                parent.trimNodes()

                parent.storage.size should be(2)

                when (val values = parent.storage.get(path = "/a/b")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(2)
                        (values[index3] as Int) should be(3)
                        (values[index1] as Int) should be(4)
                    }
                }

                when (val values = parent.storage.get(path = "/a/b/c")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(2)
                        (values[index1] as Int) should be(1)
                        (values[index2] as Int) should be(2)
                    }
                }
            }

            withClue("discard index1 after its reference is removed") {
                index1 = null

                System.gc()
                parent.trimNodes()

                parent.storage.size should be(2)

                when (val values = parent.storage.get(path = "/a/b")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(1)
                        (values[index3] as Int) should be(3)
                    }
                }

                when (val values = parent.storage.get(path = "/a/b/c")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(1)
                        (values[index2] as Int) should be(2)
                    }
                }
            }

            withClue("discard index2 after its reference is removed") {
                index2 = null

                System.gc()
                parent.trimNodes()

                parent.storage.size should be(1)

                when (val values = parent.storage.get(path = "/a/b")?.values) {
                    null -> fail("Expected a result but none was found")
                    else -> {
                        values.size should be(1)
                        (values[index3] as Int) should be(3)
                    }
                }

                parent.storage.get(path = "/a/b/c") should be(null)
            }

            withClue("discard index3 after its reference is removed") {
                index3 = null

                System.gc()
                parent.trimNodes()

                parent.storage.size should be(0)

                parent.storage.get(path = "/a/b/c") should be(null)
                parent.storage.get(path = "/a/b") should be(null)
            }
        }
    }
})
