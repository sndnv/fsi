package io.github.sndnv.fsi

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.be
import io.kotest.matchers.should

class SchemesSpec : WordSpec({
    "Schemes" should {
        "provide the scheme delimiter" {
            Schemes.Delimiter should be(":")
        }

        "provide an identity scheme mapper" {
            Schemes.Identity(null) should be(null)
            Schemes.Identity("fs") should be("fs")
            Schemes.Identity("photos") should be("photos")
            Schemes.Identity("FS") should be("FS")
        }

        "provide a lower-casing scheme mapper" {
            Schemes.Lowercase(null) should be(null)
            Schemes.Lowercase("FS") should be("fs")
            Schemes.Lowercase("Photos") should be("photos")
            Schemes.Lowercase("music") should be("music")
        }

        "provide an upper-casing scheme mapper" {
            Schemes.Uppercase(null) should be(null)
            Schemes.Uppercase("FS") should be("FS")
            Schemes.Uppercase("Photos") should be("PHOTOS")
            Schemes.Uppercase("music") should be("MUSIC")
        }

        "provide an aliasing scheme mapper" {
            val mapper = Schemes.aliases("fs", "file")

            mapper("fs") should be(null)
            mapper("file") should be(null)
            mapper("FS") should be(null)
            mapper("File") should be(null)

            mapper("photos") should be("photos")
            mapper("Photos") should be("Photos")
            mapper(null) should be(null)
        }

        "treat alias names case-insensitively" {
            val mapper = Schemes.aliases("FS", "FILE")

            mapper("fs") should be(null)
            mapper("file") should be(null)
            mapper("photos") should be("photos")
        }

        "provide an aliasing mapper with no names that preserves every scheme" {
            val mapper = Schemes.aliases()

            mapper(null) should be(null)
            mapper("fs") should be("fs")
            mapper("photos") should be("photos")
        }

        "split paths into their scheme and remainder" {
            listOf(
                // no scheme
                Triple("/a/b/c", null, "/a/b/c"),
                Triple("", null, ""),
                Triple("/", null, "/"),
                Triple("a", null, "a"),
                // scheme present (detection is separator-independent)
                Triple("photos:/a/b/c", "photos", "/a/b/c"),
                Triple("photos:/", "photos", "/"),
                Triple("photos:", "photos", ""),
                Triple("ab:x", "ab", "x"),
                Triple("mailto:foo", "mailto", "foo"),
                Triple("ab:c:d", "ab", "c:d"),
                // valid scheme characters and preserved case
                Triple("x-y.z+1:/a", "x-y.z+1", "/a"),
                Triple("a1+.-x:/p", "a1+.-x", "/p"),
                Triple("FS:/a", "FS", "/a"),
                // not a scheme
                Triple("c:/x", null, "c:/x"),
                Triple("a:", null, "a:"),
                Triple("12:30", null, "12:30"),
                Triple("/photos:/x", null, "/photos:/x"),
                Triple(":", null, ":"),
                Triple(":/x", null, ":/x"),
                Triple("_x:/p", null, "_x:/p"),
                Triple(" fs:/a", null, " fs:/a")
            ).forEach { (input, expectedScheme, expectedRest) ->
                withClue("input=[$input]") {
                    Schemes.extract(input) should be(expectedScheme to expectedRest)
                }
            }
        }
    }
})
