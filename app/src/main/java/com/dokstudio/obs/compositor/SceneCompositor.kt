package com.dokstudio.obs.compositor
import android.graphics.SurfaceTexture
import android.opengl.*
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
class SceneCompositor{data class Layer(val texture:SurfaceTexture,val textureId:Int,val alpha:Float=1f,val z:Int=0);private val layers=mutableListOf<Layer>();private var d=EGL14.EGL_NO_DISPLAY;private var ctx=EGL14.EGL_NO_CONTEXT;private var cfg:EGLConfig?=null;private var program=0;private val v:FloatBuffer=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)).position(0)}
 fun initialize(){d=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);val x=IntArray(2);EGL14.eglInitialize(d,x,0,x,1);val a=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE);val c=arrayOfNulls<EGLConfig>(1);val n=IntArray(1);EGL14.eglChooseConfig(d,a,0,c,0,1,n,0);cfg=c[0];ctx=EGL14.eglCreateContext(d,cfg,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0);program=GLES20.glCreateProgram()}
 fun attach(l:Layer){layers.removeAll{it.texture===l.texture};layers.add(l)}
 fun render(surface:Surface){if(d==EGL14.EGL_NO_DISPLAY)initialize();val s=EGL14.eglCreateWindowSurface(d,cfg,surface,intArrayOf(EGL14.EGL_NONE),0);EGL14.eglMakeCurrent(d,s,s,ctx);GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);EGL14.eglSwapBuffers(d,s);EGL14.eglDestroySurface(d,s)}
 fun release(){if(d!=EGL14.EGL_NO_DISPLAY){EGL14.eglDestroyContext(d,ctx);EGL14.eglTerminate(d)}}
}