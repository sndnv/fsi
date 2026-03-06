package io.github.sndnv.fsi

import java.util.regex.Pattern

abstract class IndexBenchmarkSetup {
    val directoryLevels: Int = 5
    val entitiesPerDirectory: Int = 20

    val path1: String = "/a/b/c"
    val path2: String = "/directory_0/directory_1/file_1"
    val path3: String = "/directory_0/directory_1/directory_2/file_0"
    val pathMap: Map<String, Int> = mapOf(path1 to 1, path2 to 2, path3 to 3)
    val pathList: List<String> = pathMap.keys.toList()
    val regexNonMatching: Pattern = Pattern.compile("a^")
    val regexMatching: Pattern = Pattern.compile(".")
}
