package io.github.sndnv.fsi

import java.util.regex.Pattern

/**
 * A function for canonicalizing the scheme component of a path.
 *
 * It receives the raw scheme parsed from a path (or `null` if the path has no scheme) and returns
 * the scheme to actually store the path under (or `null`/blank to store it as a plain, schemeless path).
 *
 * The same mapper is applied by every [Index] implementation, so that indices configured with the same
 * mapper agree on path identity regardless of their backend.
 *
 * @see Schemes
 */
typealias SchemeMapper = (String?) -> String?

/**
 * Helpers for parsing and canonicalizing path schemes.
 *
 * A scheme-qualified path has the form `scheme:rest`, for example `photos:/a/b/c`. The scheme is everything
 * before the first [Delimiter] and must start with a letter and be at least two characters long, so that
 * single-character Windows drive letters (`C:\...`), numeric prefixes (`12:30`) and absolute local paths
 * (`/a/b`) are *not* treated as schemes. Scheme detection is independent of the path separator, so every
 * [Index] backend recognizes schemes identically.
 *
 * Schemes are preserved by default; a custom [SchemeMapper] can be provided to canonicalize them
 * (for example, to treat `fs` and `file` as the local/schemeless filesystem - see [aliases]).
 */
object Schemes {
    /**
     * The character separating a scheme from the rest of a path.
     */
    const val Delimiter: String = ":"

    /**
     * A [SchemeMapper] that preserves every scheme (the default behavior).
     */
    val Identity: SchemeMapper = { it }

    /**
     * A [SchemeMapper] that lower-cases schemes.
     */
    val Lowercase: SchemeMapper = { it?.lowercase() }

    /**
     * A [SchemeMapper] that upper-cases schemes.
     */
    val Uppercase: SchemeMapper = { it?.uppercase() }

    /**
     * Creates a [SchemeMapper] that treats the provided [names] (case-insensitively) as the local/schemeless
     * filesystem, mapping them to `null` so that, for example, `fs:/a/b/c` and `/a/b/c` become the same path.
     *
     * @param names scheme names to treat as local
     */
    @JvmStatic
    fun aliases(vararg names: String): SchemeMapper {
        val local = names.map { it.lowercase() }.toSet()
        return { scheme -> if (scheme != null && scheme.lowercase() in local) null else scheme }
    }

    private val pattern: Pattern = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]+)$Delimiter")

    /**
     * Splits the provided [path] into its raw scheme (or `null` if it has none) and the remainder of the
     * path (everything after the scheme's [Delimiter], or the whole [path] when there is no scheme).
     */
    internal fun split(path: String): Pair<String?, String> {
        val matcher = pattern.matcher(path)

        return if (matcher.lookingAt()) {
            matcher.group(1) to path.substring(matcher.end())
        } else {
            null to path
        }
    }
}
