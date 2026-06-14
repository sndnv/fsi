package io.github.sndnv.fsi.backends

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
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

        "fail if the separator is the scheme delimiter" {
            val e = shouldThrow<IllegalArgumentException> {
                TrieIndex.mutable<Int>(":")
            }

            e.message should be("The separator must not be the scheme delimiter [:]")
        }

        "distinguish a scheme-qualified path from a local path with a matching colon segment" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("photos:/x", 1)
            index.put("/photos:/x", 2)

            index.size should be(2)
            index.get("photos:/x") should be(1)
            index.get("/photos:/x") should be(2)
            index.keys should be(setOf("photos:/x", "/photos:/x"))
        }

        "preserve Windows-style drive letters when using a backslash separator" {
            val index = TrieIndex.mutable<Int>(separator = "\\")
            index.put("C:\\Users\\foo", 1)

            index.size should be(1)
            index.get("C:\\Users\\foo") should be(1)
        }

        "handle scheme-qualified Windows paths using a backslash separator" {
            mapOf(
                "fs:\\a\\b" to "fs:\\a\\b",
                "photos:\\a\\b\\c" to "photos:\\a\\b\\c",
                "fs:C:\\Users\\foo" to "fs:\\C:\\Users\\foo",
                "fs:\\\\server\\share" to "fs:\\server\\share"
            ).forEach { (input, expected) ->
                withClue("input=[$input]") {
                    val index = TrieIndex.mutable<Int>(separator = "\\")
                    index.put(input, 1)

                    index.size should be(1)
                    index.get(input) should be(1)
                    index.keys should be(setOf(expected))
                }
            }
        }

        "normalize repeated, trailing and missing leading separators" {
            mapOf(
                "/a/b/c" to "/a/b/c",
                "a/b/c" to "/a/b/c",
                "/a//b///c/" to "/a/b/c",
                "////" to "/",
                "" to "/",
                "/" to "/"
            ).forEach { (input, expected) ->
                withClue("input=[$input]") {
                    val index = TrieIndex.mutable<Int>(separator = "/")
                    index.put(input, 1)
                    index.keys should be(setOf(expected))
                }
            }
        }

        "normalize malformed scheme-qualified paths" {
            mapOf(
                "photos:/a/b" to "photos:/a/b",
                "photos:/" to "photos:/",
                "photos:" to "photos:/",
                "photos://///" to "photos:/",
                "photos://a//b/" to "photos:/a/b"
            ).forEach { (input, expected) ->
                withClue("input=[$input]") {
                    val index = TrieIndex.mutable<Int>(separator = "/")
                    index.put(input, 1)
                    index.keys should be(setOf(expected))
                }
            }
        }

        "treat equivalent local paths as the same entry" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("/a/b", 1)
            index.put("a/b", 2)
            index.put("//a//b//", 3)

            index.size should be(1)
            index.get("/a/b") should be(3)
            index.keys should be(setOf("/a/b"))
        }

        "(internal) support replacing the root node" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.putAll(entries = mapOf("/a/b/c" to 1, "/a/b/d" to 2, "/a/b" to 3))

            index.size should be(3)

            index.withRoot(other = TrieIndex.IndexNode(children = mutableMapOf(), value = 1))

            index.size should be(1)

            index.keys should be(setOf("/"))
        }
    }
})
