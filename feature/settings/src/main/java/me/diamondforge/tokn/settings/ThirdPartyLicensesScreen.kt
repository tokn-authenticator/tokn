package me.diamondforge.tokn.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirdPartyLicensesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.third_party_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column {
                        DEPENDENCIES.forEach { dep ->
                            DepRow(dep = dep, onClick = { uriHandler.openUri(dep.url) })
                        }
                    }
                }
            }
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
