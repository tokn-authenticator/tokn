package me.diamondforge.tokn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.Lazy
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
import me.diamondforge.tokn.security.vault.VaultManager
import me.diamondforge.tokn.security.vault.VaultSession
import me.diamondforge.tokn.security.vault.VaultState
import me.diamondforge.tokn.ui.RootOfTrustUpgradeDialog
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
    lateinit var vaultManager: VaultManager

    @Inject
    lateinit var vaultSession: VaultSession

    // Lazy: eager injection would open the encrypted DB during onCreate, before
    // the vault is unlocked.
    @Inject
    lateinit var getAccountsUseCase: Lazy<GetAccountsUseCase>

    // Set to true once the pre-OOBE migration check has run.
    // The UI must not render the onboarding flow before this is true; otherwise
    // upgrade-from-old-version users see a brief flash of OOBE before being
    // bounced to the home screen.
    private val migrationComplete = MutableStateFlow(false)

    private val upgradeNeeded = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        lifecycleScope.launch {
            // Migrate to the slot-based vault and, when no password/biometric is
            // configured, transparently unlock from the no-auth keystore slot so
            // the database can be opened. Never generates a new master key when an
            // old passphrase exists, so existing data always stays accessible.
            withContext(Dispatchers.IO) {
                vaultManager.initIfNeeded()
                if (!vaultManager.requiresUnlock()) {
                    vaultManager.tryAutoUnlock()
                }
            }
            migrateOnboardingFlag()
            migrationComplete.value = true
        }

        lifecycleScope.launch {
            vaultSession.state.collect { state ->
                upgradeNeeded.value = if (state == VaultState.UNLOCKED) {
                    withContext(Dispatchers.IO) { vaultManager.needsRootOfTrustUpgrade() }
                } else {
                    false
                }
            }
        }

        setContent {
            val themeMode by userPreferencesRepository.themeMode.collectAsStateWithLifecycle(
                ThemeMode.SYSTEM
            )
            val dynamicColorEnabled by userPreferencesRepository.dynamicColorEnabled.collectAsStateWithLifecycle(
                true
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

            val hasVaultPassword = encryptionEnabled && vaultManager.hasPasswordOrLegacy()

            val upgradeDue by upgradeNeeded.collectAsStateWithLifecycle()
            var upgradeDismissed by rememberSaveable { mutableStateOf(false) }

            SimpleOTPTheme(themeMode = themeMode, dynamicColor = dynamicColorEnabled) {
                AppNavHost(
                    isLocked = isLocked,
                    onboardingDone = onboardingDone,
                    onUnlock = { requestBiometric() },
                    onUnlockWithPassword = { password ->
                        withContext(Dispatchers.IO) {
                            if (vaultManager.unlockWithPassword(password)) {
                                withContext(Dispatchers.Main) {
                                    lockManager.unlock()
                                    enrollBiometricIfNeeded()
                                }
                                true
                            } else {
                                false
                            }
                        }
                    },
                    hasVaultPassword = hasVaultPassword,
                    biometricEnabled = biometricEnabled,
                )

                if (isLocked == false && onboardingDone == true && upgradeDue && !upgradeDismissed) {
                    RootOfTrustUpgradeDialog(
                        onConfirm = { password ->
                            withContext(Dispatchers.IO) {
                                vaultManager.unlockWithPassword(password)
                            }.also { ok -> if (ok) upgradeNeeded.value = false }
                        },
                        onDismiss = { upgradeDismissed = true },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { vaultManager.initIfNeeded() }
            val onboardingDone = userPreferencesRepository.onboardingDone.first()
            if (!onboardingDone) {
                withContext(Dispatchers.IO) { vaultManager.tryAutoUnlock() }
                lockManager.unlock()
                return@launch
            }
            val encryptionEnabled = userPreferencesRepository.encryptionEnabled.first()
            if (!encryptionEnabled) {
                withContext(Dispatchers.IO) { vaultManager.tryAutoUnlock() }
                lockManager.unlock()
                return@launch
            }
            val timeout = userPreferencesRepository.autoLockTimeoutSeconds.first()
            lockManager.onAppForeground(timeout)
            if (!vaultSession.isUnlocked || lockManager.isLocked.value != false) {
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
            vaultManager.hasPasswordOrLegacy() ||
                    (vaultSession.isUnlocked && getAccountsUseCase.get()().first().isNotEmpty())
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
            val canUnlock = withContext(Dispatchers.IO) { vaultManager.canBiometricUnlock() }
            if (canUnlock) {
                unlockWithBiometric()
                return@launch
            }
            // Migrated biometric users without a slot yet: provision and unlock
            // from one fingerprint prompt, no password needed.
            val canProvision = withContext(Dispatchers.IO) {
                vaultManager.canProvisionBiometricFromKeystore()
            }
            if (canProvision && biometricHelper.isBiometricEnrolled()) {
                provisionBiometricAndUnlock()
                return@launch
            }
            lockManager.lock()
        }
    }

    private suspend fun unlockWithBiometric() {
        lockManager.lock()
        val cipher = withContext(Dispatchers.IO) { vaultManager.biometricDecryptCipher() }
        if (cipher == null) {
            // Biometric key invalidated (e.g. new fingerprint) — fall back to password.
            return
        }
        biometricHelper.authenticateForCrypto(
            activity = this@MainActivity,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButton = getString(android.R.string.cancel),
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            onSuccess = { authenticatedCipher ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        vaultManager.finishBiometricUnlock(authenticatedCipher)
                    }
                    if (ok) lockManager.unlock()
                }
            },
            onError = { _, _ -> },
            onFailed = { },
        )
    }

    private suspend fun provisionBiometricAndUnlock() {
        lockManager.lock()
        val cipher = withContext(Dispatchers.IO) {
            runCatching { vaultManager.biometricEncryptCipher() }.getOrNull()
        }
        if (cipher == null) {
            return
        }
        biometricHelper.authenticateForCrypto(
            activity = this@MainActivity,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButton = getString(android.R.string.cancel),
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            onSuccess = { authenticatedCipher ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            vaultManager.provisionAndUnlockBiometric(authenticatedCipher)
                        }.getOrDefault(false)
                    }
                    if (ok) lockManager.unlock()
                }
            },
            onError = { _, _ -> },
            onFailed = { },
        )
    }

    private fun enrollBiometricIfNeeded() {
        lifecycleScope.launch {
            if (!userPreferencesRepository.biometricEnabled.first()) return@launch
            if (!vaultSession.isUnlocked) return@launch
            val alreadyProvisioned = withContext(Dispatchers.IO) { vaultManager.canBiometricUnlock() }
            if (alreadyProvisioned) return@launch
            if (!biometricHelper.isBiometricEnrolled()) return@launch

            val cipher = withContext(Dispatchers.IO) {
                runCatching { vaultManager.biometricEncryptCipher() }.getOrNull()
            } ?: return@launch

            biometricHelper.authenticateForCrypto(
                activity = this@MainActivity,
                title = getString(R.string.biometric_prompt_title),
                subtitle = getString(R.string.biometric_prompt_subtitle),
                negativeButton = getString(android.R.string.cancel),
                cryptoObject = BiometricPrompt.CryptoObject(cipher),
                onSuccess = { authenticatedCipher ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching { vaultManager.enableBiometric(authenticatedCipher) }
                    }
                },
                onError = { _, _ -> },
                onFailed = { },
            )
        }
    }
}
