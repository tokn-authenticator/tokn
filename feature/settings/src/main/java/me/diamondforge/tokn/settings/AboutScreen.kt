package me.diamondforge.tokn.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/fthomys/tokn"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"
private const val MAINTAINER_URL = "https://github.com/fthomys"
private const val PLAY_STORE_INSTALLER = "com.android.vending"

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AboutSection(title = stringResource(R.string.about_section_app)) {
                    AppHeaderRow()
                    AboutRow(
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
                                                text
                                            ),
                                        ),
                                    )
                                    snackbarHostState.showSnackbar(versionCopiedMsg)
                                }
                            }
                        } else null,
                    )
                    AboutRow(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.about_changelog),
                        subtitle = stringResource(R.string.about_changelog_desc),
                        onClick = { uriHandler.openUri(RELEASES_URL) },
                    )
                    AboutRow(
                        icon = Icons.Outlined.Code,
                        title = stringResource(R.string.about_source),
                        subtitle = stringResource(R.string.about_source_desc),
                        onClick = { uriHandler.openUri(REPO_URL) },
                    )
                    AboutRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.about_license),
                        subtitle = stringResource(R.string.about_license_desc),
                        onClick = { uriHandler.openUri(LICENSE_URL) },
                    )
                    AboutRow(
                        icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                        title = stringResource(R.string.about_third_party),
                        subtitle = stringResource(R.string.about_third_party_desc),
                        onClick = onThirdPartyLicenses,
                    )
                    AboutRow(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.about_report_issue),
                        subtitle = stringResource(R.string.about_report_issue_desc),
                        onClick = { uriHandler.openUri(ISSUES_URL) },
                    )
                }
            }
            item {
                AboutSection(title = stringResource(R.string.about_section_project)) {
                    AboutRow(
                        icon = Icons.Outlined.Person,
                        title = stringResource(R.string.about_maintainer),
                        subtitle = stringResource(R.string.about_maintainer_desc),
                        onClick = { uriHandler.openUri(MAINTAINER_URL) },
                    )
                }
            }
            if (showRate) {
                item {
                    AboutSection(title = stringResource(R.string.about_section_support)) {
                        AboutRow(
                            icon = Icons.Outlined.StarRate,
                            title = stringResource(R.string.about_rate),
                            subtitle = stringResource(R.string.about_rate_desc),
                            onClick = { openPlayStoreListing(context) },
                        )
                    }
                }
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
private fun AboutSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun AppHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
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

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
