package com.axilbox.app.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Encapsulates a resolved boot asset path (e.g. disk image, kernel, or initramfs)
 * and holds any open ParcelFileDescriptor until the QEMU process lifecycle completes.
 */
data class ResolvedBootResource(
    val path: String,
    val pfd: ParcelFileDescriptor? = null,
    val isDirectFd: Boolean = false,
    val isFallbackCopy: Boolean = false
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
                    isFallbackCopy = false
                )
            }
        }

        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse URI string '$uriString': ${e.message}")
            return null
        }

        // 2. Storage Access Framework (SAF) ParcelFileDescriptor Passthrough
        if (uri.scheme == "content" || uri.scheme == "file") {
            try {
                val mode = if (writable) "rw" else "r"
                var pfd: ParcelFileDescriptor? = null
                try {
                    pfd = context.contentResolver.openFileDescriptor(uri, mode)
                } catch (e: Exception) {
                    if (writable) {
                        Log.w(TAG, "[SAF Passthrough] 'rw' mode denied for $uri (${e.message}), trying read-only 'r' mode")
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    } else {
                        throw e
                    }
                }

                if (pfd != null) {
                    val rawFd = pfd.fd
                    if (rawFd >= 0) {
                        // Clear FD_CLOEXEC so that child processes (QEMU binary) inherit the descriptor across fork/exec
                        try {
                            android.system.Os.fcntlInt(pfd.fileDescriptor, android.system.OsConstants.F_SETFD, 0)
                        } catch (t: Throwable) {
                            Log.w(TAG, "[SAF Passthrough] Clearing FD_CLOEXEC flag via fcntlInt encountered: ${t.message}")
                        }

                        val procFdPath = "/proc/self/fd/$rawFd"
                        Log.i(TAG, "[SAF Passthrough] Successfully opened $uri as $procFdPath (fd=$rawFd, writable=$writable)")
                        return ResolvedBootResource(
                            path = procFdPath,
                            pfd = pfd,
                            isDirectFd = true,
                            isFallbackCopy = false
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
                    isFallbackCopy = true
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
}
