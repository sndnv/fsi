package io.github.sndnv.fsi.backends

import io.github.sndnv.fsi.Index
import io.github.sndnv.fsi.backends.TrieIndex.Companion.mutable
import java.util.*
import java.util.regex.Pattern

/**
 * An [Index] implementation that uses a prefix tree (trie) for its underlying storage.
 *
 * **Note:** This implementation is not thread-safe.
 *
 * Example:
 * ```
 *   import java.nio.file.FileSystems
 *
 *   val fs = FileSystems.getDefault()
 *   val index = TrieIndex.mutable<Int>(fs.separator)
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
 * @see mutable
 * @see MapIndex
 */
class TrieIndex<T> private constructor(val separator: String) : Index<T> {
    init {
        require(separator.isNotBlank()) {
            "A non-empty separator must be provided but [$separator] found"
        }
    }

    private var root: IndexNode<T> = IndexNode(children = mutableMapOf(), value = null)
    private var actualSize: Int = 0

    override val size: Int
        get() = actualSize

    override val storage: Index.Storage<T> = TrieIndexStorage()

    override val keys: Set<String>
        get() {
            val result = mutableSetOf<String>()
            forEach { path, _ -> result.add(path) }
            return result
        }

    override fun get(path: String): T? = getNode(path = path.parts())?.value

    override fun put(path: String, value: T): TrieIndex<T> =
        put(path = path, value = value) { _, _, v -> v }

    override fun put(path: String, value: T, f: (String, T?, T) -> T): TrieIndex<T> = apply {
        val node = getOrCreateNode(path.parts())

        if (node.value == null) {
            actualSize += 1
        }

        node.value = f(path, node.value, value)
    }

    override fun putAll(entries: Map<String, T>): TrieIndex<T> =
        putAll(entries = entries, f = { _, _, value -> value })

    override fun <S> putAll(entries: Map<String, S>, f: (String, T?, S) -> T): TrieIndex<T> = apply {
        entries.forEach { (path, value) ->
            val node = getOrCreateNode(path.parts())

            if (node.value == null) {
                actualSize += 1
            }

            node.value = f(path, node.value, value)
        }
    }

    override fun putAll(paths: Iterable<String>, f: (String, T?) -> T): TrieIndex<T> = apply {
        paths.forEach { path ->
            val node = getOrCreateNode(path.parts())

            if (node.value == null) {
                actualSize += 1
            }

            node.value = f(path, node.value)
        }
    }

    override fun remove(path: String): TrieIndex<T> = apply {
        removeNode(path.parts())
    }

    override fun contains(path: String): Boolean {
        return getNode(path = path.parts())?.value != null
    }

    override fun clear(): TrieIndex<T> = apply {
        root.children.clear()
        root.value = null
        actualSize = 0
    }

    override fun <S> collect(f: (String, T) -> S?): List<S> {
        val result = mutableListOf<S>()

        forEachNode { path, node ->
            val value = node.value
            if (value != null) {
                val actualPath = rebuild(path)
                val collected = f(actualPath, value)
                if (collected != null) {
                    result += collected
                }
            }
        }

        return result
    }

    override fun filter(f: (String, T) -> Boolean): TrieIndex<T> = mapValuesNotNull { path, value ->
        if (f(path, value)) value else null
    }

    override fun search(expr: Pattern): Map<String, T> {
        val result = mutableMapOf<String, T>()

        forEachNode { path, node ->
            val value = node.value
            if (value != null) {
                val actualPath = rebuild(path)
                if (expr.matcher(actualPath).matches()) {
                    result[actualPath] = value
                }
            }
        }

        return result
    }

    override fun <S> mapValues(f: (String, T) -> S): TrieIndex<S> =
        mapValuesNotNull(f = f)

    override fun <S> mapValuesNotNull(f: (String, T) -> S?): TrieIndex<S> {
        val result = TrieIndex<S>(separator)

        forEachNode { path, node ->
            val existingValue = node.value
            if (existingValue != null) {
                val newValue = f(rebuild(path), existingValue)
                if (newValue != null) {
                    val node = result.getOrCreateNode(path.drop(1))
                    if (node.value == null) {
                        result.actualSize += 1
                    }
                    node.value = newValue
                }
            }
        }

        return result
    }

    override fun forEach(f: (String, T) -> Unit) {
        forEachNode { path, node ->
            val value = node.value
            if (value != null) {
                f(rebuild(path), value)
            }
        }
    }

    override fun replaceAll(f: (String, T) -> T): TrieIndex<T> = apply {
        forEachNode { path, node ->
            val value = node.value
            if (value != null) {
                node.value = f(rebuild(path), value)
            }
        }
    }

    override fun toMap(): Map<String, T> {
        val result = mutableMapOf<String, T>()

        forEach { path, value -> result[path] = value }

        return result
    }

    override fun toList(): List<Pair<String, T>> {
        val result = mutableListOf<Pair<String, T>>()

        forEach { path, value -> result.addLast(path to value) }

        return result
    }

    /**
     * Encodes this index in a format that can be further serialized.
     *
     * The returned value represents the root (node) of the tree.
     *
     * @param f function for mapping index values to encodable values
     *
     * @see EncodedNode
     */
    override fun <E> encode(f: (T) -> E): EncodedNode<E> =
        transform(source = root, convertChildMap = { it }, createTarget = { children, value ->
            EncodedNode(children, value?.let { f(it) })
        })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrieIndex<*>

        if (actualSize != other.actualSize) return false



        return sameElements(other)
    }

    override fun hashCode(): Int {
        var result = actualSize

        forEachNode { path, node ->
            result = 31 * result + path.hashCode()
            result = 31 * result + node.value.hashCode()
        }

        return result
    }

    override fun toString(): String {
        return toMap().toString()
    }

    @Suppress("ReturnCount")
    override fun sameElements(other: Index<*>): Boolean {
        if (this === other) return true

        when (other) {
            is TrieIndex<*> -> {
                val remaining: Queue<Pair<List<String>, IndexNode<T>>> = ArrayDeque()

                remaining.add(listOf("") to root)

                while (remaining.isNotEmpty()) {
                    val (currentPath, currentNode) = remaining.poll()
                    val otherNode = other.getNode(currentPath.drop(1))

                    // since the size of both instances is the same, the expectation is that, if the indices
                    // are the same, each node should exist in both and their values should be identical
                    if (otherNode == null || currentNode.value != otherNode.value) {
                        return false
                    }

                    currentNode.children.forEach { (childPart, childNode) ->
                        remaining.add((currentPath + childPart) to childNode)
                    }
                }

                return true
            }

            else -> return this.toMap() == other.toMap()
        }
    }

    companion object {
        /**
         * Creates a new mutable [TrieIndex] using the provided [separator].
         *
         * **Note:** This implementation is not thread-safe.
         *
         * @param separator file system path separator
         */
        @JvmStatic
        fun <T> mutable(separator: String): TrieIndex<T> = TrieIndex(separator = separator)

        /**
         * Decodes the provided [encoded] index using the specified function [f] for mapping the encoded values.
         *
         * @param encoded the encoded index
         * @param separator file system path separator
         * @param f value decoding function
         */
        @JvmStatic
        fun <E, T> decoded(encoded: Index.Encoded<E>, separator: String, f: (E) -> T): TrieIndex<T> =
            requireEncoded(encoded) { actual ->
                TrieIndex<T>(separator = separator).withRoot(
                    other = transform(
                        source = actual,
                        convertChildMap = { it.toMutableMap() },
                        createTarget = { children, value -> IndexNode(children = children, value = value?.let { f(it) }) })
                )
            }

        private fun <
                S,
                T,
                Source : TransformableNode<S, SourceChildren>,
                Target : TransformableNode<T, TargetChildren>,
                SourceChildren : Map<String, Source>,
                TargetChildren : Map<String, Target>
                > transform(
            source: Source, convertChildMap: (Map<String, Target>) -> TargetChildren, createTarget: (TargetChildren, S?) -> Target
        ): Target {
            val collected = ArrayDeque<Source>()
            val remaining = ArrayDeque<Source>()

            remaining.push(source)

            while (remaining.isNotEmpty()) {
                val currentNode = remaining.pop()

                collected.push(currentNode)

                currentNode.children.forEach { (_, childNode) ->
                    remaining.push(childNode)
                }
            }

            val encoded = mutableMapOf<Source, Target>()

            while (collected.isNotEmpty()) {
                val collectedNode = collected.pop()

                val encodedChildren = collectedNode.children.mapValues { (_, childNode) ->
                    encoded[childNode]!!
                }

                encoded[collectedNode] = createTarget(convertChildMap(encodedChildren), collectedNode.value)
            }

            return encoded[source]!!
        }

        private fun <E, T> requireEncoded(encoded: Index.Encoded<E>, f: (EncodedNode<E>) -> T): T = f(
            encoded as? EncodedNode ?: throw IllegalArgumentException(
                "Expected [${EncodedNode::class.java.canonicalName}] but [${encoded.javaClass.canonicalName}] provided"
            )
        )
    }

    inner class TrieIndexStorage : Index.Storage<T> {
        override fun estimatedSize(sizeOf: (T) -> Long): Long {
            var estimate = 0L
            val remaining: Queue<IndexNode<T>> = ArrayDeque()

            remaining.add(root)

            while (remaining.isNotEmpty()) {
                val current = remaining.poll()
                val valueSize = current.value?.let { sizeOf(it) } ?: 0L
                val childrenKeysSize = current.children.keys.sumOf { it.toByteArray().size }
                estimate += (valueSize + childrenKeysSize)
                remaining.addAll(current.children.values)
            }

            return estimate
        }
    }

    /**
     * An encoded [TrieIndex], with each instance of [EncodedNode] being a node in the tree.
     * Each node may have [children] and/or a [value].
     *
     * The values of the entries/nodes are determined during encoding.
     */
    data class EncodedNode<E>(
        override val children: Map<String, EncodedNode<E>>, override val value: E?
    ) : TransformableNode<E, Map<String, EncodedNode<E>>>, Index.Encoded<E>

    internal data class IndexNode<T>(override val children: MutableMap<String, IndexNode<T>>, override var value: T?) :
        TransformableNode<T, MutableMap<String, IndexNode<T>>> {
        @Suppress("EqualsAlwaysReturnsTrueOrFalse")
        override fun equals(other: Any?): Boolean = false // disabled
        override fun hashCode(): Int = 0 // disabled
    }

    private interface TransformableNode<T, ChildrenMap : Map<String, TransformableNode<T, ChildrenMap>>> {
        val children: ChildrenMap
        val value: T?
    }

    /**
     * Replaces the root node of this index.
     */
    internal fun withRoot(other: IndexNode<T>): TrieIndex<T> = apply {
        root = other

        actualSize = 0
        forEachNode { _, node ->
            if (node.value != null) {
                actualSize += 1
            }
        }
    }

    /**
     * Splits this path string into its parts, based on the provided [separator].
     *
     * Blank part are removed, for example `"/a/b//c"` will be split into `listOf("a", "b", "c")`
     * and not `listOf("", "a", "b", "", "c")`.
     */
    internal fun String.parts(): List<String> = this.split(separator).filter { it.isNotBlank() }

    /**
     * Rebuilds the specified list of path parts into a full path, based on the provided [separator].
     */
    internal fun rebuild(parts: List<String>): String = parts.joinToString(separator)

    /**
     * Retrieves the node at the provided [path], if it exists.
     */
    internal fun getNode(path: List<String>): IndexNode<T>? {
        var last: IndexNode<T> = root

        path.forEach { part ->
            when (val existing = last.children[part]) {
                null -> return null
                else -> last = existing
            }
        }

        return last
    }

    /**
     * Removes and returns the node at the provided [path], if it exists.
     */
    internal fun removeNode(path: List<String>) {
        var last: IndexNode<T> = root
        val pathNodes: Stack<Pair<IndexNode<T>, String>> = Stack()
        var removed = false

        path.forEach { part ->
            when (val child = last.children[part]) {
                null -> return
                else -> {
                    pathNodes.push(last to part)
                    last = child
                }
            }
        }

        if (last.children.isEmpty()) {
            while (pathNodes.isNotEmpty()) {
                val (parent, part) = pathNodes.pop()
                parent.children.remove(part)
                removed = true

                if (parent.value != null || parent.children.isNotEmpty()) {
                    break
                }
            }
        } else if (last.value != null) {
            last.value = null
            removed = true
        }

        if (removed) {
            actualSize -= 1
        }
    }

    /**
     * Retrieves the node at the provided [path] or creates a new (empty) node, if it doesn't exist.
     */
    internal fun getOrCreateNode(path: List<String>): IndexNode<T> {
        var last: IndexNode<T> = root

        path.forEach { part ->
            when (val existing = last.children[part]) {
                null -> {
                    val new = IndexNode<T>(children = mutableMapOf(), value = null)
                    last.children[part] = new
                    last = new
                }

                else -> last = existing
            }
        }

        return last
    }

    /**
     * Applies the provided function [f] to each node in the index.
     */
    internal inline fun forEachNode(f: (List<String>, IndexNode<T>) -> Unit) {
        val remaining: Queue<Pair<List<String>, IndexNode<T>>> = ArrayDeque()

        remaining.add(listOf("") to root)

        while (remaining.isNotEmpty()) {
            val (currentPath, currentNode) = remaining.poll()

            f(currentPath, currentNode)

            currentNode.children.forEach { (childPart, childNode) ->
                remaining.add((currentPath + childPart) to childNode)
            }
        }
    }
}
