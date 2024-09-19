package org.freedesktop.gstreamer.tutorials.tutorial_3;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import android.graphics.Bitmap;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.freedesktop.gstreamer.GStreamer;

public class Tutorial3 extends AppCompatActivity implements SurfaceHolder.Callback {
    private boolean is_recv_client = false;

    private native void nativeInit(boolean is_recv);     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    private native void nativeAppsrcData(byte[] imageData);
    private long native_custom_data;      // Native code will use this to keep private data

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private Preview preview;
    private PreviewView previewView;
    private ImageAnalysis imageAnalysis;
    private ProcessCameraProvider cameraProvider;

    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (!hasCameraPermission()) {
            requestCameraPermission();
        }

        // Initialize GStreamer and warn if it fails
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.main);

        previewView = findViewById(R.id.preview_view);

        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        if (is_recv_client) {
            sv.setVisibility(1);
        }
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        Button camera = (Button) this.findViewById(R.id.button_camera);
        if (!is_recv_client) {
            camera.setVisibility(1);
        }
        camera.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (!is_recv_client) {
                    previewView.setVisibility(1);
                    startCamera();
                    camera.setEnabled(false);
                }
            }
        });

        ImageButton play = (ImageButton) this.findViewById(R.id.button_play);
        play.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                nativePlay();
            }
        });

        ImageButton pause = (ImageButton) this.findViewById(R.id.button_stop);
        pause.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                nativePause();
            }
        });

        nativeInit(is_recv_client);
    }

    protected void onDestroy() {
        nativeFinalize();
        super.onDestroy();
    }

    private boolean hasCameraPermission() {
      return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    private void startCamera() {
        // Camera provider
        ProcessCameraProvider processCameraProvider = null;
        try {
            processCameraProvider = ProcessCameraProvider.getInstance(this).get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Full screen
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        // Back camera
        CameraSelector cameraSelector =
                new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        // 16:9 aspect ratio
        Preview preview = new Preview.Builder().build();

        // Display the preview on previewView
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Refresh the screen continuously while analyzing is ongoing. When analysis is done, analyze the latest photo again.
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

        analysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> {
            try {
//                ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
//                int ySize = planes[0].getBuffer().remaining(); // Y plane
//                int uSize = planes[1].getBuffer().remaining(); // U plane
//                int vSize = planes[2].getBuffer().remaining(); // V plane
//
//                byte[] yuvBytes = new byte[ySize + uSize + vSize];
//                planes[0].getBuffer().get(yuvBytes, 0, ySize);
//                planes[1].getBuffer().get(yuvBytes, ySize, uSize);
//                planes[2].getBuffer().get(yuvBytes, ySize + uSize, vSize);

                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap bitmap = imageProxy.toBitmap();
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                byte[] yuvBytes = bitmapToYUV(rotatedBitmap);

                nativeAppsrcData(yuvBytes);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            imageProxy.close();
        });

        // Bind the camera's lifecycle to the main activity
        processCameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);
    }

    private byte[] bitmapToYUV(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        byte[] yuv = new byte[width * height * 3 / 2];
        int frameSize = width * height;
        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + frameSize / 4;

        int a, R, G, B, Y, U, V;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                a = (argb[j * width + i] & 0xff000000) >> 24;
                R = (argb[j * width + i] & 0xff0000) >> 16;
                G = (argb[j * width + i] & 0xff00) >> 8;
                B = (argb[j * width + i] & 0xff);

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv[vIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }
            }
        }

        return yuv;
    }


    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
        runOnUiThread (new Runnable() {
          public void run() {
            tv.setText(message);
          }
        });
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {

        // Re-enable buttons, now that GStreamer is initialized
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            public void run() {
                activity.findViewById(R.id.button_play).setEnabled(true);
                activity.findViewById(R.id.button_stop).setEnabled(true);
            }
        });
    }

    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("tutorial-3");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize ();
    }

}
