package me.diamondforge.tokn.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private data class Dep(val name: String, val license: String, val url: String)

private val DEPENDENCIES = listOf(
    Dep("Jetpack Compose", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
    Dep("AndroidX Jetpack libraries", "Apache 2.0", "https://developer.android.com/jetpack"),
    Dep(
        "Material Components for Android",
        "Apache 2.0",
        "https://github.com/material-components/material-components-android"
    ),
    Dep("Material Icons", "Apache 2.0", "https://fonts.google.com/icons"),
    Dep("Hilt (Dagger)", "Apache 2.0", "https://dagger.dev/hilt/"),
    Dep("Kotlin Coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Dep("Coil", "Apache 2.0", "https://github.com/coil-kt/coil"),
    Dep(
        "SQLCipher for Android",
        "BSD-style (Zetetic)",
        "https://github.com/sqlcipher/sqlcipher-android"
    ),
    Dep("Reorderable", "Apache 2.0", "https://github.com/Calvin-LL/Reorderable"),
    Dep("Accompanist", "Apache 2.0", "https://github.com/google/accompanist"),
    Dep("Bouncy Castle", "MIT", "https://www.bouncycastle.org/"),
    Dep("ZXing", "Apache 2.0", "https://github.com/zxing/zxing"),
    Dep("Guava", "Apache 2.0", "https://github.com/google/guava"),
)

@Composable
fun ThirdPartyLicensesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SettingsScaffold(
        title = stringResource(R.string.third_party_title),
        onBack = onBack,
    ) {
        item {
            SettingsGroup(
                items = DEPENDENCIES.map { dep ->
                    { DepRow(dep = dep, onClick = { uriHandler.openUri(dep.url) }) }
                },
                dividerStartInset = 16.dp,
            )
        }
    }
}

@Composable
private fun DepRow(dep: Dep, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = dep.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = dep.license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
