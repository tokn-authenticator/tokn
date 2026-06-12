package me.diamondforge.tokn.sync.lan

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceNameTest {

    @Test
    fun `name carries a four digit collision suffix`() {
        assertTrue(DeviceName.build().matches(Regex("^.+ #\\d{4}$")))
    }

    @Test
    fun `the base never collapses to empty`() {
        val base = DeviceName.build().substringBeforeLast(" #")
        assertTrue(base.isNotBlank())
    }
}
