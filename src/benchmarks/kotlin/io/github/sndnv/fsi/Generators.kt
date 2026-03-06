package io.github.sndnv.fsi

object Generators {
    fun generatePaths(directoryLevels: Int, entitiesPerDirectory: Int, separator: String = "/"): List<String> {
        tailrec fun generate(
            remaining: Int,
            generated: List<String>,
        ): List<String> {
            return if (remaining == 0) {
                generated
            } else {
                val entities = entitiesPerDirectory / 2

                generate(
                    remaining = remaining - 1,
                    generated = generated.flatMap { current ->
                        if (current.contains("file")) {
                            listOf(current)
                        } else {
                            val files = (0 until entities).map {
                                "${current}${separator}file_${it}"
                            }

                            val directories = (0 until entities).map {
                                "${current}${separator}directory_${it}"
                            }

                            files + directories
                        }
                    }
                )
            }
        }

        return generate(remaining = directoryLevels, generated = listOf(""))
    }
}
