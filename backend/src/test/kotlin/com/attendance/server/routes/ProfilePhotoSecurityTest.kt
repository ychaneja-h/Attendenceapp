package com.attendance.server.routes

import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import javax.imageio.ImageIO

class ProfilePhotoSecurityTest {
    private fun normalize(bytes: ByteArray): ByteArray {
        val method = Class.forName("com.attendance.server.routes.UserRoutesKt")
            .getDeclaredMethod("normalizeProfilePhoto", ByteArray::class.java)
        method.isAccessible = true
        try { return method.invoke(null, bytes) as ByteArray }
        catch (error: InvocationTargetException) { throw error.targetException }
    }

    private fun image(width: Int, height: Int, format: String = "png"): ByteArray {
        val bitmap = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().use { out ->
            check(ImageIO.write(bitmap, format, out))
            bitmap.flush()
            out.toByteArray()
        }
    }

    @Test fun reencodesJpegAsPng() {
        val result = normalize(image(40, 30, "jpeg"))
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47), result.take(4).toByteArray())
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(40, decoded.width)
        assertEquals(30, decoded.height)
    }

    @Test fun stripsAppendedActiveContentAndResizes() {
        val active = "<script>alert('unsafe')</script>".toByteArray()
        val result = normalize(image(2048, 128) + active)
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(1024, decoded.width)
        assertEquals(64, decoded.height)
        assertFalse(result.toString(Charsets.ISO_8859_1).contains("<script>"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSvg() { normalize("<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>".toByteArray()) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCorruptPng() { normalize(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExcessiveDimensions() { normalize(image(4097, 1)) }
}
