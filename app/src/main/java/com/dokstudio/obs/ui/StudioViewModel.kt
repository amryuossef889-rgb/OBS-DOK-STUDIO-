package com.dokstudio.obs.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dokstudio.obs.DokStudioApplication
import com.dokstudio.obs.data.*
import com.dokstudio.obs.engine.StudioEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
class StudioViewModel(app:Application):AndroidViewModel(app){
 private val a=app as DokStudioApplication
 val engine=StudioEngine(app)
 val scenes=a.repository.sceneFlow.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val selectedScene=MutableStateFlow<String?>(null)
 init{viewModelScope.launch{if(scenes.first().isEmpty())a.repository.save(SceneEntity(UUID.randomUUID().toString(),"Main Scene",0,System.currentTimeMillis(),System.currentTimeMillis()))}}
 fun addScene(){viewModelScope.launch{val n=scenes.value.size;a.repository.save(SceneEntity(UUID.randomUUID().toString(),"Scene "+(n+1),n,System.currentTimeMillis(),System.currentTimeMillis()))}}
 fun addSource(scene:String,type:String){viewModelScope.launch{a.repository.save(SourceEntity(UUID.randomUUID().toString(),scene,type,type.replaceFirstChar{it.uppercase()},zIndex=System.currentTimeMillis().toInt()))}}
}