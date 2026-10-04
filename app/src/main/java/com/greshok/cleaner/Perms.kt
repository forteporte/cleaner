package com.greshok.cleaner

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

object Perms {

    fun hasFileAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }

    /** Android 11+: экран «Доступ ко всем файлам». */
    fun openFileAccessSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${ctx.packageName}"),
        )
        if (!start(ctx, direct)) start(ctx, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(ctx: Context) {
        start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /** Системная страница «О приложении», там Хранилище → Очистить кеш. */
    fun openAppDetails(ctx: Context, pkg: String) {
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
    }

    /** Открывает «О приложении» для первого установленного пакета из списка. */
    fun openFirstInstalled(ctx: Context, packages: List<String>) {
        val pkg = packages.firstOrNull { isInstalled(ctx, it) }
        if (pkg == null) {
            Toast.makeText(ctx, "Это приложение не установлено", Toast.LENGTH_LONG).show()
        } else {
            openAppDetails(ctx, pkg)
        }
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Проверка на вирусы через Google Play Protect (встроен в телефоны с Google Play). */
    fun openVirusCheck(ctx: Context) {
        val playProtect = Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS")
        if (start(ctx, playProtect)) return
        // Запасной путь: открыть Play Маркет, там аватарка → Play Защита
        if (launchApp(ctx, "com.android.vending")) {
            Toast.makeText(ctx, "Нажми на свою аватарку вверху, потом «Play Защита»", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(ctx, "На этом телефоне нет проверки Google", Toast.LENGTH_LONG).show()
    }

    // Браузеры и приложения, которые чаще всего шлют «рекламу» уведомлениями
    private val KNOWN_BROWSERS = listOf(
        "com.mi.globalbrowser",        // Mi Браузер (Xiaomi)
        "com.android.browser",         // встроенный браузер Xiaomi и др.
        "com.android.chrome",
        "com.yandex.browser",
        "ru.yandex.searchplugin",      // приложение «Яндекс» с Алисой
        "com.opera.browser",
        "com.opera.mini.native",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.UCMobile.intl",
    )

    /** Все браузеры на телефоне: пакет → название. */
    @Suppress("DEPRECATION")
    fun installedBrowsers(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val fromSystem = runCatching {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
            pm.queryIntentActivities(web, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())

        return (KNOWN_BROWSERS + fromSystem)
            .distinct()
            .filter { it != ctx.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    if (!ai.enabled) null else pkg to pm.getApplicationLabel(ai).toString()
                }.getOrNull()
            }
    }

    /** Экран уведомлений конкретного приложения (там верхний переключатель выключает всё). */
    fun openNotificationSettings(ctx: Context, pkg: String) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        if (!start(ctx, intent)) openAppDetails(ctx, pkg)
    }

    fun launchApp(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return start(ctx, intent)
    }

    private fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
