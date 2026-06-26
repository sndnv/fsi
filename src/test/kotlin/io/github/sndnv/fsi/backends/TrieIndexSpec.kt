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

        "fail if the separator contains the scheme delimiter" {
            listOf(":", "a:", ":/").forEach { separator ->
                withClue("separator=[$separator]") {
                    val e = shouldThrow<IllegalArgumentException> {
                        TrieIndex.mutable<Int>(separator)
                    }

                    e.message should be(
                        "The separator must not contain the scheme delimiter [:] but [$separator] found"
                    )
                }
            }
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
                "fs:C:\\Users\\foo" to "fs:C:\\Users\\foo",
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

        "round-trip Windows drive paths without injecting a leading separator" {
            mapOf(
                "C:\\source" to "C:\\source",
                "C:\\a\\b\\c.dat" to "C:\\a\\b\\c.dat",
                "C:\\Users\\foo" to "C:\\Users\\foo",
                "C:\\a\\\\b\\" to "C:\\a\\b"
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

        "round-trip Windows drive paths expressed with forward slashes" {
            mapOf(
                "C:/source" to "C:/source",
                "C:/a/b/c.dat" to "C:/a/b/c.dat"
            ).forEach { (input, expected) ->
                withClue("input=[$input]") {
                    val index = TrieIndex.mutable<Int>(separator = "/")
                    index.put(input, 1)

                    index.size should be(1)
                    index.get(input) should be(1)
                    index.keys should be(setOf(expected))
                }
            }
        }

        "not treat single-character drive letters as schemes" {
            val index = TrieIndex.mutable<Int>(separator = "\\")
            index.put("C:\\source", 1)
            index.put("photos:\\source", 2)

            index.size should be(2)
            index.keys should be(setOf("C:\\source", "photos:\\source"))
        }

        "not treat non-drive two-character prefixes as drive roots" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("5:/x", 1)
            index.put("ab/c", 2)

            index.size should be(2)
            index.get("5:/x") should be(1)
            index.get("ab/c") should be(2)
            index.keys should be(setOf("5:/x", "ab/c"))
        }

        "collapse a bare Windows drive root" {
            mapOf(
                "C:\\" to "C:",
                "C:" to "C:"
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

        "round-trip UNC paths" {
            mapOf(
                "//server/share" to "//server/share",
                "//server/share/path" to "//server/share/path",
                "//server//share//" to "//server/share"
            ).forEach { (input, expected) ->
                withClue("input=[$input]") {
                    val index = TrieIndex.mutable<Int>(separator = "/")
                    index.put(input, 1)

                    index.size should be(1)
                    index.get(input) should be(1)
                    index.keys should be(setOf(expected))
                }
            }
        }

        "round-trip UNC paths using a backslash separator" {
            val index = TrieIndex.mutable<Int>(separator = "\\")
            index.put("\\\\server\\share\\path", 1)

            index.size should be(1)
            index.get("\\\\server\\share\\path") should be(1)
            index.keys should be(setOf("\\\\server\\share\\path"))
        }

        "keep a UNC path distinct from a drive-letter head" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("C:/x", 1)
            index.put("//C:/x", 2)

            index.size should be(2)
            index.get("C:/x") should be(1)
            index.get("//C:/x") should be(2)
            index.keys should be(setOf("C:/x", "//C:/x"))
        }

        "merge a drive path with its redundantly-rooted form" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("C:/x", 1)
            index.put("/C:/x", 2)

            index.size should be(1)
            index.get("C:/x") should be(2)
            index.get("/C:/x") should be(2)
            index.keys should be(setOf("C:/x"))
        }

        "preserve drive roots under a scheme and reject non-drive heads" {
            mapOf(
                "fs:a:\\x" to "fs:a:\\x",
                "fs:z:\\x" to "fs:z:\\x",
                "fs:A:\\x" to "fs:A:\\x",
                "fs:Z:\\x" to "fs:Z:\\x",
                "fs:5:\\x" to "fs:\\5:\\x",
                "fs:ab\\x" to "fs:\\ab\\x",
                "fs:a:b\\x" to "fs:\\a:b\\x"
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

        "normalize repeated and trailing separators while preserving rootedness" {
            mapOf(
                "/a/b/c" to "/a/b/c",
                "a/b/c" to "a/b/c",
                "/a//b///c/" to "/a/b/c",
                "////" to "/",
                "" to "",
                "/" to "/"
            ).forEach { (input, expected) ->
                withClue("input=[$input]") {
                    val index = TrieIndex.mutable<Int>(separator = "/")
                    index.put(input, 1)
                    index.keys should be(setOf(expected))
                }
            }
        }

        "preserve whitespace-only path segments" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("/a/ /b", 1)
            index.put("/a/b", 2)

            index.size should be(2)
            index.get("/a/ /b") should be(1)
            index.get("/a/b") should be(2)
            index.keys should be(setOf("/a/ /b", "/a/b"))
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

        "treat differently-rooted paths as distinct entries" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("/a/b", 1)
            index.put("a/b", 2)
            index.put("//a/b", 3)

            index.size should be(3)
            index.get("/a/b") should be(1)
            index.get("a/b") should be(2)
            index.get("//a/b") should be(3)
            index.keys should be(setOf("/a/b", "a/b", "//a/b"))
        }

        "collapse redundant interior and trailing separators within an entry" {
            val index = TrieIndex.mutable<Int>(separator = "/")
            index.put("/a/b", 1)
            index.put("/a//b", 2)
            index.put("/a/b//", 3)

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
