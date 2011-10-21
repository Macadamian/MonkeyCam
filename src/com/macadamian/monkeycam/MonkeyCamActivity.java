/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.macadamian.monkeycam;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.FaceDetector;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

// imports for writing frames to the filesystem
/*import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.os.Environment;*/

// ----------------------------------------------------------------------

public class MonkeyCamActivity extends Activity {
    private static final String TAG = "MonkeyCamActivity";
    private Preview mPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        //Debug.startMethodTracing(TAG);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new Preview(this);
        setContentView(mPreview);
    }

    @Override
    protected void onPause() {
    	super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onResume() {
    	super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
        Log.d(TAG, "onDestroy");
        //Debug.stopMethodTracing();
    }

    // TODO: implement onPause, onResume so we don't keep running when the phone
    // is in suspend mode?
}

// ----------------------------------------------------------------------

class Preview extends SurfaceView implements SurfaceHolder.Callback
                                           , Camera.PreviewCallback {
    private static final String TAG = "Preview";
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Bitmap mWorkBitmap;
    private Bitmap mMonkeyImage;

    private static final int NUM_FACES = 32; // max is 64
    private static final boolean DEBUG = true;

    private FaceDetector mFaceDetector;
    private FaceDetector.Face[] mFaces = new FaceDetector.Face[NUM_FACES];
    private FaceDetector.Face face = null;      // refactor this to the callback

    private PointF eyesMidPts[] = new PointF[NUM_FACES];
    private float  eyesDistance[] = new float[NUM_FACES];

    private Paint tmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pOuterBullsEye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint pInnerBullsEye = new Paint(Paint.ANTI_ALIAS_FLAG);
   
    private int picWidth, picHeight;
    private float ratio, xRatio, yRatio;


    Preview(Context context) {
        super(context);
        Log.d(TAG, "Preview");
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.setFormat(ImageFormat.NV21);

        pInnerBullsEye.setStyle(Paint.Style.FILL);
        pInnerBullsEye.setColor(Color.RED);
       
        pOuterBullsEye.setStyle(Paint.Style.STROKE);
        pOuterBullsEye.setColor(Color.RED);
       
        tmpPaint.setStyle(Paint.Style.STROKE);

        mMonkeyImage = BitmapFactory.decodeResource(getResources(), R.drawable.monkey_head);

        picWidth = mMonkeyImage.getWidth();
        picHeight = mMonkeyImage.getHeight();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated Surface is: " + mHolder.getSurface().getClass().getName());

        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        mCamera = Camera.open();
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException exception) {
            mCamera.release();
            mCamera = null;
            // TODO: add more exception handling logic here
        }
        setWillNotDraw(false);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");

        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
    	setWillNotDraw(true);
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }


    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        Log.d(TAG, String.format("surfaceChanged: format=%d, w=%d, h=%d", format, w, h));

        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        // TODO: the obtained size is dependant on the current orientation, and that's bad
        Camera.Parameters parameters = mCamera.getParameters();

        List<Size> sizes = parameters.getSupportedPreviewSizes();
        Size optimalSize = getOptimalPreviewSize(sizes, w, h);
        parameters.setPreviewSize(optimalSize.width, optimalSize.height);

        mCamera.setParameters(parameters);
        mCamera.startPreview();

        // Setup the objects for the face detection
        mWorkBitmap = Bitmap.createBitmap(optimalSize.width, optimalSize.height, Bitmap.Config.RGB_565);
        mFaceDetector = new FaceDetector(optimalSize.width, optimalSize.height, NUM_FACES);

        int bufSize = optimalSize.width * optimalSize.height *
            ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
        byte[] cbBuffer = new byte[bufSize];
        mCamera.setPreviewCallbackWithBuffer(this);
        mCamera.addCallbackBuffer(cbBuffer);
    }

    /* Camera.PreviewCallback implementation */
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.d(TAG, "onPreviewFrame");

        // face detection: first convert the image from NV21 to RGB_565
        YuvImage yuv = new YuvImage(data, ImageFormat.NV21,
                mWorkBitmap.getWidth(), mWorkBitmap.getHeight(), null);
        Rect rect = new Rect(0, 0, mWorkBitmap.getWidth(),
                mWorkBitmap.getHeight());	// TODO: make rect a member and use it for width and height values above

        // TODO: use a threaded option or a circular buffer for converting streams?  see http://ostermiller.org/convert_java_outputstream_inputstream.html
        ByteArrayOutputStream baout = new ByteArrayOutputStream();
        if (!yuv.compressToJpeg(rect, 100, baout)) {
            Log.e(TAG, "compressToJpeg failed");
        }
        BitmapFactory.Options bfo = new BitmapFactory.Options();
        bfo.inPreferredConfig = Bitmap.Config.RGB_565;
        mWorkBitmap = BitmapFactory.decodeStream(
            new ByteArrayInputStream(baout.toByteArray()), null, bfo);

        // Dev only, save the bitmap to a file for visual inspection
        // Also remove the WRITE_EXTERNAL_STORAGE permission from the manifest
        /*String path = Environment.getExternalStorageDirectory().toString() + "/monkeyCam";
        try {
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdir();
            }

            FileOutputStream out = new FileOutputStream(path + "/monkeyCamCapture" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".jpg");
            mWorkBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        Arrays.fill(mFaces, null);	// use arraycopy instead?
        Arrays.fill(eyesMidPts, null);	// use arraycopy instead?
        mFaceDetector.findFaces(mWorkBitmap, mFaces);

        for (int i = 0; i < mFaces.length; i++)
        {
            face = mFaces[i];
            try {
                PointF eyesMP = new PointF();
                face.getMidPoint(eyesMP);
                eyesDistance[i] = face.eyesDistance();
                eyesMidPts[i] = eyesMP;

                if (DEBUG)
                {
                    Log.i("Face",
                            i +  " " + face.confidence() + " " + face.eyesDistance() + " "
                            + "Pose: ("+ face.pose(FaceDetector.Face.EULER_X) + ","
                            + face.pose(FaceDetector.Face.EULER_Y) + ","
                            + face.pose(FaceDetector.Face.EULER_Z) + ")"
                            + "Eyes Midpoint: ("+eyesMidPts[i].x + "," + eyesMidPts[i].y +")"
                    );
                }
            }
            catch (Exception e)
            {
                if (DEBUG) Log.e("Face", i + " is null");
            }
        }
        
        invalidate(); // use a dirty Rect?

        // Requeue the buffer so we get called again
        mCamera.addCallbackBuffer(data);
    }


    @Override
    protected void onDraw(Canvas canvas)
    {
        Log.d(TAG, "onDraw: frame size=(" + mWorkBitmap.getWidth() + ", " + mWorkBitmap.getHeight() + ") display size=(" + getWidth() + ", " + getHeight() + ")");
        super.onDraw(canvas);

        xRatio = getWidth() * 1.0f / mWorkBitmap.getWidth();
        yRatio = getHeight() * 1.0f / mWorkBitmap.getHeight();

        for (int i = 0; i < eyesMidPts.length; i++)
        {
            if (eyesMidPts[i] != null)
            {
                ratio = eyesDistance[i] * 4.0f / picWidth;
                RectF scaledRect = new RectF((eyesMidPts[i].x - picWidth * ratio / 2.0f) * xRatio,
                                             (eyesMidPts[i].y - picHeight * ratio / 2.0f) * yRatio,
                                             (eyesMidPts[i].x + picWidth * ratio / 2.0f) * xRatio,
                                             (eyesMidPts[i].y + picHeight * ratio / 2.0f) * yRatio);

                canvas.drawBitmap(mMonkeyImage, null , scaledRect, tmpPaint);
            }
        }
    }
}
