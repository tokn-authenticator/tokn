package me.diamondforge.tokn.rating

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface InstallInfoProvider {
    fun firstInstallTime(): Long
    fun isFromPlayStore(): Boolean
}

class AndroidInstallInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstallInfoProvider {

    override fun firstInstallTime(): Long =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(0L)

    override fun isFromPlayStore(): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
        return installer == PLAY_STORE_PACKAGE
    }

    private companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
