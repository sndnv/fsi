package io.github.sndnv.fsi.backends

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.be
import io.kotest.matchers.should

class TrieIndexSpec : WordSpec({
    "A TrieIndex" should {
        "provide its separator" {
            val separator = "/"
            val index = TrieIndex.mutable<Int>(separator)

            index.separator should be(separator)
        }

        "fail if a blank separator is provided" {
            val separator = "  "
            val e = shouldThrow<IllegalArgumentException> {
                TrieIndex.mutable<Int>(separator)
            }

            e.message should be("A non-empty separator must be provided but [$separator] found")
        }

        "(internal) support replacing the root node" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

            index.size should be(3)

            index.withRoot(other = TrieIndex.IndexNode(children = mutableMapOf(), value = 1))

            index.size should be(1)
        }
    }
})
