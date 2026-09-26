package com.dokstudio.obs.capture
import android.media.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
class AudioCaptureManager(private val rate:Int=48000){private var r:AudioRecord?=null;private var job:Job?=null;private val running=AtomicBoolean(false)
 fun start(onPcm:(ByteArray,Long)->Unit,onLevel:(Float)->Unit,onError:(Throwable)->Unit){if(running.get())return;try{val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);r=AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,8192));r!!.startRecording();running.set(true);job=CoroutineScope(Dispatchers.IO).launch{val b=ByteArray(4096);var pts=0L;while(isActive&&running.get()){val n=r!!.read(b,0,b.size,AudioRecord.READ_BLOCKING);if(n>0){val x=b.copyOf(n);var sum=0.0;for(i in 0 until n step 2){val s=(x[i].toInt() or (x[i+1].toInt() shl 8)).toShort().toInt();sum+=s.toDouble()*s};onLevel((kotlin.math.sqrt(sum/(n/2))/32768.0).toFloat());onPcm(x,pts);pts+=n*1_000_000L/(rate*2)}}}}catch(t:Throwable){onError(t);stop()}}
 fun stop(){running.set(false);job?.cancel();job=null;try{r?.stop()}catch(_:Throwable){};r?.release();r=null}
}