package com.dokstudio.obs.permissions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionCoordinator(private val activity: Activity) {
    fun missingCapturePermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        return permissions.filterNot(::isGranted)
    }

    fun missingCameraPermission(): Boolean =
        !isGranted(Manifest.permission.CAMERA)

    fun requestCapturePermissions() {
        val missing = missingCapturePermissions()
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missing.toTypedArray(),
                REQUEST_CAPTURE_PERMISSIONS,
            )
        }
    }

    fun capturePermissionsGranted(): Boolean = missingCapturePermissions().isEmpty()

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val REQUEST_CAPTURE_PERMISSIONS = 900
    }
}
