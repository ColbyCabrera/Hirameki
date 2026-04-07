/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb.rsdroid.testing

import android.annotation.SuppressLint
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.security.MessageDigest
import java.util.HashMap
import kotlin.Throws

/**
 * Loads a librsdroid alternative to allow testing of rsdroid under a Robolectric-based environment.
 *
 * This local override diverges from the upstream test helper by extracting a classloader-specific
 * filename. Robolectric commonly creates multiple sandbox classloaders in a single process, and the
 * shared extracted path can lead to native bindings being associated with the wrong loader on Windows.
 */
object RustBackendLoader {
    private var hasSetUp = false
    private val fileNameToPathCache = HashMap<String, String>()
    var PRINT_DEBUG = false

    @JvmStatic
    @Synchronized
    fun ensureSetup() {
        if (hasSetUp) {
            return
        }
        val osName = System.getProperty("os.name") ?: ""
        val normalizedOsName = osName.lowercase()
        print("loading rsdroid-testing for: $osName")
        when {
            normalizedOsName.contains("win") -> load("rsdroid", ".dll")
            normalizedOsName.contains("mac") || normalizedOsName.contains("darwin") -> load("librsdroid", ".dylib")
            normalizedOsName.contains("nix") || normalizedOsName.contains("nux") || normalizedOsName.contains("linux") -> load("librsdroid", ".so")
            else -> throw IllegalStateException("Could not determine OS Type for: '$osName'")
        }
        hasSetUp = true
    }

    private fun print(message: String) {
        if (PRINT_DEBUG) {
            println(message)
        }
    }

    private fun load(
        fileName: String,
        extension: String,
    ) {
        val path = getPathFromResourceStream(fileName, extension)
        loadPath(path)
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadPath(path: String) {
        try {
            // loadLibrary() cannot target the extracted classloader-specific temp file used here.
            // This helper must load the exact absolute path to keep Robolectric sandboxes isolated.
            System.load(path)
        } catch (e: UnsatisfiedLinkError) {
            if (!File(path).exists()) {
                throw RuntimeException(
                    FileNotFoundException(
                        "Extracted file was not found. Maybe the temp folder was deleted. Please try again: '$path'",
                    ),
                )
            }
            if (e.message == null || !e.message!!.contains("already loaded in another classloader")) {
                throw e
            }
            print("native library already loaded in another classloader: $path")
        }
    }

    @Throws(IOException::class)
    private fun getPathFromResourceStream(
        fileName: String,
        extension: String,
    ): String {
        val fullFilename = fileName + extension
        fileNameToPathCache[fullFilename]?.let { return it }

        val buffer = ByteArray(8 * 1024)
        val checksum =
            withStream(fullFilename) { stream ->
                val digest = MessageDigest.getInstance("SHA-1")
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }

        val loaderId = System.identityHashCode(RustBackendLoader::class.java.classLoader)
        val expectedFile = File(System.getProperty("java.io.tmpdir"), "$fileName-$checksum-$loaderId$extension")
        if (!expectedFile.exists()) {
            val tempFile = File.createTempFile("$fileName-$loaderId-", extension)
            tempFile.outputStream().use { outStream ->
                withStream(fullFilename) { inStream ->
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                    }
                }
                outStream.flush()
            }
            check(tempFile.renameTo(expectedFile) || tempFile.copyTo(expectedFile, overwrite = true).let { tempFile.delete(); true }) {
                "Could not move extracted rsdroid library to $expectedFile"
            }
        }

        fileNameToPathCache[fullFilename] = expectedFile.path
        return expectedFile.absolutePath
    }

    private fun <T> withStream(
        fullFilename: String,
        func: (InputStream) -> T,
    ): T {
        val stream = RustBackendLoader::class.java.classLoader!!.getResourceAsStream(fullFilename)
            ?: throw IllegalStateException("Could not find bundled backend resource '$fullFilename'")
        return stream.use(func)
    }
}