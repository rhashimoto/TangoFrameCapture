package com.shoestringresearch.tango;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TangoActivity
   extends android.app.Activity
   implements Tango.OnTangoUpdateListener {
   private Tango tango_;
   private TangoConfig tangoConfig_;
   private volatile boolean tangoConnected_ = false;

   HashMap<Integer, Integer> cameraTextures_;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      tango_ = new Tango(this);
      tangoConfig_ = tango_.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
      cameraTextures_ = new HashMap<>();
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

   public void attachTexture(final int cameraId, final int textureName) {
      // Ensure that the body runs on the UI thread. This simplifies
      // synchronizing access to member variables.
      if (!Looper.getMainLooper().equals(Looper.myLooper())) {
         runOnUiThread(new Runnable() {
            public void run() {
               attachTexture(cameraId, textureName);
            }
         });
         return;
      }

      if (textureName > 0) {
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

   @Override
   public void onPoseAvailable(TangoPoseData tangoPoseData) {
   }

   @Override
   public void onXyzIjAvailable(TangoXyzIjData tangoXyzIjData) {
   }

   @Override
   public void onFrameAvailable(int i) {
   }

   @Override
   public void onTangoEvent(TangoEvent tangoEvent) {
   }

   public Tango getTango() {
      return tango_;
   }

   protected void setTangoConfig(TangoConfig tangoConfig) {
      tangoConfig_ = tangoConfig;
   }

   private void startTango() {
      try {
         // Connect Tango.
         if (tangoConfig_ == null)
            tangoConfig_ = tango_.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
         tango_.connect(tangoConfig_);
         tangoConnected_ = true;

         for (Map.Entry<Integer, Integer> entry : cameraTextures_.entrySet())
            tango_.connectTextureId(entry.getKey(), entry.getValue());

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
