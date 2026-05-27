package me.diamondforge.tokn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.preferences.ThemeMode
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.navigation.AppNavHost
import me.diamondforge.tokn.security.BiometricHelper
import me.diamondforge.tokn.security.LockManager
import me.diamondforge.tokn.security.VaultPasswordManager
import me.diamondforge.tokn.ui.theme.SimpleOTPTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var lockManager: LockManager
    @Inject
    lateinit var biometricHelper: BiometricHelper
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject
    lateinit var vaultPasswordManager: VaultPasswordManager
    @Inject
    lateinit var getAccountsUseCase: GetAccountsUseCase

    // Set to true once the pre-OOBE migration check has run.
    // The UI must not render the onboarding flow before this is true; otherwise
    // upgrade-from-old-version users see a brief flash of OOBE before being
    // bounced to the home screen.
    private val migrationComplete = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        lifecycleScope.launch {
            migrateOnboardingFlag()
            migrationComplete.value = true
        }

        setContent {
            val themeMode by userPreferencesRepository.themeMode.collectAsStateWithLifecycle(
                ThemeMode.SYSTEM
            )
            val isLocked by lockManager.isLocked.collectAsStateWithLifecycle()
            val screenshotsEnabled by userPreferencesRepository.screenshotsEnabled.collectAsStateWithLifecycle(
                false
            )
            val encryptionEnabled by userPreferencesRepository.encryptionEnabled.collectAsStateWithLifecycle(
                false
            )
            val biometricEnabled by userPreferencesRepository.biometricEnabled.collectAsStateWithLifecycle(
                true
            )
            val onboardingDoneRaw by userPreferencesRepository.onboardingDone.collectAsStateWithLifecycle(
                initialValue = null
            )
            val migrated by migrationComplete.collectAsStateWithLifecycle()
            // Hold the UI at the "loading" state (null) until migration finishes,
            // so old-install upgrades never briefly render the OOBE flow.
            val onboardingDone = if (migrated) onboardingDoneRaw else null

            LaunchedEffect(screenshotsEnabled) {
                if (screenshotsEnabled) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val hasVaultPassword = encryptionEnabled && vaultPasswordManager.hasPassword()

            SimpleOTPTheme(themeMode = themeMode) {
                AppNavHost(
                    isLocked = isLocked,
                    onboardingDone = onboardingDone,
                    onUnlock = { requestBiometric() },
                    onUnlockWithPassword = { password ->
                        withContext(Dispatchers.IO) {
                            if (vaultPasswordManager.verify(password)) {
                                withContext(Dispatchers.Main) { lockManager.unlock() }
                                true
                            } else {
                                false
                            }
                        }
                    },
                    hasVaultPassword = hasVaultPassword,
                    biometricEnabled = biometricEnabled,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val onboardingDone = userPreferencesRepository.onboardingDone.first()
            if (!onboardingDone) {
                lockManager.unlock()
                return@launch
            }
            val encryptionEnabled = userPreferencesRepository.encryptionEnabled.first()
            if (!encryptionEnabled) {
                lockManager.unlock()
                return@launch
            }
            val timeout = userPreferencesRepository.autoLockTimeoutSeconds.first()
            lockManager.onAppForeground(timeout)
            if (lockManager.isLocked.value != false) {
                requestBiometric()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lockManager.onAppBackground()
    }

    // Pre-OOBE installs ship without an onboarding flag. If the user already has accounts
    // or a vault password from before this version, treat onboarding as done so updates
    // don't push them through the OOBE.
    private suspend fun migrateOnboardingFlag() {
        if (userPreferencesRepository.onboardingDone.first()) return
        val hasExistingData = withContext(Dispatchers.IO) {
            vaultPasswordManager.hasPassword() ||
                    getAccountsUseCase().first().isNotEmpty()
        }
        if (hasExistingData) {
            userPreferencesRepository.setOnboardingDone(true)
        }
    }

    private fun requestBiometric() {
        lifecycleScope.launch {
            val biometricEnabled = userPreferencesRepository.biometricEnabled.first()
            if (!biometricEnabled || !biometricHelper.isAvailable()) {
                lockManager.lock()
                return@launch
            }
            lockManager.lock()
            biometricHelper.authenticate(
                activity = this@MainActivity,
                title = getString(R.string.biometric_prompt_title),
                subtitle = getString(R.string.biometric_prompt_subtitle),
                onSuccess = { lockManager.unlock() },
                onError = { _, _ -> },
                onFailed = { },
            )
        }
    }
}
