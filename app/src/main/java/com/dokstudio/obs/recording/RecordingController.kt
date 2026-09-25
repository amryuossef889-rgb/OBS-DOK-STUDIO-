package com.dokstudio.obs.recording
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.dokstudio.obs.capture.AudioCaptureManager
import com.dokstudio.obs.capture.ScreenCaptureManager
class RecordingController(private val c:Context){private var r:MediaCodecRecorder?=null;private var s:ScreenCaptureManager?=null;private var a:AudioCaptureManager?=null;private val h=Handler(Looper.getMainLooper());private var loop:Runnable?=null
 fun start(code:Int,data:Intent,w:Int,hgt:Int,fps:Int,bitrate:Int,onStarted:()->Unit,onError:(Throwable)->Unit){try{val x=MediaCodecRecorder(c);val surface=x.start(MediaCodecRecorder.Config(w,hgt,fps,bitrate));s=ScreenCaptureManager(c);s!!.startDirect(code,data,surface,w,hgt,c.resources.displayMetrics.densityDpi){};a=AudioCaptureManager();a!!.start({pcm,pts->x.audio(pcm,pts)},{},{t->onError(t)});r=x;loop=object:Runnable{override fun run(){try{x.drain();h.postDelayed(this,10)}catch(t:Throwable){onError(t)}}};h.post(loop!!);onStarted()}catch(t:Throwable){onError(t);stop()}}
 fun stop(){loop?.let{h.removeCallbacks(it)};loop=null;s?.stop();a?.stop();r?.stop();r=null;s=null;a=null}
}