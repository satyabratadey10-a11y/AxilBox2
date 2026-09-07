package com.axilbox.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class UriUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
    }

    @Test
    fun resolveBootResource_whenNullOrBlank_returnsNull() {
        assertNull(UriUtils.resolveBootResource(context, null))
        assertNull(UriUtils.resolveBootResource(context, ""))
        assertNull(UriUtils.resolveBootResource(context, "   "))
    }

    @Test
    fun resolveBootResource_whenDirectLocalFileExists_returnsDirectPath() {
        val testFile = tempFolder.newFile("guest_disk.img")
        testFile.writeText("disk data")

        val res = UriUtils.resolveBootResource(context, testFile.absolutePath)
        assertNotNull(res)
        assertEquals(testFile.absolutePath, res?.path)
        assertFalse(res?.isDirectFd ?: true)
        assertFalse(res?.isFallbackCopy ?: true)
    }

    @Test
    fun resolveBootResource_whenSafPfdSucceeds_returnsProcSelfFd() {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/101")
        val mockPfd: ParcelFileDescriptor = mockk(relaxed = true)
        every { mockPfd.fd } returns 33
        every { contentResolver.openFileDescriptor(uri, "rw") } returns mockPfd

        val res = UriUtils.resolveBootResource(context, uri.toString(), writable = true)
        assertNotNull(res)
        assertEquals("/proc/self/fd/33", res?.path)
        assertTrue(res?.isDirectFd ?: false)
        assertFalse(res?.isFallbackCopy ?: true)

        res?.close()
        verify(exactly = 1) { mockPfd.close() }
    }

    @Test
    fun resolveBootResource_whenSafFails_fallsBackToCopyUriToCache() {
        val uri = Uri.parse("content://com.example.provider/image.iso")
        every { contentResolver.openFileDescriptor(uri, any()) } throws SecurityException("Permission denied")
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream("iso payload bytes".toByteArray())

        val res = UriUtils.resolveBootResource(context, uri.toString(), writable = false)
        assertNotNull(res)
        assertTrue(res?.path?.contains("saf_fallback") == true)
        assertFalse(res?.isDirectFd ?: true)
        assertTrue(res?.isFallbackCopy ?: false)

        val cachedFile = File(res!!.path)
        assertTrue(cachedFile.exists())
        assertEquals("iso payload bytes", cachedFile.readText())
    }

    @Test
    fun copyUriToCache_copiesStreamToPrivateCacheDir() {
        val uri = Uri.parse("content://com.example/test.bin")
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream("binary content".toByteArray())

        val file = UriUtils.copyUriToCache(context, uri)
        assertNotNull(file)
        assertTrue(file?.exists() == true)
        assertEquals("binary content", file?.readText())
    }
}
