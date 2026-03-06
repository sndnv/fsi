package io.github.sndnv.fsi

import java.util.regex.Pattern

/**
 * An object for storing and retrieving arbitrary values related to file-system paths.
 *
 * @see io.github.sndnv.fsi.backends.MapIndex
 * @see io.github.sndnv.fsi.backends.TrieIndex
 */
interface Index<T> {
    /**
     * Returns the number of values in this index.
     */
    val size: Int

    /**
     * Returns the current [Storage] handler.
     */
    val storage: Storage<T>

    /**
     * Returns a set of all paths in this index.
     */
    val keys: Set<String>

    /**
     * Returns the value associated with the provided [path], or `null` if no value is found
     *
     * @param path path to retrieve
     */
    fun get(path: String): T?

    /**
     * Inserts the provided [value] with the associated [path].
     *
     * @param path path to insert
     * @param value value to insert for the provided path
     */
    fun put(path: String, value: T): Index<T>

    /**
     * Inserts the provided [value] with the associated [path] by using the function [f] to
     * determine the final value to be inserted.
     *
     * **Example:**
     * ```
     * val index: Index<Int> = ...;
     *
     * index.put(path = "/a/b/c", value = 1) { path, existingValue, providedValue ->
     *   if (existingValue != null) {
     *     existingValue + providedValue
     *   } else {
     *     providedValue
     *   }
     * }
     * ```
     *
     * @param path path to insert
     * @param value value to insert for the provided path
     * @param f value accumulation function
     */
    fun put(path: String, value: T, f: (String, T?, T) -> T): Index<T>

    /**
     * Inserts all provided [entries].
     *
     * **Note:** If an entry is already in this index, its value will be replaced with the newly provided one.
     *
     * @param entries entries to insert
     */
    fun putAll(entries: Map<String, T>): Index<T>

    /**
     * Inserts all provided [entries] using the result of specified function [f].
     *
     * **Example:**
     * ```
     * val index: Index<Int> = ...;
     * val entries: Map<String, Int> = ...;
     *
     * index.putAll(entries) { entryKey, existingValue, entryValue ->
     *   if (existingValue != null) {
     *     existingValue + entryValue
     *   } else {
     *     entryValue
     *   }
     * }
     * ```
     *
     * @param entries entries to insert
     * @param f value accumulation function
     */
    fun <S> putAll(entries: Map<String, S>, f: (String, T?, S) -> T): Index<T>

    /**
     * Inserts all provided [paths] using the result of specified function [f].
     *
     * **Example:**
     * ```
     * val index: Index<Int> = ...;
     * val paths: List<String> = ...;
     *
     * index.putAll(paths) { entryKey, existingValue ->
     *   if (existingValue != null) {
     *     existingValue + entryValue
     *   } else {
     *     entryValue
     *   }
     * }
     * ```
     *
     * @param paths paths to insert
     * @param f value accumulation function
     */
    fun putAll(paths: Iterable<String>, f: (String, T?) -> T): Index<T>

    /**
     * Removes the value associated with the provided [path] from this index, if a value exists.
     *
     * @param path path to remove
     */
    fun remove(path: String): Index<T>

    /**
     * Checks if the provided [path] has a value in this index.
     */
    fun contains(path: String): Boolean

    /**
     * Removes all elements from this index.
     */
    fun clear(): Index<T>

    /**
     * Creates a new list by applying the specified function [f] to all elements of
     * this index and keeping only the non-null values provided by the function.
     *
     * @param f collection function
     */
    fun <S> collect(f: (String, T) -> S?): List<S>

    /**
     * Returns a new index containing only entries matched by the provided function [f].
     *
     * **Example:**
     * ```
     * val index: Index<Int> = ...;
     *
     * index.filter { path, existingValue ->
     *   existingValue > 9000
     * }
     * ```
     *
     * @param f predicate
     */
    fun filter(f: (String, T) -> Boolean): Index<T>

    /**
     * Returns a map containing only entries with keys that match the provided regular expression [expr].
     *
     * **Example:**
     * ```
     * val index: Index<Int> = ...;
     *
     * val result: Map<String, Int> = index.search(expr = Pattern.compile("^/a/b/.*"))
     * ```
     *
     * @param expr regular expression
     */
    fun search(expr: Pattern): Map<String, T>

    /**
     * Returns a new index with entries having the keys of this index and the values being the results applying
     * the provided function [f] to each entry in this index.
     *
     * **Example:**
     * ```
     * val original: Index<Int> = ...;
     * val mapped: Index<String> = original.mapValues { path, existingValue ->
     *   existingValue.toString()
     * }
     * ```
     *
     * @param f mapping function
     */
    fun <S> mapValues(f: (String, T) -> S): Index<S>

    /**
     * Returns a new index containing only non-null results of applying the provided function [f] to each entry in this index.
     *
     * **Example:**
     * ```
     * val original: Index<Int> = ...;
     * val mapped: Index<String> = original.mapValuesNotNull { path, existingValue ->
     *   if (path.startsWith("/a/b")) {
     *     existingValue.toString()
     *   } else {
     *     null
     *   }
     * }
     * ```
     *
     * @param f mapping function
     */
    fun <S> mapValuesNotNull(f: (String, T) -> S?): Index<S>

    /**
     * Calls the provided function [f] for each key/value pair in this index.
     *
     * **Example:**
     * ```
     * val index: Index<Int> = ...;
     * var count: Int = 0;
     *
     * index.forEach { path, existingValue ->
     *   if (existingValue > 9000) {
     *     count += 1
     *   }
     * }
     * ```
     *
     * @param f action to perform for each entry
     */
    fun forEach(f: (String, T) -> Unit)

    /**
     * Replaces the value of each entry with the result of applying the specified function [f] to that entry.
     *
     * @param f function to apply to each entry
     */
    fun replaceAll(f: (String, T) -> T): Index<T>

    /**
     * Returns a map containing all key/value pairs from this index.
     */
    fun toMap(): Map<String, T>

    /**
     * Returns a list containing all key/value pairs from this index.
     */
    fun toList(): List<Pair<String, T>>

    /**
     * Encodes this index in a format that can be further serialized.
     *
     * **Note:** Each [Index] implementation provides its own [Encoded] format.
     *
     * @param f function for mapping index values to encodable values
     */
    fun <E> encode(f: (T) -> E): Encoded<E>

    /**
     * Interface providing storage information for the current index.
     *
     * @see estimatedSize
     */
    interface Storage<T> {
        /**
         * Returns the current storage size estimation for all keys and values, in bytes, using
         * the provided [sizeOf] function to calculate the size of each value in the index.
         *
         * **Note:** This operation may be very expensive, as it needs to iterate over
         * all entries in the index.
         *
         * @param sizeOf value size calculation function
         */
        fun estimatedSize(sizeOf: (T) -> Long): Long
    }

    /**
     * Interface representing the current index as an encoded/serializable object.
     */
    interface Encoded<T>
}
