package me.diamondforge.tokn.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.diamondforge.tokn.domain.model.GroupSort
import me.diamondforge.tokn.domain.model.OtpAccount
import me.diamondforge.tokn.domain.testing.FakeAccountRepository
import me.diamondforge.tokn.domain.usecase.CreateGroupUseCase
import me.diamondforge.tokn.domain.usecase.DeleteGroupUseCase
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.domain.usecase.ListGroupsUseCase
import me.diamondforge.tokn.domain.usecase.RenameGroupUseCase
import me.diamondforge.tokn.domain.usecase.ReorderGroupsUseCase
import me.diamondforge.tokn.domain.usecase.SetGroupColorUseCase
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
class GroupManagementViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repo: FakeAccountRepository
    private lateinit var userPreferences: FakeUserPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repo = FakeAccountRepository(
            listOf(
                account(1, listOf("Work", "Personal")),
                account(2, listOf("work")),
                account(3, listOf("VIP")),
            ),
        )
        userPreferences = FakeUserPreferences(context)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun account(id: Long, groups: List<String>) =
        OtpAccount(
            id = id,
            issuer = "i$id",
            accountName = "n$id",
            secret = "JBSWY3DPEHPK3PXP",
            groups = groups
        )

    private fun newVm() = GroupManagementViewModel(
        getAccountsUseCase = GetAccountsUseCase(repo),
        listGroupsUseCase = ListGroupsUseCase(repo),
        createGroupUseCase = CreateGroupUseCase(repo),
        renameGroupUseCase = RenameGroupUseCase(repo),
        deleteGroupUseCase = DeleteGroupUseCase(repo),
        setGroupColorUseCase = SetGroupColorUseCase(repo),
        reorderGroupsUseCase = ReorderGroupsUseCase(repo),
        userPreferences = userPreferences,
    )

    private fun TestScope.state(vm: GroupManagementViewModel): () -> GroupManagementUiState {
        var latest = vm.uiState.value
        backgroundScope.launch { vm.uiState.collect { latest = it } }
        runCurrent()
        return { latest }
    }

    @Test
    fun `groups are aggregated case-insensitively and counted, ordered by first appearance`() =
        runTest(dispatcher) {
            val current = state(newVm())

            val rows = current().groups
            assertEquals(listOf("Work", "Personal", "VIP"), rows.map { it.name })
            assertEquals(2, rows.first { it.name == "Work" }.accountCount)
            assertEquals(1, rows.first { it.name == "VIP" }.accountCount)
        }

    @Test
    fun `rename routes through the use case`() = runTest(dispatcher) {
        val vm = newVm()
        state(vm)

        vm.rename("Work", "Office")
        advanceUntilIdle()

        assertEquals(listOf("Office", "Personal"), repo.snapshot.first { it.id == 1L }.groups)
        assertEquals(listOf("Office"), repo.snapshot.first { it.id == 2L }.groups)
    }

    @Test
    fun `rename to a blank or unchanged name does nothing`() = runTest(dispatcher) {
        val vm = newVm()
        state(vm)

        vm.rename("Work", "   ")
        vm.rename("Work", "Work")
        advanceUntilIdle()

        assertEquals(listOf("Work", "Personal"), repo.snapshot.first { it.id == 1L }.groups)
    }

    @Test
    fun `delete removes the group from every account`() = runTest(dispatcher) {
        val vm = newVm()
        state(vm)

        vm.delete("VIP")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repo.snapshot.first { it.id == 3L }.groups)
    }

    @Test
    fun `setSort to name ascending reorders the list alphabetically`() = runTest(dispatcher) {
        val vm = newVm()
        val current = state(vm)

        userPreferences.groupSortState.value = GroupSort.NAME_ASC
        runCurrent()

        assertEquals(GroupSort.NAME_ASC, current().sort)
        assertEquals(listOf("Personal", "VIP", "Work"), current().groups.map { it.name })
    }

    @Test
    fun `setSort to name descending reorders the list reverse alphabetically`() =
        runTest(dispatcher) {
            val vm = newVm()
            val current = state(vm)

            userPreferences.groupSortState.value = GroupSort.NAME_DESC
            runCurrent()

            assertEquals(listOf("Work", "VIP", "Personal"), current().groups.map { it.name })
        }

    @Test
    fun `setSort persists through the use case`() = runTest(dispatcher) {
        val vm = newVm()
        state(vm)

        vm.setSort(GroupSort.NAME_ASC)
        advanceUntilIdle()

        assertEquals(GroupSort.NAME_ASC, userPreferences.groupSortState.value)
    }

    @Test
    fun `reorder is ignored while sort is not custom`() = runTest(dispatcher) {
        val vm = newVm()
        val current = state(vm)
        userPreferences.groupSortState.value = GroupSort.NAME_ASC
        runCurrent()

        vm.reorder(listOf("VIP", "Work", "Personal"))
        advanceUntilIdle()

        userPreferences.groupSortState.value = GroupSort.CUSTOM
        runCurrent()

        assertEquals(listOf("Work", "Personal", "VIP"), current().groups.map { it.name })
    }
}
