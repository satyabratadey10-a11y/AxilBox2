package com.axilbox.app.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Encapsulates a resolved boot asset path (e.g. disk image, kernel, or initramfs)
 * and holds any open ParcelFileDescriptor until the QEMU process lifecycle completes.
 */
data class ResolvedBootResource(
    val path: String,
    val pfd: ParcelFileDescriptor? = null,
    val isDirectFd: Boolean = false,
    val isFallbackCopy: Boolean = false,
    val isReadOnly: Boolean = false
) : AutoCloseable {
    override fun close() {
        try {
            pfd?.close()
        } catch (_: Exception) {}
    }
}

object UriUtils {
    private const val TAG = "UriUtils"

    /**
     * Resolves a user-configured URI string into an operating-system path accessible by QEMU.
     *
     * 1. Direct POSIX Path: If the string is a local accessible filesystem path, returns it directly.
     * 2. SAF FD Passthrough: For content:// or file:// URIs, opens a ParcelFileDescriptor via SAF,
     *    clears FD_CLOEXEC so child processes inherit the descriptor across fork/exec, and returns
     *    "/proc/self/fd/<rawFd>". This provides zero-copy, direct-storage I/O without copying gigabytes.
     * 3. Fallback (copyUriToCache): If descriptor access fails or is restricted, explicitly copies
     *    the stream to app-private cache storage.
     */
    fun resolveBootResource(
        context: Context,
        uriString: String?,
        writable: Boolean = false
    ): ResolvedBootResource? {
        if (uriString.isNullOrBlank()) return null

        // 1. Direct local filesystem path
        if (uriString.startsWith("/") && !uriString.startsWith("/proc/")) {
            val file = File(uriString)
            if (file.exists() && file.canRead()) {
                Log.i(TAG, "[Direct Path] Resolved local file path: $uriString")
                return ResolvedBootResource(
                    path = file.absolutePath,
                    pfd = null,
                    isDirectFd = false,
                    isFallbackCopy = false,
                    isReadOnly = !file.canWrite() || !writable
                )
            }
        }

        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse URI string '$uriString': ${e.message}")
            null
        } ?: return null

        val scheme = uri.scheme ?: when {
            uriString.startsWith("content://") -> "content"
            uriString.startsWith("file://") -> "file"
            else -> ""
        }

        // 2. Storage Access Framework (SAF) ParcelFileDescriptor Passthrough
        if (scheme == "content" || scheme == "file") {
            try {
                val mode = if (writable) "rw" else "r"
                var pfd: ParcelFileDescriptor? = null
                var actuallyWritable = writable
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri, mode)
                } catch (e: Exception) {
                    if (writable) {
                        Log.w(TAG, "[SAF Passthrough] 'rw' mode denied for $uri (${e.message}), trying read-only 'r' mode")
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        actuallyWritable = false
                    } else {
                        throw e
                    }
                }

                var isReadOnly = !actuallyWritable

                if (pfd != null) {
                    val rawFd = pfd.fd
                    if (rawFd >= 0) {
                        // Check actual kernel open flags via fcntl F_GETFL if available
                        try {
                            val flags = android.system.Os.fcntlInt(pfd.fileDescriptor, android.system.OsConstants.F_GETFL, 0)
                            val accMode = flags and android.system.OsConstants.O_ACCMODE
                            if (accMode == android.system.OsConstants.O_RDONLY) {
                                isReadOnly = true
                            }
                        } catch (_: Throwable) {
                            // Ignored in unit tests or unsupported environments
                        }

                        // Clear FD_CLOEXEC so that child processes (QEMU binary) inherit the descriptor across fork/exec
                        try {
                            android.system.Os.fcntlInt(pfd.fileDescriptor, android.system.OsConstants.F_SETFD, 0)
                        } catch (t: Throwable) {
                            Log.w(TAG, "[SAF Passthrough] Clearing FD_CLOEXEC flag via fcntlInt encountered: ${t.message}")
                        }

                        val procFdPath = "/proc/self/fd/$rawFd"
                        Log.i(TAG, "[SAF Passthrough] Successfully opened $uri as $procFdPath (fd=$rawFd, writable=${!isReadOnly})")
                        return ResolvedBootResource(
                            path = procFdPath,
                            pfd = pfd,
                            isDirectFd = true,
                            isFallbackCopy = false,
                            isReadOnly = isReadOnly
                        )
                    } else {
                        pfd.close()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[SAF Passthrough Failed] Could not open ParcelFileDescriptor for $uri: ${e.message}. Proceeding to copyUriToCache fallback.")
            }

            // 3. Fallback: copyUriToCache
            val fallbackFile = copyUriToCache(context, uri)
            if (fallbackFile != null && fallbackFile.exists() && fallbackFile.length() > 0) {
                Log.i(TAG, "[Fallback copyUriToCache] Successfully copied $uri to cache: ${fallbackFile.absolutePath} (${fallbackFile.length()} bytes)")
                return ResolvedBootResource(
                    path = fallbackFile.absolutePath,
                    pfd = null,
                    isDirectFd = false,
                    isFallbackCopy = true,
                    isReadOnly = !fallbackFile.canWrite() || !writable
                )
            }
        }

        return null
    }

    /**
     * Explicit fallback copy mechanism. Copies the SAF content stream into the app's private cache dir.
     * Note: For multi-gigabyte disk images, this requires sufficient free internal storage and time.
     */
    fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val cacheDir = File(context.cacheDir, "saf_fallback").apply { mkdirs() }
            val fileName = "boot_media_${abs(uri.toString().hashCode())}.img"
            val targetFile = File(cacheDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) targetFile else null
        } catch (e: Exception) {
            Log.e(TAG, "[Fallback copyUriToCache] Failed to copy URI $uri to cache: ${e.message}", e)
            null
        }
    }

    /**
     * Copies a SAF or file URI to app-private storage (context.filesDir/boot_media),
     * skipping the copy on subsequent boots if a content-hash-matched copy already exists in filesDir.
     * Returns the persistent File in app-private storage.
     */
    fun copyUriToFilesDirWithHash(
        context: Context,
        uriString: String?,
        prefix: String = "boot_asset"
    ): File? {
        if (uriString.isNullOrBlank()) return null

        val filesDir = context.filesDir ?: return null
        val targetDir = File(filesDir, "boot_media").apply { mkdirs() }
        val hashKey = abs(uriString.hashCode())
        val targetFile = File(targetDir, "${prefix}_$hashKey.bin")
        val hashFile = File(targetDir, "${prefix}_$hashKey.sha256")

        try {
            // Check if source is an existing readable local file already in filesDir
            if (uriString.startsWith("/") && !uriString.startsWith("/proc/")) {
                val directFile = File(uriString)
                if (directFile.exists() && directFile.canRead()) {
                    if (directFile.canonicalPath.startsWith(filesDir.canonicalPath)) {
                        return directFile
                    }
                }
            }

            // Open input stream from content resolver or filesystem
            val inputStream = when {
                uriString.startsWith("/") && !uriString.startsWith("/proc/") -> {
                    val directFile = File(uriString)
                    if (directFile.exists() && directFile.canRead()) {
                        FileInputStream(directFile)
                    } else null
                }
                else -> {
                    val uri = try { Uri.parse(uriString) } catch (_: Exception) { null }
                    if (uri != null) context.contentResolver.openInputStream(uri) else null
                }
            } ?: return null

            val tempFile = File(targetDir, "${prefix}_${hashKey}.tmp")
            val digest = MessageDigest.getInstance("SHA-256")

            val sourceHash = inputStream.use { src ->
                DigestInputStream(src, digest).use { dis ->
                    FileOutputStream(tempFile).use { fos ->
                        dis.copyTo(fos)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }

            val existingHash = if (hashFile.exists()) hashFile.readText().trim() else ""
            if (targetFile.exists() && existingHash == sourceHash && targetFile.length() == tempFile.length()) {
                // Content-hash matched! Skip re-copying, reuse existing target file
                tempFile.delete()
                Log.i(TAG, "[$prefix] Content-hash matched ($sourceHash). Reusing existing ${targetFile.absolutePath}")
                return targetFile
            }

            // New file or modified content: replace targetFile and update hash
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            hashFile.writeText(sourceHash)
            Log.i(TAG, "[$prefix] Copied $uriString to ${targetFile.absolutePath} (SHA-256: $sourceHash, size: ${targetFile.length()} bytes)")
            return targetFile
        } catch (e: Exception) {
            Log.e(TAG, "[$prefix] Failed to copy URI $uriString to filesDir: ${e.message}", e)
            return null
        }
    }

    /**
     * Resolves a boot resource specifically via copy-to-files-dir with content-hash matching,
     * ensuring that QEMU ROM/kernel/initrd loaders receive a standard, plain filesystem path
     * that can be opened via libc open() without SELinux or /dev/fdset failure.
     */
    fun resolveBootResourceViaCopy(
        context: Context,
        uriString: String?,
        prefix: String = "boot_asset"
    ): ResolvedBootResource? {
        if (uriString.isNullOrBlank()) return null

        val copiedFile = copyUriToFilesDirWithHash(context, uriString, prefix)
        if (copiedFile != null && copiedFile.exists() && copiedFile.length() > 0) {
            return ResolvedBootResource(
                path = copiedFile.absolutePath,
                pfd = null,
                isDirectFd = false,
                isFallbackCopy = true,
                isReadOnly = true
            )
        }
        return null
    }
}
