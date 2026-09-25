package com.dokstudio.obs.permissions
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
class PermissionCoordinator(private val a:Activity){fun missing():List<String>{val p=mutableListOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=33)p+=Manifest.permission.POST_NOTIFICATIONS;return p.filter{ContextCompat.checkSelfPermission(a,it)!=PackageManager.PERMISSION_GRANTED}};fun request(){val p=missing();if(p.isNotEmpty())ActivityCompat.requestPermissions(a,p.toTypedArray(),900)};fun granted()=missing().isEmpty()}