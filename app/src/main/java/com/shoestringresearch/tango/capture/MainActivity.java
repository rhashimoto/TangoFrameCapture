// Copyright 2015 Shoestring Research, LLC.  All rights reserved.
package com.shoestringresearch.tango.capture;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity
   implements View.OnClickListener, Tango.OnTangoUpdateListener {
   private Tango tango_;
   private TangoConfig tangoConfig_;
   private volatile boolean tangoConnected_ = false;

   HashMap<Integer, Integer> cameraTextures_;

   private GLSurfaceView view_;
   private Renderer renderer_;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      // Request depth in the Tango config because otherwise frames
      // are not delivered.
      tango_ = new Tango(this);
      tangoConfig_ = tango_.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
      tangoConfig_.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
      cameraTextures_ = new HashMap<>();

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
   protected void onResume() {
      super.onResume();
      if (!tangoConnected_) {
         startActivityForResult(
            Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING),
            Tango.TANGO_INTENT_ACTIVITYCODE);
      }
   }

   @Override
   protected void onPause() {
      super.onPause();
      stopTango();
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      // Check which request we're responding to
      if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
         // Make sure the request was successful
         if (resultCode == RESULT_CANCELED) {
            Toast.makeText(
               this,
               "Motion Tracking permissions required",
               Toast.LENGTH_SHORT).show();
            finish();
            return;
         }

         startTango();
      }
   }

   public synchronized void attachTexture(final int cameraId, final int textureName) {
      if (textureName > 0) {
         // Link the texture with Tango if the texture changes after
         // Tango is connected. This generally doesn't happen but
         // technically could because they happen in separate
         // threads. Otherwise the link will be made in startTango().
         if (tangoConnected_ && cameraTextures_.get(cameraId) != textureName)
            tango_.connectTextureId(cameraId, textureName);
         cameraTextures_.put(cameraId, textureName);
      }
      else
         cameraTextures_.remove(cameraId);
   }

   public synchronized void updateTexture(int cameraId) {
      if (tangoConnected_) {
         try {
            tango_.updateTexture(cameraId);
         }
         catch (TangoInvalidException e) {
            e.printStackTrace();
         }
      }
   }

   public Point getCameraFrameSize(int cameraId) {
      TangoCameraIntrinsics intrinsics = tango_.getCameraIntrinsics(cameraId);
      return new Point(intrinsics.width, intrinsics.height);
   }

   @Override
   public void onClick(View v) {
      renderer_.saveFrame();
   }

   @Override
   public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
   }

   @Override
   public void onPoseAvailable(TangoPoseData tangoPoseData) {
   }

   @Override
   public void onXyzIjAvailable(TangoXyzIjData tangoXyzIjData) {
   }

   @Override
   public void onFrameAvailable(int i) {
      view_.requestRender();
   }

   @Override
   public void onTangoEvent(TangoEvent tangoEvent) {
      Log.i("TangoEvent", String.format("%s: %s", tangoEvent.eventKey, tangoEvent.eventValue));
   }

   private void startTango() {
      try {
         // Connect Tango.
         tango_.connect(tangoConfig_);
         tangoConnected_ = true;

         // Attach cameras to textures.
         synchronized(this) {
            for (Map.Entry<Integer, Integer> entry : cameraTextures_.entrySet())
               tango_.connectTextureId(entry.getKey(), entry.getValue());
         }

         // Attach Tango listener.
         ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();
         framePairs.add(new TangoCoordinateFramePair(
            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
            TangoPoseData.COORDINATE_FRAME_DEVICE));
         tango_.connectListener(framePairs, this);
      }
      catch (TangoOutOfDateException e) {
         Toast.makeText(
            this,
            "TangoCore update required",
            Toast.LENGTH_SHORT).show();
      }
      catch (TangoErrorException e) {
         Toast.makeText(
            this,
            "Tango error: " + e.getMessage(),
            Toast.LENGTH_SHORT).show();
      }
   }

   private synchronized void stopTango() {
      try {
         if (tangoConnected_) {
            tango_.disconnect();
            tangoConnected_ = false;
         }
      }
      catch (TangoErrorException e) {
         Toast.makeText(
            this,
            "Tango error: " + e.getMessage(),
            Toast.LENGTH_SHORT).show();
      }
   }
}

