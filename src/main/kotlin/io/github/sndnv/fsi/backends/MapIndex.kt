package io.github.sndnv.fsi.backends

import io.github.sndnv.fsi.Index
import io.github.sndnv.fsi.SchemeMapper
import io.github.sndnv.fsi.Schemes
import io.github.sndnv.fsi.backends.MapIndex.Companion.concurrent
import io.github.sndnv.fsi.backends.MapIndex.Companion.custom
import io.github.sndnv.fsi.backends.MapIndex.Companion.mutable
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * An [Index] implementation that uses a [MutableMap] for its underlying storage.
 *
 * Example:
 * ```
 *   val index = MapIndex.concurrent<Int>()
 *   val path = "/a/b/c"
 *
 *   // basic operations
 *   index.size should be(0)
 *   index.get(path) should be(null)
 *
 *   index.put(path, 42) // adds a new entry
 *   index.size should be(1)
 *   index.get(path) should be(42)
 *
 *   index.remove(path) // removes an existing entry
 *   index.size should be(0)
 *   index.get(path) should be(null)
 *
 *   // encoding and decoding
 *   val encoded = index.encode { it.toLong() } // encoding and converting the values from Int to Long (for example)
 *   val decoded = MapIndex.decodedConcurrent(encoded) { it.toInt() } // decoding and converting the values from Long to Int
 *
 *   encoded should be(decoded)
 * ```
 *
 * **Note:** Keys are stored unchanged - unlike [TrieIndex], a [MapIndex] has no separator and does not
 * normalize paths, so `a/b/c` and `/a/b/c` are distinct keys.
 *
 * @see mutable
 * @see concurrent
 * @see custom
 * @see TrieIndex
 */
class MapIndex<T> private constructor(
    private val underlying: MutableMap<String, T>,
    private val schemeMapper: SchemeMapper
) : Index<T> {
    override val size: Int
        get() = underlying.size

    override val storage: Index.Storage<T> = MapIndexStorage()

    override val keys: Set<String>
        get() = underlying.keys

    override fun get(path: String): T? {
        return underlying[normalize(path)]
    }

    override fun put(path: String, value: T): MapIndex<T> = apply {
        underlying[normalize(path)] = value
    }

    override fun put(path: String, value: T, f: (String, T?, T) -> T): MapIndex<T> = apply {
        underlying.compute(normalize(path)) { _, existing -> f(path, existing, value) }
    }

    override fun putAll(entries: Map<String, T>): MapIndex<T> =
        putAll(entries = entries, f = { _, _, value -> value })

    override fun <S> putAll(
        entries: Map<String, S>, f: (String, T?, S) -> T
    ): MapIndex<T> = apply {
        entries.forEach { (path, otherValue) ->
            underlying.compute(normalize(path)) { _, existingValue -> f(path, existingValue, otherValue) }
        }
    }

    override fun putAll(paths: Iterable<String>, f: (String, T?) -> T): MapIndex<T> = apply {
        paths.forEach { path -> underlying.compute(normalize(path)) { _, existing -> f(path, existing) } }
    }

    override fun remove(path: String): MapIndex<T> = apply {
        underlying.remove(normalize(path))
    }

    override fun contains(path: String): Boolean {
        return underlying.contains(normalize(path))
    }

    override fun clear(): MapIndex<T> = apply {
        underlying.clear()
    }

    override fun <S> collect(f: (String, T) -> S?): List<S> {
        return underlying.mapNotNull { (key, value) ->
            f(key, value)
        }
    }

    override fun filter(f: (String, T) -> Boolean): MapIndex<T> {
        return MapIndex(underlying.filter { f(it.key, it.value) }.toMap(emptyDestination()), schemeMapper)
    }

    override fun search(expr: Pattern): Map<String, T> {
        return underlying.filter { expr.matcher(it.key).matches() }
    }

    override fun <S> mapValues(f: (String, T) -> S): MapIndex<S> =
        mapValuesNotNull(f = f)

    override fun <S> mapValuesNotNull(f: (String, T) -> S?): MapIndex<S> {
        return MapIndex(underlying = underlying.mapNotNull { (key, value) -> f(key, value)?.let { key to it } }
            .toMap(emptyDestination()), schemeMapper = schemeMapper)
    }

    override fun forEach(f: (String, T) -> Unit) {
        underlying.forEach(f)
    }

    override fun replaceAll(f: (String, T) -> T): MapIndex<T> = apply {
        underlying.replaceAll { key, value -> f(key, value) }
    }

    override fun toMap(): Map<String, T> {
        return underlying.toMap()
    }

    override fun toList(): List<Pair<String, T>> {
        return underlying.toList()
    }

    /**
     * Encodes this index in a format that can be further serialized.
     *
     * @param f function for mapping index values to encodable values
     *
     * @see Encoded
     */
    override fun <E> encode(f: (T) -> E): Encoded<E> {
        return Encoded(entries = underlying.mapValues { f(it.value) })
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MapIndex<*>

        return sameElements(other)
    }

    override fun hashCode(): Int {
        return underlying.hashCode()
    }

    override fun toString(): String {
        return underlying.toString()
    }

    override fun sameElements(other: Index<*>): Boolean {
        if (this === other) return true

        return when (other) {
            is MapIndex<*> -> underlying == other.underlying
            else -> underlying == other.toMap()
        }
    }

    private fun <V> emptyDestination(): MutableMap<String, V> = when (underlying) {
        is ConcurrentHashMap -> ConcurrentHashMap()
        else -> LinkedHashMap()
    }

    private fun normalize(path: String): String {
        if (schemeMapper === Schemes.Identity) return path
        val (rawScheme, rest) = Schemes.split(path)
        val scheme = schemeMapper(rawScheme)
        return if (scheme.isNullOrEmpty()) rest else "$scheme${Schemes.Delimiter}$rest"
    }

    inner class MapIndexStorage : Index.Storage<T> {
        override fun estimatedSize(sizeOf: (T) -> Long): Long =
            underlying.map { it.key.toByteArray().size + sizeOf(it.value) }.sum()
    }

    /**
     * An encoded [MapIndex], with [entries] providing the content of the index.
     *
     * The values of the [entries] are determined during encoding.
     *
     * @param entries encoded index entries
     */
    data class Encoded<E>(val entries: Map<String, E>) : Index.Encoded<E>

    companion object {
        /**
         * Creates a new [MapIndex] using a [ConcurrentHashMap] as the underlying storage, preserving path schemes.
         */
        @JvmStatic
        fun <T> concurrent(): MapIndex<T> = concurrent(schemeMapper = Schemes.Identity)

        /**
         * Creates a new [MapIndex] using a [ConcurrentHashMap] as the underlying storage and the provided
         * [schemeMapper] (see [Schemes]).
         */
        @JvmStatic
        fun <T> concurrent(schemeMapper: SchemeMapper): MapIndex<T> = MapIndex(
            underlying = ConcurrentHashMap(), schemeMapper = schemeMapper
        )

        /**
         * Creates a new [MapIndex] using a [LinkedHashMap] as the underlying storage, preserving path schemes.
         *
         * **Note:** This implementation is not thread-safe.
         */
        @JvmStatic
        fun <T> mutable(): MapIndex<T> = mutable(schemeMapper = Schemes.Identity)

        /**
         * Creates a new [MapIndex] using a [LinkedHashMap] as the underlying storage and the provided
         * [schemeMapper] (see [Schemes]).
         *
         * **Note:** This implementation is not thread-safe.
         */
        @JvmStatic
        fun <T> mutable(schemeMapper: SchemeMapper): MapIndex<T> = MapIndex(
            underlying = LinkedHashMap(), schemeMapper = schemeMapper
        )

        /**
         * Creates a new [MapIndex] using the provided underlying storage, preserving path schemes.
         *
         * @param map underlying storage
         */
        @JvmStatic
        fun <T> custom(map: MutableMap<String, T>): MapIndex<T> = custom(map = map, schemeMapper = Schemes.Identity)

        /**
         * Creates a new [MapIndex] using the provided underlying storage and [schemeMapper] (see [Schemes]).
         *
         * @param map underlying storage
         * @param schemeMapper function for canonicalizing path schemes
         */
        @JvmStatic
        fun <T> custom(map: MutableMap<String, T>, schemeMapper: SchemeMapper): MapIndex<T> = MapIndex(
            underlying = map, schemeMapper = schemeMapper
        )

        /**
         * Decodes the provided [encoded] index using the specified function [f] for mapping the encoded values,
         * preserving path schemes.
         *
         * @param encoded the encoded index
         * @param f value decoding function
         *
         * @see concurrent
         */
        @JvmStatic
        fun <T, E> decodedConcurrent(encoded: Index.Encoded<E>, f: (E) -> T): MapIndex<T> =
            decodedConcurrent(encoded = encoded, schemeMapper = Schemes.Identity, f = f)

        /**
         * Decodes the provided [encoded] index using the specified function [f] for mapping the encoded values
         * and the provided [schemeMapper] (see [Schemes]).
         *
         * @param encoded the encoded index
         * @param schemeMapper function for canonicalizing path schemes
         * @param f value decoding function
         *
         * @see concurrent
         */
        @JvmStatic
        fun <T, E> decodedConcurrent(encoded: Index.Encoded<E>, schemeMapper: SchemeMapper, f: (E) -> T): MapIndex<T> =
            requireEncoded(encoded) { actual ->
                MapIndex(actual.entries.mapValues { f(it.value) }.toMap(ConcurrentHashMap()), schemeMapper)
            }

        /**
         * Decodes the provided [encoded] index using the specified function [f] for mapping the encoded values,
         * preserving path schemes.
         *
         * @param encoded the encoded index
         * @param f value decoding function
         *
         * @see mutable
         */
        @JvmStatic
        fun <T, E> decodedMutable(encoded: Index.Encoded<E>, f: (E) -> T): MapIndex<T> =
            decodedMutable(encoded = encoded, schemeMapper = Schemes.Identity, f = f)

        /**
         * Decodes the provided [encoded] index using the specified function [f] for mapping the encoded values
         * and the provided [schemeMapper] (see [Schemes]).
         *
         * @param encoded the encoded index
         * @param schemeMapper function for canonicalizing path schemes
         * @param f value decoding function
         *
         * @see mutable
         */
        @JvmStatic
        fun <T, E> decodedMutable(encoded: Index.Encoded<E>, schemeMapper: SchemeMapper, f: (E) -> T): MapIndex<T> =
            requireEncoded(encoded) { actual ->
                MapIndex(actual.entries.mapValues { f(it.value) }.toMutableMap(), schemeMapper)
            }

        /**
         * Decodes the provided [encoded] index using the specified function [f] for mapping the encoded values,
         * preserving path schemes.
         *
         * @param encoded the encoded index
         * @param destination underlying storage
         * @param f value decoding function
         *
         * @see custom
         */
        @JvmStatic
        fun <T, E> decodedCustom(encoded: Index.Encoded<E>, destination: MutableMap<String, T>, f: (E) -> T): MapIndex<T> =
            decodedCustom(encoded = encoded, destination = destination, schemeMapper = Schemes.Identity, f = f)

        /**
         * Decodes the provided [encoded] index using the specified function [f] for mapping the encoded values
         * and the provided [schemeMapper] (see [Schemes]).
         *
         * @param encoded the encoded index
         * @param destination underlying storage
         * @param schemeMapper function for canonicalizing path schemes
         * @param f value decoding function
         *
         * @see custom
         */
        @JvmStatic
        fun <T, E> decodedCustom(
            encoded: Index.Encoded<E>, destination: MutableMap<String, T>, schemeMapper: SchemeMapper, f: (E) -> T
        ): MapIndex<T> =
            requireEncoded(encoded) { actual ->
                MapIndex(actual.entries.mapValues { f(it.value) }.toMap(destination), schemeMapper)
            }

        private fun <E, T> requireEncoded(encoded: Index.Encoded<E>, f: (Encoded<E>) -> T): T =
            f(
                encoded as? Encoded ?: throw IllegalArgumentException(
                    "Expected [${Encoded::class.java.canonicalName}] but [${encoded.javaClass.canonicalName}] provided"
                )
            )
    }
}
