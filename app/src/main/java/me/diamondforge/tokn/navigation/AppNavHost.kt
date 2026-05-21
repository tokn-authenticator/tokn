package me.diamondforge.tokn.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import me.diamondforge.tokn.R
import me.diamondforge.tokn.add.AddAccountViewModel
import me.diamondforge.tokn.add.FromImageScreen
import me.diamondforge.tokn.add.ManualEntryScreen
import me.diamondforge.tokn.add.QrScannerScreen
import me.diamondforge.tokn.backup.BackupScreen
import me.diamondforge.tokn.backup.qr.MigrationScanScreen
import me.diamondforge.tokn.home.EditAccountScreen
import me.diamondforge.tokn.home.HomeScreen
import me.diamondforge.tokn.onboarding.OnboardingScreen
import me.diamondforge.tokn.settings.AboutScreen
import me.diamondforge.tokn.settings.AppearanceScreen
import me.diamondforge.tokn.settings.SecurityScreen
import me.diamondforge.tokn.settings.SettingsScreen
import me.diamondforge.tokn.settings.ThirdPartyLicensesScreen
import me.diamondforge.tokn.data.preferences.SyncMethod
import me.diamondforge.tokn.sync.ui.ChooseMethodScreen
import me.diamondforge.tokn.sync.ui.LanReceiveScreen
import me.diamondforge.tokn.sync.ui.LanSendScreen
import me.diamondforge.tokn.sync.ui.QrReceiveScreen
import me.diamondforge.tokn.sync.ui.QrSendScreen
import me.diamondforge.tokn.sync.ui.ReceiveViewModel
import me.diamondforge.tokn.sync.ui.SelectAccountsScreen
import me.diamondforge.tokn.sync.ui.SendViewModel
import me.diamondforge.tokn.sync.ui.SyncEntryScreen
import me.diamondforge.tokn.sync.ui.WfdReceiveScreen
import me.diamondforge.tokn.sync.ui.WfdSendScreen

@Composable
fun AppNavHost(
    isLocked: Boolean?,
    onboardingDone: Boolean?,
    onUnlock: () -> Unit,
    onUnlockWithPassword: suspend (String) -> Boolean,
    hasVaultPassword: Boolean,
    biometricEnabled: Boolean,
) {
    if (onboardingDone == null || isLocked == null) return
    if (!onboardingDone) {
        OnboardingScreen(onFinished = {})
        return
    }
    if (isLocked) {
        LockScreen(
            onUnlock = onUnlock,
            onUnlockWithPassword = onUnlockWithPassword,
            hasVaultPassword = hasVaultPassword,
            biometricEnabled = biometricEnabled,
        )
        return
    }

    val navController = rememberNavController()

    val navAnim = tween<Float>(durationMillis = 250)
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enterTransition = { fadeIn(animationSpec = navAnim) },
        exitTransition = { fadeOut(animationSpec = navAnim) },
        popEnterTransition = { fadeIn(animationSpec = navAnim) },
        popExitTransition = { fadeOut(animationSpec = navAnim) },
    ) {
        composable(Screen.Home.route) { entry ->
            HomeScreen(
                onScanQr = { entry.navigateOnce(navController, Screen.AddFlow.route) },
                onFromImage = { entry.navigateOnce(navController, Screen.FromImage.route) },
                onManualEntry = { entry.navigateOnce(navController, Screen.ManualEntry.route) },
                onSettings = { entry.navigateOnce(navController, Screen.Settings.route) },
                onAbout = { entry.navigateOnce(navController, Screen.About.route) },
                onBackup = { entry.navigateOnce(navController, Screen.Backup.route) },
                onEditAccount = { id -> entry.navigateOnce(navController, Screen.EditAccount.createRoute(id)) },
            )
        }
        composable(
            route = Screen.EditAccount.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
        ) {
            EditAccountScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        navigation(
            startDestination = Screen.QrScanner.route,
            route = Screen.AddFlow.route,
        ) {
            composable(Screen.QrScanner.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Screen.AddFlow.route)
                }
                val viewModel: AddAccountViewModel = hiltViewModel(parentEntry)
                QrScannerScreen(
                    onScanned = { rawValue ->
                        viewModel.onQrScanned(rawValue)
                        entry.navigateOnce(navController, Screen.ManualEntry.route)
                    },
                    onManualEntry = { entry.navigateOnce(navController, Screen.ManualEntry.route) },
                    onBack = { navController.popBackStack(Screen.Home.route, inclusive = false) },
                )
            }
            composable(Screen.FromImage.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Screen.AddFlow.route)
                }
                val viewModel: AddAccountViewModel = hiltViewModel(parentEntry)
                FromImageScreen(
                    onScanned = { rawValue ->
                        viewModel.onQrScanned(rawValue)
                        entry.navigateOnce(navController, Screen.ManualEntry.route)
                    },
                    onManualEntry = { entry.navigateOnce(navController, Screen.ManualEntry.route) },
                    onBack = { navController.popBackStack(Screen.Home.route, inclusive = false) },
                    suppressLock = viewModel::suppressLock,
                )
            }
            composable(Screen.ManualEntry.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Screen.AddFlow.route)
                }
                val viewModel: AddAccountViewModel = hiltViewModel(parentEntry)
                ManualEntryScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    },
                    viewModel = viewModel,
                )
            }
        }

        composable(Screen.Settings.route) { entry ->
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAppearance = { entry.navigateOnce(navController, Screen.Appearance.route) },
                onSecurity = { entry.navigateOnce(navController, Screen.SecuritySettings.route) },
                onBackup = { entry.navigateOnce(navController, Screen.Backup.route) },
                onSync = { entry.navigateOnce(navController, Screen.Sync.route) },
            )
        }
        composable(Screen.Sync.route) { entry ->
            SyncEntryScreen(
                onBack = { navController.popBackStack() },
                onSend = { entry.navigateOnce(navController, Screen.SyncSendFlow.route) },
                onReceive = { entry.navigateOnce(navController, Screen.SyncReceiveFlow.route) },
            )
        }
        navigation(
            startDestination = Screen.SyncSelect.route,
            route = Screen.SyncSendFlow.route,
        ) {
            composable(Screen.SyncSelect.route) { entry ->
                val sendVm = entry.sharedSendViewModel(navController)
                SelectAccountsScreen(
                    viewModel = sendVm,
                    onBack = { navController.popBackStack() },
                    onContinue = { entry.navigateOnce(navController, Screen.SyncSendChoose.route) },
                )
            }
            composable(Screen.SyncSendChoose.route) { entry ->
                ChooseMethodScreen(
                    isSender = true,
                    onBack = { navController.popBackStack() },
                    onContinue = { method ->
                        entry.navigateOnce(navController, Screen.syncSendRoute(method))
                    },
                )
            }
            composable(Screen.SyncSendLan.route) { entry ->
                LanSendScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = entry.sharedSendViewModel(navController),
                )
            }
            composable(Screen.SyncSendWfd.route) { entry ->
                WfdSendScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = entry.sharedSendViewModel(navController),
                )
            }
            composable(Screen.SyncSendQr.route) { entry ->
                QrSendScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = entry.sharedSendViewModel(navController),
                )
            }
        }
        navigation(
            startDestination = Screen.SyncReceiveChoose.route,
            route = Screen.SyncReceiveFlow.route,
        ) {
            composable(Screen.SyncReceiveChoose.route) { entry ->
                ChooseMethodScreen(
                    isSender = false,
                    onBack = { navController.popBackStack() },
                    onContinue = { method ->
                        entry.navigateOnce(navController, Screen.syncReceiveRoute(method))
                    },
                )
            }
            composable(Screen.SyncReceiveLan.route) { entry ->
                LanReceiveScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = entry.sharedReceiveViewModel(navController),
                )
            }
            composable(Screen.SyncReceiveWfd.route) { entry ->
                WfdReceiveScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = entry.sharedReceiveViewModel(navController),
                )
            }
            composable(Screen.SyncReceiveQr.route) { entry ->
                QrReceiveScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = entry.sharedReceiveViewModel(navController),
                )
            }
        }
        composable(Screen.Appearance.route) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SecuritySettings.route) {
            SecurityScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.About.route) { entry ->
            AboutScreen(
                onBack = { navController.popBackStack() },
                onThirdPartyLicenses = {
                    entry.navigateOnce(navController, Screen.ThirdPartyLicenses.route)
                },
            )
        }
        composable(Screen.ThirdPartyLicenses.route) {
            ThirdPartyLicensesScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Backup.route) { entry ->
            BackupScreen(
                onBack = { navController.popBackStack() },
                onScanMigration = { entry.navigateOnce(navController, Screen.MigrationScan.route) },
            )
        }
        composable(Screen.MigrationScan.route) {
            MigrationScanScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun LockScreen(
    onUnlock: () -> Unit,
    onUnlockWithPassword: suspend (String) -> Boolean,
    hasVaultPassword: Boolean,
    biometricEnabled: Boolean,
) {
    val scope = rememberCoroutineScope()
    var showPasswordField by remember { mutableStateOf(!biometricEnabled) }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var wrongPassword by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Tokn",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vault_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (showPasswordField) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        wrongPassword = false
                    },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.wrong_password)) }
                    } else null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isVerifying = true
                            val ok = onUnlockWithPassword(password)
                            isVerifying = false
                            if (!ok) {
                                wrongPassword = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.isNotEmpty() && !isVerifying,
                ) {
                    Text(stringResource(R.string.unlock))
                }
                if (biometricEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        showPasswordField = false
                        password = ""
                        wrongPassword = false
                    }) {
                        Text(stringResource(R.string.use_biometrics))
                    }
                }
            } else {
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.unlock))
                }
                if (hasVaultPassword) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showPasswordField = true }) {
                        Text(stringResource(R.string.use_password))
                    }
                }
            }
        }
    }
}

private fun NavBackStackEntry.navigateOnce(navController: NavController, route: String) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        navController.navigate(route)
    }
}

@Composable
private fun NavBackStackEntry.sharedSendViewModel(navController: NavController): SendViewModel {
    val parent = remember(this) { navController.getBackStackEntry(Screen.SyncSendFlow.route) }
    return hiltViewModel(parent)
}

@Composable
private fun NavBackStackEntry.sharedReceiveViewModel(navController: NavController): ReceiveViewModel {
    val parent = remember(this) { navController.getBackStackEntry(Screen.SyncReceiveFlow.route) }
    return hiltViewModel(parent)
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddFlow : Screen("add_flow")
    data object QrScanner : Screen("qr_scanner")
    data object FromImage : Screen("from_image")
    data object ManualEntry : Screen("manual_entry")
    data object Settings : Screen("settings")
    data object Appearance : Screen("appearance")
    data object SecuritySettings : Screen("security_settings")
    data object About : Screen("about")
    data object ThirdPartyLicenses : Screen("about/third_party")
    data object Backup : Screen("backup")
    data object MigrationScan : Screen("backup/migration_scan")
    data object Sync : Screen("sync")
    data object SyncSendFlow : Screen("sync/send_flow")
    data object SyncSelect : Screen("sync/select")
    data object SyncSendChoose : Screen("sync/send/choose")
    data object SyncSendLan : Screen("sync/send/lan")
    data object SyncSendWfd : Screen("sync/send/wfd")
    data object SyncSendQr : Screen("sync/send/qr")
    data object SyncReceiveFlow : Screen("sync/receive_flow")
    data object SyncReceiveChoose : Screen("sync/receive/choose")
    data object SyncReceiveLan : Screen("sync/receive/lan")
    data object SyncReceiveWfd : Screen("sync/receive/wfd")
    data object SyncReceiveQr : Screen("sync/receive/qr")

    companion object {
        fun syncSendRoute(method: SyncMethod) = when (method) {
            SyncMethod.LAN -> SyncSendLan.route
            SyncMethod.WFD -> SyncSendWfd.route
            SyncMethod.QR -> SyncSendQr.route
        }

        fun syncReceiveRoute(method: SyncMethod) = when (method) {
            SyncMethod.LAN -> SyncReceiveLan.route
            SyncMethod.WFD -> SyncReceiveWfd.route
            SyncMethod.QR -> SyncReceiveQr.route
        }
    }
    data object EditAccount : Screen("edit/{accountId}") {
        fun createRoute(accountId: Long) = "edit/$accountId"
    }
}
