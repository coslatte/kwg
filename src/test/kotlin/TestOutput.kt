package test

import java.io.File

/**
 * Centralized test output directory management.
 * All test-generated files go under target/test-output to keep the repo clean.
 */
object TestOutput {
    private val baseDir = File("target/test-output")

    init {
        baseDir.mkdirs()
    }

    fun file(name: String): File = File(baseDir, name)

    fun clean() {
        baseDir.listFiles()?.forEach { it.delete() }
    }
}