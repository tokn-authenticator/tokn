package me.diamondforge.tokn.sync.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.data.preferences.SyncMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChooseMethodViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var prefs: FakeSyncPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        prefs = FakeSyncPreferences(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lastMethod reflects the stored preference`() = runTest(dispatcher) {
        prefs.lastSync.value = SyncMethod.WFD
        val vm = ChooseMethodViewModel(prefs)
        advanceUntilIdle()
        assertEquals(SyncMethod.WFD, vm.lastMethod.value)
    }

    @Test
    fun `commit persists the chosen method`() = runTest(dispatcher) {
        val vm = ChooseMethodViewModel(prefs)
        vm.commit(SyncMethod.QR)
        advanceUntilIdle()

        assertEquals(SyncMethod.QR, prefs.lastSync.value)
        assertEquals(SyncMethod.QR, vm.lastMethod.value)
    }
}
