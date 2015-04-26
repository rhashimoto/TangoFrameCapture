package com.shoestringresearch.tango.capture;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.shoestringresearch.tango.TangoActivity;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class MainActivity
   extends TangoActivity
   implements View.OnClickListener {
   private GLSurfaceView view_;
   private Renderer renderer_;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      final TangoConfig tangoConfig = getTango().getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
      tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
      tangoConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
      setTangoConfig(tangoConfig);

      // Make full screen.
      this.requestWindowFeature(Window.FEATURE_NO_TITLE);
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                           WindowManager.LayoutParams.FLAG_FULLSCREEN);

      // Set up OpenGL ES surface
      view_ = new GLSurfaceView(this);
      view_.setEGLContextClientVersion(2);
      view_.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR);
      view_.setRenderer(renderer_ = new Renderer(this));
      view_.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
      view_.setOnClickListener(this);
      setContentView(view_);
   }

   @Override
   public void onFrameAvailable(int i) {
      view_.requestRender();
   }

   @Override
   public void onClick(View v) {
      renderer_.saveFrame();
   }
}

