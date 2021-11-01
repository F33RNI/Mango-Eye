/*
 * Copyright (C) 2021 Fern H. Mango-Eye android application
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
 *
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.fern.mangoeye;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private final String TAG = this.getClass().getName();

    private final Activity activity;
    private final Recorder recorder;
    private final OpenCVHandler openCVHandler;

    private Camera camera;
    private long stopRecordingTimer = 0;
    private long warmupTimer = 0;
    private boolean flashlightAvailable = false;

    public CameraView(Activity activity,
                      Recorder recorder,
                      OpenCVHandler openCVHandler) {
        super(activity.getApplicationContext());

        this.activity = activity;
        this.recorder = recorder;
        this.openCVHandler = openCVHandler;

        SurfaceHolder mHolder = getHolder();
        mHolder.addCallback(CameraView.this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            // Open camera device
            camera = Camera.open();

            // Enable Autofocus, auto exposure and auto white balance
            Camera.Parameters params = camera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            params.setAutoExposureLock(false);
            params.setAutoWhiteBalanceLock(false);

            // Get available resolutions
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();

            // Sort the list in ascending order
            Collections.sort(sizes, new Comparator<Camera.Size>() {
                public int compare(final Camera.Size a, final Camera.Size b) {
                    return a.width * a.height - b.width * b.height;
                }
            });

            // Pick the first preview size that is equal or bigger,
            // or pick the last (biggest) option if we cannot reach the
            // initial settings of imageWidth/imageHeight
            int imageWidth = MainActivity.getSettingsContainer().frameWidth;
            int imageHeight = MainActivity.getSettingsContainer().frameHeight;
            for (int i = 0; i < sizes.size(); i++) {
                if ((sizes.get(i).width >= imageWidth && sizes.get(i).height >= imageHeight)
                        || i == sizes.size() - 1) {
                    imageWidth = sizes.get(i).width;
                    imageHeight = sizes.get(i).height;
                    Log.i(TAG, "Changed to supported resolution: " +
                            imageWidth + "x" + imageHeight);
                    break;
                }
            }
            params.setPreviewSize(imageWidth, imageHeight);

            Log.i(TAG, "Setting imageWidth: " +
                    imageWidth + " imageHeight: " + imageHeight +
                    " frameRate: " + MainActivity.getSettingsContainer().frameRate);
            params.setPreviewFrameRate(MainActivity.getSettingsContainer().frameRate);
            Log.i(TAG, "Preview frameRate: " + params.getPreviewFrameRate());

            // Init OpenCV Mats
            openCVHandler.initMats(imageWidth, imageHeight);

            // Init recorder image parameters
            recorder.setFrameSize(imageWidth, imageHeight);
            recorder.setFrameRate(params.getPreviewFrameRate());

            // Apply parameter to camera
            camera.setParameters(params);
            camera.setPreviewCallback(CameraView.this);
            camera.setPreviewDisplay(holder);
            camera.startPreview();

            // Check flashlight
            if (activity.getBaseContext().getPackageManager().
                    hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
                flashlightAvailable = true;
            else {
                flashlightAvailable = false;
                Log.w(TAG, "No flashlight available!");
            }

            // Start warmup timer
            warmupTimer = System.currentTimeMillis();

        } catch(Exception e) {
            // Show error message
            Toast.makeText(activity, "Error starting camera!",
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error starting camera!", e);

            // Release camera
            if (camera != null)
                camera.release();

            // Exit application
            activity.finish();
            System.exit(0);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCamera();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        // Feed image to OpenCVHandler
        openCVHandler.feedNewYUVData(data);

        // Initialize reference frame with warmup timer
        if (System.currentTimeMillis() - warmupTimer
                < MainActivity.getSettingsContainer().warmupTimeout) {
            if (System.currentTimeMillis() - warmupTimer
                    < MainActivity.getSettingsContainer().warmupTimeout / 2)
                openCVHandler.resetRefFrame();
            return;
        }

        // Record new image
        if (recorder.isRecording())
            recorder.recordRGBAMat(openCVHandler.getMatRGBA());

        // Start new recording if motion detected
        if (openCVHandler.isMotionDetected() && !recorder.isRecording()) {
            Toast.makeText(activity, "New motion detected!", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "New motion detected!");
            recorder.startRecording();

            // Turn on flashlight
            if (flashlightAvailable && MainActivity.getSettingsContainer().enableFlashlightOnMotion)
                enableFlashlight();
        }

        // Start timer
        if (openCVHandler.isMotionDetected() && recorder.isRecording()) {
            stopRecordingTimer = System.currentTimeMillis();
        }

        // Stop recording after stop_recording_timeout
        if (recorder.isRecording() && System.currentTimeMillis() - stopRecordingTimer
                > MainActivity.getSettingsContainer().stopRecordingTimeout) {
            Toast.makeText(activity, "No more movements detected", Toast.LENGTH_LONG).show();
            Log.w(TAG, "No more movements detected");
            recorder.stopRecording();

            // Turn off flashlight
            if (flashlightAvailable && MainActivity.getSettingsContainer().enableFlashlightOnMotion)
                disableFlashlight();
        }
    }

    /**
     * Stops recording and releases camera
     */
    public void stopCamera() {
        Log.w(TAG, "Stopping camera");
        recorder.stopRecording();

        // Close camera
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    /**
     * Enabled camera flashlight
     */
    private void enableFlashlight() {
        try {
            Camera.Parameters params = camera.getParameters();
            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            camera.setParameters(params);
        } catch (Exception ignored) { }
    }

    /**
     * Disables camera flashlight
     */
    private void disableFlashlight() {
        try {
            Camera.Parameters params = camera.getParameters();
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);
        } catch (Exception ignored) { }
    }
}
