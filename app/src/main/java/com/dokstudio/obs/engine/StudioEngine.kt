package com.dokstudio.obs.engine
import android.content.Context
import kotlinx.coroutines.flow.*
enum class StudioState{IDLE,PREPARING,READY,RECORDING,STOPPING}
enum class StreamState{OFFLINE,CONNECTING,LIVE,RECONNECTING,STOPPING}
data class PerformanceSnapshot(val renderFps:Float=0f,val encoderFps:Float=0f,val droppedFrames:Long=0,val queueDepth:Int=0,val audioUnderruns:Long=0,val encodeLatencyMs:Long=0,val memoryMb:Long=0,val thermal:String="unknown",val networkKbps:Int=0)
class StudioEngine(private val context:Context){private val _studio=MutableStateFlow(StudioState.IDLE);val studio:StateFlow<StudioState> = _studio.asStateFlow();private val _stream=MutableStateFlow(StreamState.OFFLINE);val stream:StateFlow<StreamState> = _stream.asStateFlow();val metrics:StateFlow<PerformanceSnapshot> = MutableStateFlow(PerformanceSnapshot()).asStateFlow()
 fun prepare(){check(_studio.value==StudioState.IDLE);_studio.value=StudioState.PREPARING}
 fun ready(){check(_studio.value==StudioState.PREPARING);_studio.value=StudioState.READY}
 fun markRecording(){check(_studio.value==StudioState.READY);_studio.value=StudioState.RECORDING}
 fun stopping(){check(_studio.value==StudioState.RECORDING);_studio.value=StudioState.STOPPING}
 fun idle(){_studio.value=StudioState.IDLE}
 fun setStream(s:StreamState){_stream.value=s}
}