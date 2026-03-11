package io.github.sndnv.fsi.backends

import io.github.sndnv.fsi.Index
import io.github.sndnv.fsi.backends.SharedIndex.Companion.custom
import io.github.sndnv.fsi.backends.SharedIndex.Companion.default
import java.nio.file.FileSystems
import java.util.*
import java.util.regex.Pattern

/**
 * An index implementation that allows multiple indices to reuse the same prefix tree (trie).
 *
 * The advantage over a regular [TrieIndex] is that multiple instances can all share the same underlying
 * storage, rather than having individual copies of the file system tree. This can be especially useful
 * when many transient instances are being created by copying or transforming an [Index].
 *
 * **Note:** This implementation is not thread-safe.
 *
 * Example:
 * ```
 *   val index = SharedIndex.default<Int>()
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
 *   val decoded = TrieIndex.decoded(encoded, fs) { it.toInt() } // decoding and converting the values from Long to Int
 *
 *   encoded should be(decoded)
 * ```
 *
 * @see default
 * @see custom
 * @see TrieIndex
 */
class SharedIndex<T> internal constructor(internal val parent: SharedIndexStore) : Index<T> {
    override val size: Int
        get() = parent.size(owner = this)

    override val storage: Index.Storage<T> = SharedIndexStorage()

    override val keys: Set<String>
        get() = parent.keys(owner = this)

    override fun get(path: String): T? =
        parent.get(this, path)

    override fun put(path: String, value: T): SharedIndex<T> = apply {
        parent.compute(owner = this, path) { _, _ -> value }
    }

    override fun put(path: String, value: T, f: (String, T?, T) -> T): SharedIndex<T> = apply {
        parent.compute(owner = this, path) { path, existingValue ->
            f(path, existingValue, value)
        }
    }

    override fun putAll(entries: Map<String, T>): SharedIndex<T> = apply {
        entries.forEach { (path, value) ->
            parent.compute(owner = this, path) { _, _ -> value }
        }
    }

    override fun <S> putAll(entries: Map<String, S>, f: (String, T?, S) -> T): SharedIndex<T> = apply {
        entries.forEach { (path, otherValue) ->
            parent.compute(owner = this, path) { _, existingValue -> f(path, existingValue, otherValue) }
        }
    }

    override fun putAll(paths: Iterable<String>, f: (String, T?) -> T): SharedIndex<T> = apply {
        paths.forEach { path -> parent.compute(owner = this, path, f) }
    }

    override fun remove(path: String): SharedIndex<T> = apply {
        parent.remove(owner = this, path)
    }

    override fun contains(path: String): Boolean =
        parent.contains(owner = this, path)

    override fun clear(): SharedIndex<T> = apply {
        parent.clear(owner = this)
    }

    override fun <S> collect(f: (String, T) -> S?): List<S> =
        parent.collect(owner = this, f)

    override fun filter(f: (String, T) -> Boolean): SharedIndex<T> =
        parent.mapValuesNotNull(owner = this) { path, value ->
            if (f(path, value)) value else null
        }

    override fun search(expr: Pattern): Map<String, T> =
        parent.search(owner = this, expr)

    override fun <S> mapValues(f: (String, T) -> S): SharedIndex<S> =
        parent.mapValuesNotNull(owner = this, f)

    override fun <S> mapValuesNotNull(f: (String, T) -> S?): SharedIndex<S> =
        parent.mapValuesNotNull(owner = this, f)

    override fun forEach(f: (String, T) -> Unit) {
        parent.forEach(owner = this, f)
    }

    override fun replaceAll(f: (String, T) -> T): SharedIndex<T> = apply {
        parent.replaceAll(owner = this, f)
    }

    override fun toMap(): Map<String, T> =
        parent.toMap(owner = this)

    override fun toList(): List<Pair<String, T>> =
        parent.toList(owner = this)

    override fun <E> encode(f: (T) -> E): Index.Encoded<E> =
        parent.encode(owner = this, f)

    override fun sameElements(other: Index<*>): Boolean {
        if (this === other) return true

        return when (other) {
            is SharedIndex<*> -> parent.sameElements(this, other)
            else -> this.toMap() == other.toMap()
        }
    }

    override fun toString(): String {
        return toMap().toString()
    }

    companion object {
        /**
         * Creates a new [SharedIndex] using the default shared store.
         */
        fun <T> default(): SharedIndex<T> =
            custom(store = getDefaultSharedIndexStore())

        /**
         * Creates a new [SharedIndex] using the provided [store].
         *
         * @param store custom shared store
         */
        fun <T> custom(store: SharedIndexStore): SharedIndex<T> =
            SharedIndex(parent = store)

        /**
         * Decodes the provided [encoded] index using the specified function [f] for
         * mapping the encoded values, storing the results in the default shared store.
         *
         * @param encoded the encoded index
         * @param f value decoding function
         *
         * @see default
         */
        @JvmStatic
        fun <T, E> decoded(encoded: Index.Encoded<E>, f: (E) -> T): SharedIndex<T> =
            decodedCustom(encoded = encoded, store = getDefaultSharedIndexStore(), f = f)

        /**
         * Decodes the provided [encoded] index using the specified function [f] for
         * mapping the encoded values, storing the results in the provided shared [store].
         *
         * @param encoded the encoded index
         * @param f value decoding function
         * @param store custom shared store
         *
         * @see custom
         */
        @JvmStatic
        fun <T, E> decodedCustom(encoded: Index.Encoded<E>, store: SharedIndexStore, f: (E) -> T): SharedIndex<T> {
            val result = SharedIndex<T>(parent = store)

            TrieIndex.decoded(encoded, separator, f).forEach { path, node ->
                result.put(path, node)
            }

            return result
        }

        @Volatile
        private var defaultSharedIndexStore: SharedIndexStore? = null

        private val separator: String = FileSystems.getDefault().separator

        /**
         * Creates or provides the default [SharedIndexStore].
         */
        fun getDefaultSharedIndexStore(): SharedIndexStore =
            defaultSharedIndexStore ?: synchronized(this) {
                defaultSharedIndexStore
                    ?: SharedIndexStore(separator = separator)
                        .also { defaultSharedIndexStore = it }
            }
    }

    inner class SharedIndexStorage : Index.Storage<T> {
        override fun estimatedSize(sizeOf: (T) -> Long): Long =
            parent.estimatedStorageSize(owner = this@SharedIndex, sizeOf = sizeOf)
    }

    /**
     * Store for [SharedIndex] instances and their associated values.
     *
     * @see SharedIndex
     */
    class SharedIndexStore internal constructor(internal val storage: TrieIndex<SharedNode>) {
        fun <T> size(owner: SharedIndex<T>): Int {
            var count = 0

            storage.forEachNodeWithCleanup { _, node ->
                if (node.value?.values[owner] != null) {
                    count += 1
                }
            }

            return count
        }

        fun <T> estimatedStorageSize(owner: SharedIndex<T>, sizeOf: (T) -> Long): Long {
            var size = 0L

            storage.forEachNodeWithCleanup { parts, node ->
                val existingValue = node.valueFor<T>(owner)
                if (existingValue != null) {
                    val lastPartSize = parts.lastOrNull()?.toByteArray()?.size ?: 0
                    val valueSize = sizeOf(existingValue)
                    size += (lastPartSize + valueSize)
                }
            }

            return size
        }

        fun <T> keys(owner: SharedIndex<T>): Set<String> {
            val result = mutableSetOf<String>()

            storage.forEachNodeWithCleanup { parts, node ->
                if (node.value?.values[owner] != null) {
                    result += storage.rebuild(parts)
                }
            }

            return result
        }

        fun <T> get(owner: SharedIndex<T>, path: String): T? =
            storage.get(path)?.valueFor(owner)

        fun <T> compute(owner: SharedIndex<T>, path: String, f: (String, T?) -> T) {
            storage.put(path, SharedNode.EmptyPlaceholder) { path, existingNode, _ ->
                val node = (existingNode ?: SharedNode())
                val existingValue = node.valueFor<T>(owner)
                node.values[owner] = f(path, existingValue)

                node
            }
        }

        fun <T> remove(owner: SharedIndex<T>, path: String) {
            storage.get(path)?.values?.remove(owner)
        }

        fun <T> contains(owner: SharedIndex<T>, path: String): Boolean =
            storage.get(path)?.values[owner] != null

        fun <T> clear(owner: SharedIndex<T>) {
            storage.forEachNodeWithCleanup { _, node ->
                node.value?.values?.remove(owner)
            }
        }

        fun <T, S> collect(owner: SharedIndex<T>, f: (String, T) -> S?): List<S> =
            storage.collect { path, node ->
                node.valueFor<T>(owner)?.let { f(path, it) }
            }

        fun <T> search(owner: SharedIndex<T>, expr: Pattern): Map<String, T> =
            storage.search(expr).mapNotNull { (path, node) ->
                node.valueFor<T>(owner)?.let { path to it }
            }.toMap()

        fun <T, S> mapValuesNotNull(owner: SharedIndex<T>, f: (String, T) -> S?): SharedIndex<S> {
            val result = SharedIndex<S>(parent = this)

            storage.forEachNodeWithCleanup { parts, node ->
                val existingValue = node.valueFor<T>(owner)
                if (existingValue != null) {
                    val path = storage.rebuild(parts)
                    val updatedValue = f(path, existingValue)
                    if (updatedValue != null) {
                        result.put(path, updatedValue)
                    }
                }
            }

            return result
        }

        fun <T> forEach(owner: SharedIndex<T>, f: (String, T) -> Unit) {
            storage.forEachNodeWithCleanup { parts, node ->
                val existingValue = node.valueFor<T>(owner)
                if (existingValue != null) {
                    f(storage.rebuild(parts), existingValue)
                }
            }
        }

        fun <T> replaceAll(owner: SharedIndex<T>, f: (String, T) -> T) {
            storage.forEachNodeWithCleanup { parts, node ->
                val existingValue = node.valueFor<T>(owner)
                if (existingValue != null) {
                    node.value?.values[owner] = f(storage.rebuild(parts), existingValue)
                }
            }
        }

        fun <T> toMap(owner: SharedIndex<T>): Map<String, T> {
            val result = mutableMapOf<String, T>()

            storage.forEachNodeWithCleanup { parts, node ->
                val existingValue = node.valueFor<T>(owner)
                if (existingValue != null) {
                    result[storage.rebuild(parts)] = existingValue
                }
            }

            return result
        }

        fun <T> toList(owner: SharedIndex<T>): List<Pair<String, T>> {
            val result = mutableListOf<Pair<String, T>>()

            storage.forEachNodeWithCleanup { parts, node ->
                val existingValue = node.valueFor<T>(owner)
                if (existingValue != null) {
                    result += (storage.rebuild(parts) to existingValue)
                }
            }

            return result
        }

        fun <T, E> encode(owner: SharedIndex<T>, f: (T) -> E): Index.Encoded<E> =
            storage.mapValuesNotNull { _, node -> node.valueFor<T>(owner) }.encode(f)

        fun <T> sameElements(a: SharedIndex<T>, b: SharedIndex<*>): Boolean {
            storage.forEachNodeWithCleanup { _, node ->
                if (node.value?.values[a] != node.value?.values[b])
                    return@sameElements false
            }

            return true
        }

        companion object {
            /**
             * Creates a new [SharedIndexStore] with the provided [separator].
             */
            operator fun invoke(separator: String): SharedIndexStore =
                SharedIndexStore(storage = TrieIndex.mutable(separator))
        }

        /**
         * Iterates over all nodes in the shared storage and removes those with no associated indices.
         */
        internal fun trimNodes() {
            storage.forEachNodeWithCleanup(f = { _, _ -> })
        }

        private inline fun TrieIndex<SharedNode>.forEachNodeWithCleanup(
            f: (List<String>, TrieIndex.IndexNode<SharedNode>) -> Unit
        ) {
            this.forEachNode { parts, node ->
                f(parts, node)

                val value = node.value
                if (value != null && value.values.isEmpty()) {
                    this.removeNode(parts.filter { it.isNotBlank() })
                }
            }
        }

        private fun <T> TrieIndex.IndexNode<SharedNode>.valueFor(owner: SharedIndex<*>): T? =
            this.value?.valueFor(owner)
    }

    internal data class SharedNode(val values: WeakHashMap<SharedIndex<*>, Any>) {
        @Suppress("UNCHECKED_CAST")
        fun <T> valueFor(owner: SharedIndex<*>): T? = values[owner]?.let { it as T }

        companion object {
            operator fun invoke(): SharedNode = SharedNode(values = WeakHashMap())

            val EmptyPlaceholder: SharedNode = SharedNode(values = WeakHashMap())
        }
    }
}
