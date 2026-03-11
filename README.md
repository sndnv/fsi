# fsi - File System Index

<img src="./assets/fsi.logo.svg" width="64px" alt="fsi Logo" align="right"/>

`fsi` is a library providing simple data structures for efficiently associating information with file system paths.

## Setup

```
dependencies {
    implementation("io.github.sndnv:fsi:<version>")
}
```

## Components

There are currently three implementations available:

* [`MapIndex`](./src/main/kotlin/io/github/sndnv/fsi/backends/MapIndex.kt) - based on a hashmap
* [`TrieIndex`](./src/main/kotlin/io/github/sndnv/fsi/backends/TrieIndex.kt) - based on a prefix tree (trie)
* [`SharedIndex`](./src/main/kotlin/io/github/sndnv/fsi/backends/SharedIndex.kt) - using a shared `TrieIndex` as storage

### `MapIndex` vs `TrieIndex`

The main difference between `MapIndex` and `TrieIndex` is their underlying storage - a hashmap in the case of `MapIndex`
and a prefix tree (trie) for `TrieIndex`. In theory, a `MapIndex` should have better performance
for basic operations (add, remove, retrieve) but it uses more memory to store the full paths;
versus a `TrieIndex` that has to traverse the tree to get to a node (which is slower) but stores
the parts of a path only once.

The benefits of `TrieIndex` over `MapIndex` are most visible when collecting information about
a large amount of files on a deeply nested file system.

To illustrate this, we can take the following setup:

```kotlin
// the value in the index is not relevant for this example
val map = MapIndex.mutable<Int>()
val trie = TrieIndex.mutable<Int>(separator = java.nio.file.FileSystems.getDefault().separator)

(0 until 5).forEach { i ->
    map.put("/a/b/c/$i", 0)
    trie.put("/a/b/c/$i", 0)
}
```

then data for the `map` index will be stored as:

```json
{
  "/a/b/c/0": 0,
  "/a/b/c/1": 0,
  "/a/b/c/2": 0,
  "/a/b/c/3": 0,
  "/a/b/c/4": 0
}
```

with each entry having a copy of `/a/b/c`, whereas in the `trie` index, it will be (essentially):

```json
{
  "a": {
    "b": {
      "c": {
        "0": 0,
        "1": 0,
        "2": 0,
        "3": 0,
        "4": 0
      }
    }
  }
}
```

with the parts of `/a/b/c` being stored only once.

### `SharedIndex`

As for `SharedIndex`, it works by having a "shared" `TrieIndex` as storage so that a path is only ever kept in memory once, and it
is "reused" for all instances of `SharedIndex`, thus reducing memory usage even further.

For example, with the following setup:

```kotlin
// the value in the index is not relevant for this example
val indexA = SharedIndex.default<Int>()
val indexB = SharedIndex.default<Int>()

(0 until 5).forEach { i ->
    indexA.put("/a/b/c/$i", 1)
    indexB.put("/a/b/c/$i", 2)
}
```

we will have in memory (roughly):

```json
{
  "a": {
    "b": {
      "c": {
        "0": {
          "index-a": 1,
          "index-b": 2
        },
        "1": {
          "index-a": 1,
          "index-b": 2
        },
        "2": {
          "index-a": 1,
          "index-b": 2
        },
        "3": {
          "index-a": 1,
          "index-b": 2
        },
        "4": {
          "index-a": 1,
          "index-b": 2
        }
      }
    }
  }
}
```

> The actual index references and values in a `SharedIndex` as stored in a `WeakHashMap`; as soon as an index instance is no
> longer used/accessible, it is made eligible for garbage collection and will (eventually) be automatically discarded from the
> shared storage.

## Usage

Below are a few examples of how to use the `Index` API; for all available functionality, you can check the
code [here](./src/main/kotlin/io/github/sndnv/fsi/Index.kt) or go through the KDocs using your IDE.

### Basic Operations

```kotlin
// or MapIndex.concurrent() or TrieIndex.mutable(...) or SharedIndex.default()
val index: io.github.sndnv.fsi.Index<Int> = MapIndex.mutable()
val path = "/a/b/c"

// basic operations
index.size // should return 0
index.get(path) // should return null

index.put(path, 42) // adds a new entry

index.size // should return 1
index.get(path) // should return 42

index.remove(path) // removes the existing entry
index.size // should return 0
index.get(path) // should return null
```

### Encoding and Decoding

#### `MapIndex`

```kotlin
val index = MapIndex.mutable<Int>()

val encoded = index.encode { it.toLong() } // encoding and converting the values from Int to Long (for example)
val decoded = MapIndex.decodedConcurrent(encoded) { it.toInt() } // decoding and converting the values from Long to Int
```

#### `TrieIndex`

```kotlin
val index = TrieIndex.mutable<Int>(fs)

val encoded = index.encode { it.toLong() } // encoding and converting the values from Int to Long (for example)
val decoded = TrieIndex.decoded(encoded, fs) { it.toInt() } // decoding and converting the values from Long to Int
```

#### `SharedIndex`

```kotlin
val index = SharedIndex.default<Int>()

val encoded = index.encode { it.toLong() } // encoding and converting the values from Int to Long (for example)
val decoded = SharedIndex.decoded(encoded) { it.toInt() } // decoding and converting the values from Long to Int
```

## Development

Refer to the [DEVELOPMENT.md](DEVELOPMENT.md) file for more details.

### Benchmarks

Benchmark tests are available for comparing the performance of the supported index implementations.

To execute them and generate a report, run:

* `./gradlew benchmark` - standard benchmarks; faster to run but has higher score error
* `./gradlew extendedBenchmark` - extended benchmarks; takes much longer but improves result accuracy

There's also a [Kotlin Notebook](https://kotlinlang.org/docs/kotlin-notebook-overview.html) available
in the [`notebooks`](./notebooks) directory; it can be used to analyze the results of a benchmark run.

## Contributing

Contributions are always welcome!

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details.

## Versioning

We use [SemVer](http://semver.org/) for versioning.

## License

This project is licensed under the Apache License, Version 2.0 - see the [LICENSE](LICENSE) file for details

> Copyright 2026 https://github.com/sndnv
>
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
> http://www.apache.org/licenses/LICENSE-2.0
>
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.
