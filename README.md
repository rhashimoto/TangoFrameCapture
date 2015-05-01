# TangoFrameCapture
This is a simple app demonstrating how to save Project Tango frame textures in Java.
Tap the screen to capture a live frame to Pictures/Tango Capture, which can be
accessed in the Gallery app.

The implementation uses an OpenGL offscreen renderbuffer to draw the image at the
correct size (getting RGB conversion as a bonus), then uses `Bitmap.compress()` to
save.
