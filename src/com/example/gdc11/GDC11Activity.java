package com.example.gdc11;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.ETC1Util;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

// This is a slightly-more-complex-than Hello World for OpenGL.
// It shows how to do fast resource loading, how to use compressed
// textures, how to do handle touch input, how to use VBOs, and
// generally how to draw with OpenGL ES 2.0. For easier navigation,
// almost all code is contained in this single class.
public class GDC11Activity extends Activity {
    static private final String kTag = "GDC11";

    // Tweakables.
    private static final boolean kUseMipmaps = true;
    private static final boolean kUseCompressedTextures = true;
    private static final boolean kUseMultisampling = false;

    // If |kUseMultisampling| is set, this is what chose the multisampling config.
    private MultisampleConfigChooser mConfigChooser;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GLSurfaceView view = new TouchGLView(this);
        setContentView(view);
    }

    // Subclass GLSurfaceView to receive touch events. This class does nothing
    // but touch event handling.
    private class TouchGLView extends GLSurfaceView
            implements GestureDetector.OnGestureListener,
                       ScaleGestureDetector.OnScaleGestureListener {
        private GDC11Renderer mRenderer;
        private GestureDetector mTapDetector;
        private ScaleGestureDetector mScaleDetector;
        private float mLastSpan = 0;
        private long mLastNonTapTouchEventTimeNS = 0;

        TouchGLView(Context c) {
            super(c);
            // Use Android's built-in gesture detectors to detect
            // which touch event the user is doing.
            mTapDetector = new GestureDetector(c, this);
            mTapDetector.setIsLongpressEnabled(false);
            mScaleDetector = new ScaleGestureDetector(c, this);

            // Create an OpenGL ES 2.0 context.
            setEGLContextClientVersion(2);
            if (kUseMultisampling)
                setEGLConfigChooser(mConfigChooser = new MultisampleConfigChooser());
            setRenderer(mRenderer = new GDC11Renderer());
        }

        @Override
        public boolean onTouchEvent(final MotionEvent e) {
            // Forward touch events to the gesture detectors.
            mScaleDetector.onTouchEvent(e);
            mTapDetector.onTouchEvent(e);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Forward the scale event to the renderer.
            final float amount = detector.getCurrentSpan() - mLastSpan;
            queueEvent(new Runnable() {
                    public void run() {
                        // This Runnable will be executed on the render
                        // thread.
                        // In a real app, you'd want to divide this by
                        // the display resolution first.
                        mRenderer.zoom(amount);
                    }});
            mLastSpan = detector.getCurrentSpan();
            mLastNonTapTouchEventTimeNS = System.nanoTime();
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mLastSpan = detector.getCurrentSpan();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                final float dx, final float dy) {
            // Forward the drag event to the renderer.
            queueEvent(new Runnable() {
                    public void run() {
                        // This Runnable will be executed on the render
                        // thread.
                        // In a real app, you'd want to divide these by
                        // the display resolution first.
                        mRenderer.drag(dx, dy);
                    }});
            mLastNonTapTouchEventTimeNS = System.nanoTime();
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Have a short dead time after rotating and zooming,
            // to make erratic taps less likely.
            final double kDeadTimeS = 0.3;
            if ((System.nanoTime() - mLastNonTapTouchEventTimeNS) / 1e9f < kDeadTimeS)
                return true;

            // Copy x/y into local variables, because |e| is changed and reused for
            // other views after this has been called.
            //final int x = Math.round(e.getX());
            //final int y = Math.round(e.getY());

            // Run something on the render thread...
            queueEvent(new Runnable(){
                    public void run() {
                        // Here you could call a method on the renderer that
                        // checks which object has been tapped. A good way to
                        // do this is color picking: Render your scene with one
                        // unique color per entity (make sure to disable
                        // multisampling, blending, and everything else that
                        // changes colors), and then get the pixel color below
                        // the tap (or in a small neighborhood if nothing is
                        // below the tap). Map the color back to the object.
                        // mRenderer.getEntityAt(x, y);
                        
                        // ...once that's done, post the result back to the UI
                        // thread:
                        getHandler().post(new Runnable() {
                                @Override
                                public void run() {
                                    // ...
                                }});
                        }});
            return true;
        }
    }

    // The renderer object. All methods of this class are called on the render
    // thread.
    private class GDC11Renderer implements GLSurfaceView.Renderer {
        // FPS counter.
        private int mFrameCount = 0;
        private long mStartTime = System.nanoTime();

        // OpenGL state stuff.
        private int mBuffer;
        private int mShader;
        private int mTristripBuffer;
        private int mNumTristripIndices;
        private int mTexture;
        private int mViewProjectionLoc;
        private int mLightVectorLoc;

        // Camera stuff.
        private float mPhi, mZ = 3.5f;
        private float[] mProjectionMatrix = new float[16];
        private float[] mViewMatrix = new float[16];
        private float[] mViewProjectionMatrix = new float[16];
        private float[] mLightVector = { 2/3.f, 1/3.f, 2/3.f };  // Needs to be normalized
        private float[] mTransformedLightVector = new float[3];

        // Updates mViewProjectionMatrix with the current camera position.
        private void updateMatrices() {
            Matrix.setIdentityM(mViewMatrix, 0);
            Matrix.translateM(mViewMatrix, 0, 0, 0, -mZ);
            Matrix.rotateM(mViewMatrix, 0, mPhi, 0, 1, 0);
            Matrix.rotateM(mViewMatrix, 0, -90, 1, 0, 0);
            Matrix.multiplyMM(
                    mViewProjectionMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

            // Transform the light vector into model space. Since mViewMatrix
            // is orthogonal, the reverse transform can be done by multiplying
            // with the transpose.
            mTransformedLightVector[0] =
                mViewMatrix[0] * mLightVector[0] +
                mViewMatrix[1] * mLightVector[1] +
                mViewMatrix[2] * mLightVector[2];
            mTransformedLightVector[1] =
                mViewMatrix[4] * mLightVector[0] +
                mViewMatrix[5] * mLightVector[1] +
                mViewMatrix[6] * mLightVector[2];
            mTransformedLightVector[2] =
                mViewMatrix[8] * mLightVector[0] +
                mViewMatrix[9] * mLightVector[1] +
                mViewMatrix[10] * mLightVector[2];            
        }

        // Called from the UI when the user drags the scene.
        public void drag(float dx, float dy) {
            // In a real app, you'd have some animation logic in here.
            mPhi -= dx / 5;
            updateMatrices();
        }

        // Called from the UI when the user zooms the scene.
        public void zoom(float z) {
            mZ = (float) Math.min(5, Math.max(mZ - z / 300, 1.6));
            updateMatrices();
        }

        // This is called continuously to render.
        @Override
        public void onDrawFrame(GL10 unused) {
            int clearMask = GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT;
            if (kUseMultisampling && mConfigChooser.usesCoverageAa()) {
              final int GL_COVERAGE_BUFFER_BIT_NV = 0x8000;
              clearMask |= GL_COVERAGE_BUFFER_BIT_NV;
            }
            GLES20.glClear(clearMask);

            GLES20.glUseProgram(mShader);
            GLES20.glUniformMatrix4fv(mViewProjectionLoc, 1,
                    /*transpose isn't supported*/ false, mViewProjectionMatrix, 0);
            GLES20.glUniform3fv(mLightVectorLoc, 1, mTransformedLightVector, 0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBuffer);

            GLES20.glEnableVertexAttribArray(0);
            GLES20.glEnableVertexAttribArray(1);
            GLES20.glEnableVertexAttribArray(2);

            // NOTE: glVertexAttribPointer() is broken for VBOs on Android 2.2.
            // Use the patched GLES20Fix class to call these.
            // In a real app, consider using smaller attributes (maybe signed
            // bytes for normals and halfs for tex coords) -- measure
            // what works.
            GLES20Fix.glVertexAttribPointer(
                    0, 3, GLES20.GL_FLOAT, false, 4 * (3 + 3 + 2), 0);
            GLES20Fix.glVertexAttribPointer(
                    1, 3, GLES20.GL_FLOAT, false, 4 * (3 + 3 + 2), 4 * 3);
            GLES20Fix.glVertexAttribPointer(
                    2, 2, GLES20.GL_FLOAT, false, 4 * (3 + 3 + 2), 4 * (3 + 3));

            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mTristripBuffer);
            // glDrawElements() is broken as well, use a patched version here, too.
            GLES20Fix.glDrawElements(GLES20.GL_TRIANGLE_STRIP,
                    mNumTristripIndices, GLES20.GL_UNSIGNED_SHORT, 0);

            ++mFrameCount;
            if (mFrameCount % 50 == 0) {
                long now = System.nanoTime();
                double elapsedS = (now - mStartTime) / 1.0e9;
                double msPerFrame = (1000 * elapsedS / mFrameCount);
                Log.i(kTag, "ms / frame: " + msPerFrame + " - fps: " + (1000 / msPerFrame));

                mFrameCount = 0;
                mStartTime = now;
            }
        }

        private static final String kVertexShader =
            "precision mediump float; \n" +
            "uniform mat4 worldViewProjection; \n" +
            "uniform vec3 lightVector; \n" +
            "attribute vec3 position; \n" +
            "attribute vec3 normal; \n" +
            "attribute vec2 texCoord; \n" +
            "varying vec2 tc; \n" +
            "varying float light; \n" +
            "void main() { \n" +
            "  tc = texCoord; \n" +
            // Not that |lightVector| is in the model space, so the model
            // doesn't have to be transformed.
            "  light = max(dot(normal, lightVector), 0.0) + 0.2; \n" +
            "  gl_Position = worldViewProjection * vec4(position, 1.0); \n" +
            "}";

        private static final String kFragmentShader =
            "precision mediump float; \n" +
            "uniform sampler2D textureSampler; \n" +
            "varying vec2 tc; \n" +
            "varying float light; \n" +
            "void main() { \n" +
            "  gl_FragColor = light * vec4(texture2D(textureSampler, tc).rgb, 1.0); \n" +
            "}";

        // Loads a resource to a GL buffer. This method is very fast, but the
        // resource needs to be uncompressed for this method to work.
        private void loadResourceToBuffer(int target, int resource, int[] out) {
            try {
                long start = System.nanoTime();

                AssetFileDescriptor ad = getResources().openRawResourceFd(resource);
                // This will fail for compressed resources:
                FileInputStream fis = ad.createInputStream();
                FileChannel fc = fis.getChannel();
                MappedByteBuffer b =
                    fc.map(MapMode.READ_ONLY, ad.getStartOffset(), ad.getLength());
                long size = ad.getLength();
                out[1] = (int)size;

                int[] buffers = {0};
                GLES20.glGenBuffers(1, buffers, 0);
                out[0] = buffers[0];
                GLES20.glBindBuffer(target, buffers[0]);
                GLES20.glBufferData(target, out[1], b, GLES20.GL_STATIC_DRAW);

                double loadS = (System.nanoTime() - start) / 1e9;
                Log.i(kTag, (size / loadS) + " bps, " + loadS + " total");
            } catch (IOException e) {
                Log.e(kTag, "" + e);
            }
        }

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // Load geometry.
            // NEVER load stuff on the render thread in real life!
            // You'd call fc.map() and b.load() on a loader thread, and
            // only then upload that to GL once it's done.
            int[] out = {0,0};
            loadResourceToBuffer(GLES20.GL_ARRAY_BUFFER, R.raw.points_3f3f2f, out);
            mBuffer = out[0];
            loadResourceToBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, R.raw.tristrips, out);
            mTristripBuffer = out[0];
            mNumTristripIndices = out[1] / 2;

            // Load textures.
            long texStart = System.nanoTime();
            int[] tex = {0};
            GLES20.glGenTextures(1, tex, 0);
            mTexture = tex[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            if (kUseMipmaps) {
                // Note that trilinear filtering (GL_*_MIPMAP_LINEAR) is a lot
                // more expensive, you probably don't want to use that.
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_NEAREST);
            } else {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            }
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            if (kUseCompressedTextures) {
                // ~38 fps on tegra
                int levels = 1;
                if (kUseMipmaps)
                    levels = 12;
                try {
                    Log.i(kTag, "supports etc: " + ETC1Util.isETC1Supported());
                    // In a real app, you should read the texture on a bg thread with createTexture()
                    // and then only do the upload of the result on the render thread. You probably
                    // also want to put all mipmaps into a single file.
                    for (int level = 0; level < levels; ++level) {
                        String name = String.format("earth_map_%d.pkm", level);
                        if (level == 0) name = "earth_map_0.pkm.jet";
                        ETC1Util.loadTexture(GLES20.GL_TEXTURE_2D, level, 0,
                                GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5,
                                getAssets().open(name));
                    }
                } catch (NotFoundException e) {
                    Log.e(kTag, "" + e);
                } catch (IOException e) {
                    Log.e(kTag, "" + e);
                }
            } else {
                // 30 fps on tegra
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inScaled = false;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeResource(
                        getResources(), R.drawable.jpg_earth_map, opts);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                bitmap.recycle();
                if (kUseMipmaps)
                    GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            }
            double texS = (System.nanoTime() - texStart) / 1e9;
            Log.i(kTag, texS + " total tex load");

            // Prepare shaders.
            mShader = Programs.loadProgram(kVertexShader, kFragmentShader);
            GLES20.glBindAttribLocation(mShader, 0, "position");
            GLES20.glBindAttribLocation(mShader, 1, "normal");
            GLES20.glBindAttribLocation(mShader, 2, "texCoord");
            GLES20.glLinkProgram(mShader);
            mViewProjectionLoc =
                GLES20.glGetUniformLocation(mShader, "worldViewProjection");
            mLightVectorLoc =
                GLES20.glGetUniformLocation(mShader, "lightVector");

            // Other state.
            GLES20.glClearColor(0.7f, 0.7f, 0.7f, 1.0f);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }

        // Like gluPerspective(), but writes the output to a Matrix.
        private void perspectiveM(
                float[] m, float angle, float aspect, float near, float far) {
            float f = (float)Math.tan(0.5 * (Math.PI - angle));
            float range = near - far;

            m[0] = f / aspect;
            m[1] = 0;
            m[2] = 0;
            m[3] = 0;

            m[4] = 0;
            m[5] = f;
            m[6] = 0;
            m[7] = 0;

            m[8] = 0;
            m[9] = 0; 
            m[10] = far / range;
            m[11] = -1;

            m[12] = 0;
            m[13] = 0;
            m[14] = near * far / range;
            m[15] = 0;
        }

        // This is called when the surface changes, e.g. after screen rotation.
        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            float aspect = width / (float)height;
            perspectiveM(
                    mProjectionMatrix,
                    (float)Math.toRadians(45),
                    aspect, 0.5f, 5.f);
            updateMatrices();

            // Necessary if the manifest contains |android:configChanges="orientation"|.
            GLES20.glViewport(0, 0, width, height);
        }
    }
}