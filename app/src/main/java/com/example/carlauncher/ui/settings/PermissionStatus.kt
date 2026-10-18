package com.example.carlauncher.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.service.MediaListenerService

enum class PermissionId { NOTIFICATIONS, LOCATION, CALENDAR, CAMERA, HOME_LAUNCHER }

/** One row of the Settings → Oprávnění section. Plain data — icons are mapped by [id] in the UI. */
data class PermissionItem(
    val id: PermissionId,
    val title: String,
    val description: String,
    val granted: Boolean,
    val settingsIntent: Intent,
)

/**
 * Current state of everything the launcher needs from the system. Cheap synchronous
 * queries — safe to call on the main thread on every ON_RESUME.
 */
fun permissionItems(context: Context): List<PermissionItem> = buildList {
    add(
        PermissionItem(
            id = PermissionId.NOTIFICATIONS,
            title = "Přístup k oznámením",
            description = "Hudba a navigace z notifikací",
            granted = NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName),
            settingsIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, MediaListenerService::class.java).flattenToString()
                ),
        )
    )
    add(
        PermissionItem(
            id = PermissionId.LOCATION,
            title = "Poloha",
            description = "Mapa, GPS a počasí",
            granted = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION),
            settingsIntent = appDetailsIntent(context),
        )
    )
    add(
        PermissionItem(
            id = PermissionId.CALENDAR,
            title = "Kalendář",
            description = "Dnešní události ve widgetu",
            granted = isGranted(context, Manifest.permission.READ_CALENDAR),
            settingsIntent = appDetailsIntent(context),
        )
    )
    if (BuildConfig.INCIDENT_RECORDER_ENABLED) {
        add(
            PermissionItem(
                id = PermissionId.CAMERA,
                title = "Kamera",
                description = "Incident Recorder",
                granted = isGranted(context, Manifest.permission.CAMERA),
                settingsIntent = appDetailsIntent(context),
            )
        )
    }
    add(
        PermissionItem(
            id = PermissionId.HOME_LAUNCHER,
            title = "Výchozí launcher",
            description = "CarLauncher jako domovská obrazovka",
            granted = context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_HOME) == true,
            settingsIntent = Intent(Settings.ACTION_HOME_SETTINGS),
        )
    )
}

/**
 * Opens [intent]; if the device has no such settings screen, falls back to this app's
 * details page, which exists everywhere. Runtime permissions deliberately go through the
 * details page too — after two denials Android stops showing the request dialog.
 */
fun openSystemSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun appDetailsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
