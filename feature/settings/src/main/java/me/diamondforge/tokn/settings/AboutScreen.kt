package me.diamondforge.tokn.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch

private const val HOMEPAGE_URL = "https://usetokn.app"
private const val REPO_URL = "https://github.com/fthomys/tokn"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"
private const val MAINTAINER_URL = "https://github.com/fthomys"
private const val PLAY_STORE_INSTALLER = "com.android.vending"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onThirdPartyLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val showRate = remember(context) { isPlayStoreInstall(context) }
    val versionCopiedMsg = stringResource(R.string.about_version_copied)

    SettingsScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        item { SettingsSectionHeader(stringResource(R.string.about_section_app)) }
        item {
            SettingsGroup(
                items = buildList<@Composable () -> Unit> {
                    add { AppHeaderRow() }
                    add {
                        SettingsRow(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.about_version),
                            subtitle = versionName.ifEmpty { "-" },
                            onClick = if (versionName.isNotEmpty()) {
                                {
                                    scope.launch {
                                        val text = "Tokn $versionName"
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                android.content.ClipData.newPlainText(
                                                    "Tokn version",
                                                    text,
                                                ),
                                            ),
                                        )
                                        snackbarHostState.showSnackbar(versionCopiedMsg)
                                    }
                                }
                            } else null,
                        )
                    }
                    add {
                        SettingsRow(
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.about_changelog),
                            subtitle = stringResource(R.string.about_changelog_desc),
                            onClick = { uriHandler.openUri(RELEASES_URL) },
                        )
                    }
                    add {
                        SettingsRow(
                            icon = Icons.Outlined.Language,
                            title = stringResource(R.string.about_homepage),
                            subtitle = stringResource(R.string.about_homepage_desc),
                            onClick = { uriHandler.openUri(HOMEPAGE_URL) },
                        )
                    }
                    add {
                        SettingsRow(
                            icon = Icons.Outlined.Code,
                            title = stringResource(R.string.about_source),
                            subtitle = stringResource(R.string.about_source_desc),
                            onClick = { uriHandler.openUri(REPO_URL) },
                        )
                    }
                    add {
                        SettingsRow(
                            icon = Icons.Outlined.Description,
                            title = stringResource(R.string.about_license),
                            subtitle = stringResource(R.string.about_license_desc),
                            onClick = { uriHandler.openUri(LICENSE_URL) },
                        )
                    }
                    add {
                        SettingsRow(
                            icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                            title = stringResource(R.string.about_third_party),
                            subtitle = stringResource(R.string.about_third_party_desc),
                            onClick = onThirdPartyLicenses,
                        )
                    }
                    add {
                        SettingsRow(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.about_report_issue),
                            subtitle = stringResource(R.string.about_report_issue_desc),
                            onClick = { uriHandler.openUri(ISSUES_URL) },
                        )
                    }
                },
            )
        }

        item { SettingsSectionHeader(stringResource(R.string.about_section_project)) }
        item {
            SettingsGroup(
                items = listOf(
                    {
                        SettingsRow(
                            icon = Icons.Outlined.Person,
                            title = stringResource(R.string.about_maintainer),
                            subtitle = stringResource(R.string.about_maintainer_desc),
                            onClick = { uriHandler.openUri(MAINTAINER_URL) },
                        )
                    },
                ),
            )
        }

        if (showRate) {
            item { SettingsSectionHeader(stringResource(R.string.about_section_support)) }
            item {
                SettingsGroup(
                    items = listOf(
                        {
                            SettingsRow(
                                icon = Icons.Outlined.StarRate,
                                title = stringResource(R.string.about_rate),
                                subtitle = stringResource(R.string.about_rate_desc),
                                onClick = { openPlayStoreListing(context) },
                            )
                        },
                    ),
                )
            }
        }
    }
}

private fun isPlayStoreInstall(context: Context): Boolean {
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrNull()
    } else {
        @Suppress("DEPRECATION")
        runCatching {
            context.packageManager.getInstallerPackageName(context.packageName)
        }.getOrNull()
    }
    return installer == PLAY_STORE_INSTALLER
}

private fun openPlayStoreListing(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(web)
    }
}

@Composable
private fun AppHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Tokn",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.about_app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
