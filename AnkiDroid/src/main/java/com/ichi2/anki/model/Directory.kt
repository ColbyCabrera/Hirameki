// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.model

import com.ichi2.compat.CompatHelper
import java.io.File
import java.io.IOException
import java.nio.file.NotDirectoryException

/**
 * A directory which is assumed to exist (existed when class was instantiated)
 */
class Directory private constructor(
    val directory: File,
) {
    /** List of files in this directory. If this is not a directory or no longer exists, then an empty array. */
    fun listFiles(): Array<out File> = directory.listFiles() ?: emptyArray()

    /**
     * Whether this directory has at least files
     * @return Whether the directory has file.
     * @throws [SecurityException] If a security manager exists and its SecurityManager.checkRead(String)
     * method denies read access to the directory
     * @throws [java.io.FileNotFoundException] if the file do not exists
     * @throws [NotDirectoryException] if the file could not otherwise be opened because it is not
     * a directory (optional specific exception), (starting at API 26)
     * @throws [IOException] – if an I/O error occurs.
     * This also occurred on an existing directory because of permission issue
     * that we could not reproduce. See https://github.com/ankidroid/Anki-Android/issues/10358
     */
    @Throws(IOException::class, SecurityException::class, NotDirectoryException::class)
    fun hasFiles(): Boolean = CompatHelper.compat.hasFiles(directory)

    /** The [canonical path][java.io.File.getCanonicalPath] for the file */
    override fun toString(): String = directory.canonicalPath

    companion object {
        /**
         * Returns a [Directory] from [path] if `Directory` precondition holds; i.e. [path] is an existing directory.
         * Otherwise returns `null`.
         */
        fun createInstance(path: String): Directory? = createInstance(File(path))

        /**
         * Returns a [Directory] from [file] if `Directory` precondition holds; i.e. [file] is an existing directory.
         * Otherwise returns `null`.
         */
        fun createInstance(file: File): Directory? {
            if (!file.exists() || !file.isDirectory) {
                return null
            }
            return Directory(file)
        }
    }
}
