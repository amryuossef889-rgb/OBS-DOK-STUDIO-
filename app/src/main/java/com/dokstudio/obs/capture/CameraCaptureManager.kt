package com.dokstudio.obs.capture
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
class CameraCaptureManager(private val c:Context,private val owner:LifecycleOwner){private var s:Surface?=null
 fun start(target:SurfaceTexture,w:Int,h:Int,onError:(Throwable)->Unit){s=Surface(target);val f=ProcessCameraProvider.getInstance(c);f.addListener({try{val p=f.get();p.unbindAll();val preview=Preview.Builder().setTargetResolution(android.util.Size(w,h)).build();preview.setSurfaceProvider{q->q.provideSurface(s!!,ContextCompat.getMainExecutor(c)){} };p.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview)}catch(t:Throwable){onError(t)}},ContextCompat.getMainExecutor(c))}
 fun stop(){try{ProcessCameraProvider.getInstance(c).get().unbindAll()}catch(_:Throwable){};s?.release();s=null}
}