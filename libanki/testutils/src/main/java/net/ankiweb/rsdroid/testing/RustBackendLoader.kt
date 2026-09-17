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

/**
 * Loads a librsdroid alternative to allow testing of rsdroid under a Robolectric-based environment.
 *
 * This local override diverges from the upstream test helper by giving every extraction a filename of
 * its own. Robolectric commonly creates multiple sandbox classloaders in a single process, and a shared
 * extracted path can lead to native bindings being associated with the wrong loader on Windows.
 */
object RustBackendLoader {
    private var hasSetUp = false
    var printDebug = false

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
            normalizedOsName.contains("mac") || normalizedOsName.contains("darwin") -> load(
                "librsdroid", ".dylib"
            )

            normalizedOsName.contains("nix") || normalizedOsName.contains("nux") || normalizedOsName.contains(
                "linux"
            ) -> load("librsdroid", ".so")

            else -> throw IllegalStateException("Could not determine OS Type for: '$osName'")
        }
        hasSetUp = true
    }

    private fun print(message: String) {
        if (printDebug) {
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
            val extracted = File(path)
            if (!extracted.exists()) {
                // keep the linker's own text, and the error itself as the cause: "no such file",
                // "cannot allocate memory" and "invalid ELF header" are three different bugs, and a bare
                // FileNotFoundException names none of them. dropping it is why the ci failure that
                // prompted this file's last change (github run 35273990450) is still unexplained
                throw RuntimeException(
                    FileNotFoundException(
                        "Extracted file was not found. Maybe the temp folder was deleted. Please try again: '$path'" +
                            " (load failed: ${e.message}; temp dir present: ${extracted.parentFile?.exists()})",
                    ).apply { initCause(e) },
                )
            }
            // the only link failure this may swallow is the jvm refusing a second System.load() of one
            // path from another classloader, which is the message upstream's helper matches too. a file
            // per extraction means it can no longer happen here, but a shared path would still be
            // harmless. every other link error - a truncated copy, a missing dependency, a policy block -
            // has to fail the test instead of leaving hasSetUp true with no backend loaded
            if (!(e.message ?: "").contains("already loaded in another classloader", ignoreCase = true)) {
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
        sweepOldExtractions(fileName, extension)

        // a file of this extraction's own. the name used to be the library's checksum plus the
        // classloader's identity hash, and that hash repeats across jvm launches (measured: the same
        // value on every run of the same code), so every test worker gradle forks arrived at one shared
        // path rather than its own. keeping that shared path current needed rename, copy(overwrite) and
        // a delete on failure - the last two unlink a file another worker may be loading, and on windows,
        // where renaming over a loaded dll fails, that fallback is the live path. a per-extraction name
        // removes that class of race and still gives each robolectric sandbox a path of its own, which is
        // what this override exists for. it is not known to be the cause of run 35273990450
        val extracted = File.createTempFile("$fileName-", extension)
        // tens of megabytes per sandbox: the jvm unlinks them when it exits. a library windows still has
        // mapped cannot be deleted and that failure is silent, so windows keeps its copies until the
        // sweep above reaches them
        extracted.deleteOnExit()
        try {
            val buffer = ByteArray(8 * 1024)
            extracted.outputStream().use { outStream ->
                withStream(fullFilename) { inStream ->
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                    }
                }
                outStream.flush()
            }
        } catch (e: Throwable) {
            // an empty or half-written copy is indistinguishable from a good one afterwards, and on
            // windows deleteOnExit may never remove it
            extracted.delete()
            throw e
        }

        return extracted.absolutePath
    }

    /**
     * Deletes library copies left behind by earlier runs.
     *
     * [File.deleteOnExit] cannot unlink a library Windows still has mapped, so without this the temp
     * directory grows by one copy per extraction and never shrinks. Only copies older than an hour are
     * touched, so a worker running beside this one keeps its own; a delete that fails is a copy still in
     * use, which is expected and ignored.
     */
    private fun sweepOldExtractions(
        fileName: String,
        extension: String,
    ) {
        val tempDir = System.getProperty("java.io.tmpdir")?.let { File(it) } ?: return
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        tempDir
            .listFiles()
            ?.filter {
                it.isFile &&
                    it.name.startsWith("$fileName-") &&
                    it.name.endsWith(extension) &&
                    it.lastModified() < cutoff
            }?.forEach { it.delete() }
    }

    private fun <T> withStream(
        fullFilename: String,
        func: (InputStream) -> T,
    ): T {
        val loader = RustBackendLoader::class.java.classLoader
            ?: throw IllegalStateException("Could not retrieve classloader for RustBackendLoader")
        val stream = loader.getResourceAsStream(fullFilename)
            ?: throw IllegalStateException("Could not find bundled backend resource '$fullFilename'")
        return stream.use(func)
    }
}
