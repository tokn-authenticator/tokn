package me.diamondforge.tokn

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.net.toUri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import me.diamondforge.tokn.audit.AuditLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.data.preferences.ThemeMode
import me.diamondforge.tokn.data.preferences.UserPreferencesRepository
import me.diamondforge.tokn.domain.usecase.GetAccountsUseCase
import me.diamondforge.tokn.navigation.AppNavHost
import me.diamondforge.tokn.passwordreminder.PasswordReminderDialog
import me.diamondforge.tokn.passwordreminder.PasswordReminderViewModel
import me.diamondforge.tokn.rating.RatingPromptDialog
import me.diamondforge.tokn.rating.RatingPromptViewModel
import me.diamondforge.tokn.security.BiometricHelper
import me.diamondforge.tokn.security.LockManager
import me.diamondforge.tokn.security.vault.VaultManager
import me.diamondforge.tokn.security.vault.VaultSession
import me.diamondforge.tokn.security.vault.VaultState
import me.diamondforge.tokn.ui.RootOfTrustUpgradeDialog
import me.diamondforge.tokn.ui.theme.SimpleOTPTheme
import javax.inject.Inject
import kotlin.coroutines.resume

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

    @Inject
    lateinit var auditLogger: AuditLogger

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
            userPreferencesRepository.auditLoggingEnabled.collect(auditLogger::setEnabled)
        }
        lifecycleScope.launch {
            userPreferencesRepository.auditDisabledCategories.collect(auditLogger::setDisabledCategories)
        }
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
            seedReminderForExistingVault()
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

        lifecycleScope.launch {
            combine(
                userPreferencesRepository.onboardingDone,
                userPreferencesRepository.encryptionEnabled,
                userPreferencesRepository.biometricEnabled,
                vaultSession.state,
            ) { done, encryption, biometric, state ->
                done && encryption && biometric && state == VaultState.UNLOCKED
            }.distinctUntilChanged().collect { ready ->
                if (ready) enrollBiometricIfNeeded()
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
                val reminderViewModel: PasswordReminderViewModel = hiltViewModel()
                val reminderDue by reminderViewModel.shouldPrompt.collectAsStateWithLifecycle()
                val nextReminderDays by reminderViewModel.nextReminderDays.collectAsStateWithLifecycle()
                var reminderHandled by rememberSaveable { mutableStateOf(false) }
                // Re-arm so a later due period isn't suppressed by the earlier prompt's flag.
                LaunchedEffect(reminderDue) { if (reminderDue) reminderHandled = false }

                val ratingViewModel: RatingPromptViewModel = hiltViewModel()
                val ratingDue by ratingViewModel.shouldPrompt.collectAsStateWithLifecycle()
                var ratingHandled by rememberSaveable { mutableStateOf(false) }
                var launchRecorded by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!launchRecorded) {
                        ratingViewModel.recordLaunch()
                        launchRecorded = true
                    }
                }

                AppNavHost(
                    isLocked = isLocked,
                    onboardingDone = onboardingDone,
                    onUnlock = { requestBiometric() },
                    onUnlockWithPassword = { password ->
                        withContext(Dispatchers.IO) {
                            if (vaultManager.unlockWithPassword(password)) {
                                // Just typed the password; defer the reminder without escalating.
                                reminderViewModel.markSeen()
                                withContext(Dispatchers.Main) {
                                    lockManager.unlock()
                                }
                                true
                            } else {
                                false
                            }
                        }
                    },
                    hasVaultPassword = hasVaultPassword,
                    biometricEnabled = biometricEnabled,
                    onSetupBiometric = { setupBiometric() },
                    onAuthenticate = { confirmVaultAuth() },
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

                // Root-of-trust upgrade takes precedence (!upgradeDue) so the prompts don't stack.
                if (isLocked == false && onboardingDone == true && migrated && !upgradeDue &&
                    hasVaultPassword && reminderDue && !reminderHandled
                ) {
                    PasswordReminderDialog(
                        onVerify = { reminderViewModel.verify(it) },
                        onVerified = {
                            reminderViewModel.recordSuccess()
                            reminderHandled = true
                        },
                        onDismiss = { wrongAttempt ->
                            if (wrongAttempt) reminderViewModel.recordFailure()
                            else reminderViewModel.snooze()
                            reminderHandled = true
                        },
                        nextReminderDays = nextReminderDays,
                    )
                }

                val reminderShowing = hasVaultPassword && reminderDue && !reminderHandled
                if (isLocked == false && onboardingDone == true && migrated && !upgradeDue &&
                    !reminderShowing && ratingDue && !ratingHandled
                ) {
                    RatingPromptDialog(
                        onRate = {
                            openPlayStoreReview()
                            ratingViewModel.markHandled()
                            ratingHandled = true
                        },
                        onLater = {
                            ratingViewModel.snooze()
                            ratingHandled = true
                        },
                        onNever = {
                            ratingViewModel.markHandled()
                            ratingHandled = true
                        },
                    )
                }
            }
        }
    }

    private fun openPlayStoreReview() {
        lockManager.suppressNextForeground()
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()).apply {
            setPackage("com.android.vending")
            addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
            )
        }
        if (runCatching { startActivity(market) }.isFailure) {
            runCatching {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$packageName".toUri(),
                    ),
                )
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
                vaultSession.lock()
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

    // Pre-feature vaults have lastShownAt=0, which would fire the prompt instantly. Seed
    // once so the first reminder lands an interval out.
    private suspend fun seedReminderForExistingVault() {
        val hasPassword = withContext(Dispatchers.IO) { vaultManager.hasPasswordOrLegacy() }
        if (hasPassword && userPreferencesRepository.passwordReminderLastShownAt.first() == 0L) {
            userPreferencesRepository.setPasswordReminderLastShownAt(System.currentTimeMillis())
            userPreferencesRepository.setPasswordReminderStage(0)
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

    // Identity confirmation before a sensitive action (exporting a backup,
    // sending the vault to another device). The vault is already unlocked here,
    // so this only proves the fingerprint still maps to the vault key; it
    // deliberately does not touch the session or lock state. Returns false on
    // cancel/error so the caller can fall back to the vault password.
    private suspend fun confirmVaultAuth(): Boolean = suspendCancellableCoroutine { cont ->
        val cipher = runCatching { vaultManager.biometricDecryptCipher() }.getOrNull()
        if (cipher == null) {
            if (cont.isActive) cont.resume(false)
            return@suspendCancellableCoroutine
        }
        biometricHelper.authenticateForCrypto(
            activity = this,
            title = getString(R.string.vault_auth_title),
            subtitle = getString(R.string.vault_auth_subtitle),
            negativeButton = getString(android.R.string.cancel),
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            onSuccess = { if (cont.isActive) cont.resume(true) },
            onError = { _, _ -> if (cont.isActive) cont.resume(false) },
            onFailed = { },
        )
    }

    private suspend fun setupBiometric(): Boolean = suspendCancellableCoroutine { cont ->
        if (!biometricHelper.isBiometricEnrolled()) {
            if (cont.isActive) cont.resume(false)
            return@suspendCancellableCoroutine
        }
        val cipher = runCatching { vaultManager.biometricEncryptCipher() }.getOrNull()
        if (cipher == null) {
            if (cont.isActive) cont.resume(false)
            return@suspendCancellableCoroutine
        }
        biometricHelper.authenticateForCrypto(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            negativeButton = getString(android.R.string.cancel),
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            onSuccess = { authenticatedCipher ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { vaultManager.enableBiometric(authenticatedCipher) }
                    }
                    if (cont.isActive) cont.resume(true)
                }
            },
            onError = { _, _ -> if (cont.isActive) cont.resume(false) },
            onFailed = { },
        )
    }

    private fun enrollBiometricIfNeeded() {
        lifecycleScope.launch {
            if (!userPreferencesRepository.encryptionEnabled.first()) return@launch
            if (!userPreferencesRepository.biometricEnabled.first()) return@launch
            if (!vaultSession.isUnlocked) return@launch
            val alreadyProvisioned =
                withContext(Dispatchers.IO) { vaultManager.canBiometricUnlock() }
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
                onError = { _, _ ->
                    lifecycleScope.launch { userPreferencesRepository.setBiometricEnabled(false) }
                },
                onFailed = { },
            )
        }
    }
}
