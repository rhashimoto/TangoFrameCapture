package com.shoestringresearch.tango.capture;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.widget.Toast;

import com.google.atap.tangoservice.TangoCameraIntrinsics;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// Need OpenGL ES 3.0 for RGBA8 renderbuffer.
import static android.opengl.GLES30.*;

class Renderer implements GLSurfaceView.Renderer {
   // Vertex program flips Y when drawing on screen, doesn't flip
   // when drawing offscreen for saving.
   static final String videoVertexSource =
      "uniform mediump int cap;\n" +
      "attribute vec4 a_v;\n" +
      "varying vec2 t;\n" +
      "void main() {\n" +
      "	gl_Position = a_v;\n" +
      "	t = 0.5*vec2(a_v.x, cap != 0 ? a_v.y : -a_v.y) + vec2(0.5,0.5);\n" +
      "}\n";

   // Fragment buffer reorders color components when drawing offscreen
   // for saving.
   static final String videoFragmentSource =
      "#extension GL_OES_EGL_image_external : require\n" +
      "precision mediump float;\n" +
      "uniform mediump int cap;\n" +
      "varying vec2 t;\n" +
      "uniform samplerExternalOES colorTex;\n" +
      "void main() {\n" +
      "  vec4 c = texture2D(colorTex, t);\n" +
      "	gl_FragColor = cap != 0 ? c.bgra : c;\n" +
      "}\n";

   MainActivity activity_;

   int videoProgram_;
   int videoVertexAttribute_;
   int videoVertexBuffer_;
   int videoTextureName_;

   int offscreenBuffer_;
   Point offscreenSize_;

   volatile boolean saveNextFrame_;

   Renderer(MainActivity activity) {
      activity_ = activity;
   }

   @Override
   public void onSurfaceCreated(GL10 gl, EGLConfig config) {
      glClearColor(0.3f, 0.3f, 0.3f, 1.0f);

      IntBuffer bufferNames = IntBuffer.allocate(1);
      glGenBuffers(1, bufferNames);
      videoVertexBuffer_ = bufferNames.get(0);

      // Create a bi-unit square geometry.
      glBindBuffer(GL_ARRAY_BUFFER, videoVertexBuffer_);
      glBufferData(GL_ARRAY_BUFFER, 8, null, GL_STATIC_DRAW);
      ((ByteBuffer)glMapBufferRange(
         GL_ARRAY_BUFFER,
         0, 8,
         GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT))
         .order(ByteOrder.nativeOrder())
         .put(new byte[] { -1, 1,  -1, -1,  1, 1,  1, -1 });
      glUnmapBuffer(GL_ARRAY_BUFFER);

      // Create the video texture.
      IntBuffer textureNames = IntBuffer.allocate(1);
      glGenTextures(1, textureNames);
      videoTextureName_ = textureNames.get(0);

      glActiveTexture(GL_TEXTURE0);
      glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureName_);

      // Connect the texture to Tango.
      activity_.attachTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR, videoTextureName_);

      // Prepare the shader program.
      videoProgram_ = createShaderProgram(videoVertexSource, videoFragmentSource);
      glUseProgram(videoProgram_);
      videoVertexAttribute_ = glGetAttribLocation(videoProgram_, "a_v");
      glUniform1i(
              glGetUniformLocation(videoProgram_, "colorTex"),
              0);  // GL_TEXTURE0
      glUniform1i(
              glGetUniformLocation(videoProgram_, "cap"),
              0);

      // Get the camera frame dimensions.
      offscreenSize_ = activity_.getCameraFrameSize(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

      // Create an offscreen render target to capture a frame.
      IntBuffer renderbufferName = IntBuffer.allocate(1);
      glGenRenderbuffers(1, renderbufferName);
      glBindRenderbuffer(GL_RENDERBUFFER, renderbufferName.get(0));
      glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, offscreenSize_.x, offscreenSize_.y);

      IntBuffer framebufferName = IntBuffer.allocate(1);
      glGenFramebuffers(1, framebufferName);
      glBindFramebuffer(GL_FRAMEBUFFER, framebufferName.get(0));
      glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderbufferName.get(0));

      glBindFramebuffer(GL_FRAMEBUFFER, 0);
      offscreenBuffer_ = framebufferName.get(0);
   }

   @Override
   public void onSurfaceChanged(GL10 gl, int width, int height) {
      glViewport(0, 0, width, height);
   }

   @Override
   public void onDrawFrame(GL10 gl) {
      activity_.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

      if (!saveNextFrame_) {
         glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);

         glBindBuffer(GL_ARRAY_BUFFER, videoVertexBuffer_);
         glVertexAttribPointer(videoVertexAttribute_, 2, GL_BYTE, false, 0, 0);
         glEnableVertexAttribArray(videoVertexAttribute_);
         glActiveTexture(GL_TEXTURE0);
         glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureName_);
         glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
         glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
         glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
      }
      else {
         glBindFramebuffer(GL_FRAMEBUFFER, offscreenBuffer_);

         IntBuffer viewport = IntBuffer.allocate(4);
         glGetIntegerv(GL_VIEWPORT, viewport);
         glViewport(0, 0, offscreenSize_.x, offscreenSize_.y);
         glClear(GL_COLOR_BUFFER_BIT);

         glUniform1i(
            glGetUniformLocation(videoProgram_, "cap"),
            1);

         glBindBuffer(GL_ARRAY_BUFFER, videoVertexBuffer_);
         glVertexAttribPointer(videoVertexAttribute_, 2, GL_BYTE, false, 0, 0);
         glEnableVertexAttribArray(videoVertexAttribute_);
         glActiveTexture(GL_TEXTURE0);
         glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureName_);
         glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
         glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
         glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

         IntBuffer intBuffer = ByteBuffer.allocateDirect(offscreenSize_.x * offscreenSize_.y * 4)
                 .order(ByteOrder.nativeOrder())
                 .asIntBuffer();
         glReadPixels(0, 0, offscreenSize_.x, offscreenSize_.y, GL_RGBA, GL_UNSIGNED_BYTE, intBuffer.rewind());

         // Restore onscreen state.
         glBindFramebuffer(GL_FRAMEBUFFER, 0);
         glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
         glUniform1i(
                 glGetUniformLocation(videoProgram_, "cap"),
                 0);

         // Convert to an array for Bitmap.createBitmap().
         int[] pixels = new int[intBuffer.capacity()];
         intBuffer.rewind();
         intBuffer.get(pixels);
         saveNextFrame_ = false;

         try {
            // Create/access a pictures subdirectory.
            File directory = new File(
               Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
               "Tango Captures");
            if (!directory.mkdirs() && !directory.isDirectory()) {
               Toast.makeText(
                       activity_,
                       "Could not access save directory",
                       Toast.LENGTH_SHORT).show();
               return;
            }

            // Get the current capture index to construct a unique filename.
            SharedPreferences prefs = activity_.getPreferences(Context.MODE_PRIVATE);
            int index = prefs.getInt("index", 0);
            SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putInt("index", index + 1);
            prefsEditor.commit();

            // Create the capture file.
            File file = new File(directory, String.format("tango%05d.png", index));
            FileOutputStream fileOutputStream = new FileOutputStream(file);

            // Bitmap conveniently provides file output.
            Bitmap bitmap = Bitmap.createBitmap(pixels, offscreenSize_.x, offscreenSize_.y, Bitmap.Config.ARGB_8888);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fileOutputStream);
            fileOutputStream.close();

            // Make the new file visible to other apps.
            activity_.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
         }
         catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   void saveFrame() {
      saveNextFrame_ = true;
   }

   private int createShaderProgram(String vertexSource, String fragmentSource) {
      int vsName = glCreateShader(GL_VERTEX_SHADER);
      glShaderSource(vsName, vertexSource);
      glCompileShader(vsName);
      System.out.println(glGetShaderInfoLog(vsName));

      int fsName = glCreateShader(GL_FRAGMENT_SHADER);
      glShaderSource(fsName, fragmentSource);
      glCompileShader(fsName);
      System.out.println(glGetShaderInfoLog(fsName));

      int programName = glCreateProgram();
      glAttachShader(programName, vsName);
      glAttachShader(programName, fsName);
      glLinkProgram(programName);
      System.out.println(glGetProgramInfoLog(programName));

      return programName;
   }

}
