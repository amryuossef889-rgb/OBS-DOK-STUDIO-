package com.dokstudio.obs.capture
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
class ScreenCaptureManager(private val c:Context){private var p:MediaProjection?=null;private var d:VirtualDisplay?=null
 fun startDirect(code:Int,data:Intent,surface:Surface,w:Int,h:Int,dpi:Int,onStopped:()->Unit){val m=c.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;p=m.getMediaProjection(code,data);p?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stop();onStopped()}},android.os.Handler(c.mainLooper));d=p?.createVirtualDisplay("OBS-Dok",w,h,dpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,null)}
 fun stop(){d?.release();d=null;p?.stop();p=null}
}