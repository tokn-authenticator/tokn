package me.diamondforge.tokn.home

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpFormattingTest {

    @Test
    fun `six digit codes split in the middle`() {
        assertEquals("123 456", formatOtpCode("123456"))
    }

    @Test
    fun `eight digit codes split in the middle`() {
        assertEquals("1234 5678", formatOtpCode("12345678"))
    }

    @Test
    fun `other lengths pass through unchanged`() {
        assertEquals("1234567", formatOtpCode("1234567"))
        assertEquals("", formatOtpCode(""))
    }

    @Test
    fun `mask mirrors the formatting with bullets`() {
        assertEquals("••• •••", maskOtpCode("123456"))
        assertEquals("•••• ••••", maskOtpCode("12345678"))
    }
}
