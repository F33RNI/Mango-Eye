/*
 * Copyright (C) 2021 Fern H., Mango-Eye Android application
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
import android.util.Log;
import android.view.Surface;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class OpenCVHandler implements CameraBridgeViewBase.CvCameraViewListener2 {
    private final String TAG = this.getClass().getName();

    private static final int detectMotionFrames = 5;
    private static final long stopRecordingTimeout = 5000;
    private static final long warmupTimeout = 5000;

    private final JavaCameraView cameraBridgeViewBase;
    private final Activity activity;
    private final Recorder recorder;

    private boolean initialized;

    private Mat inputRGBA, outputRGBA, matRGBAt, matGray, matRef, matRefFloat, matDiff;
    private List<Mat> channels;

    private final Timestamp timestamp = new Timestamp(0);
    private final Scalar textBackgroundColor = new Scalar(255, 255, 255, 255);
    private final Scalar textForegroundColor = new Scalar(0, 0, 0, 255);

    private int rotationLast;
    private boolean flashlightStateLast;
    private int motionFrames;
    private long warmupTimer, stopTimer;

    OpenCVHandler(JavaCameraView cameraBridgeViewBase,
                  Activity activity, Recorder recorder) {
        this.cameraBridgeViewBase = cameraBridgeViewBase;
        this.activity = activity;
        this.recorder = recorder;

        this.initialized = false;
    }

    /**
     * @return CameraBridgeViewBase class
     */
    public CameraBridgeViewBase getCameraBridgeViewBase() {
        return cameraBridgeViewBase;
    }

    /**
     * Initializes the components of the class.
     * NOTE: Make sure the method is called no more than once to prevent memory leaks
     */
    public void initView() {
        // Initialize CameraBridgeViewBase object
        cameraBridgeViewBase.setCvCameraViewListener(this);
        cameraBridgeViewBase.setCameraIndex(SettingsContainer.cameraID);
        cameraBridgeViewBase.setVisibility(CameraBridgeViewBase.VISIBLE);

        // Initialize variables
        rotationLast = -1;
        flashlightStateLast = false;
        motionFrames = 0;
        warmupTimer = 0;
        stopTimer = 0;
        inputRGBA = new Mat();
        outputRGBA = new Mat();
        matRGBAt = new Mat();
        matGray = new Mat();
        matRef = new Mat();
        matRefFloat = new Mat();
        matDiff = new Mat();
        channels = new ArrayList<>();

        // Set initialized flag
        initialized = true;
    }

    /**
     * @return true if initView() was called
     */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, "onCameraViewStarted");

        // Reset times
        warmupTimer = 0;
        stopTimer = 0;
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped");

        // Stop recording
        if (recorder.isRecording())
            recorder.stopRecording();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        try {
            // Read input RGBA image
            inputRGBA = inputFrame.rgba();

            // Initialise warmup timer
            if (warmupTimer == 0)
                warmupTimer = System.currentTimeMillis();

            // Calculate warmup time left
            long warmupTimeLeft = System.currentTimeMillis() - warmupTimer;

            // Get current screen rotation angle
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            // Rotate frame on different orientations
            if (rotation == Surface.ROTATION_0) {
                Core.transpose(inputRGBA, matRGBAt);
                if (SettingsContainer.cameraID == CameraBridgeViewBase.CAMERA_ID_FRONT)
                    Core.flip(matRGBAt, inputRGBA, 0);
                else
                    Core.flip(matRGBAt, inputRGBA, 1);
            } else if (rotation == Surface.ROTATION_270) {
                Core.flip(inputRGBA, inputRGBA, 0);
                Core.flip(inputRGBA, inputRGBA, 1);
            } else if (rotation == Surface.ROTATION_180) {
                Core.transpose(inputRGBA, matRGBAt);
                if (SettingsContainer.cameraID == CameraBridgeViewBase.CAMERA_ID_FRONT)
                    Core.flip(matRGBAt, inputRGBA, 1);
                else
                    Core.flip(matRGBAt, inputRGBA, 0);
            }

            // Convert to grayscale
            Imgproc.cvtColor(inputRGBA, matGray, Imgproc.COLOR_RGBA2GRAY);

            // Fill reference frame on first run
            if (warmupTimeLeft < warmupTimeout / 2
                    || matGray.cols() != matRefFloat.cols()
                    || matGray.rows() != matRefFloat.rows()) {
                matGray.convertTo(matRefFloat, CvType.CV_32FC1);
            }

            // Find difference in frames
            matRefFloat.convertTo(matRef, CvType.CV_8UC1);
            Core.absdiff(matGray, matRef, matDiff);

            // Accumulate reference frame
            Imgproc.accumulateWeighted(matGray, matRefFloat, SettingsContainer.speedThreshold);

            // Threshold difference
            Imgproc.threshold(matDiff, matDiff, 20, 255, 0);

            // Increment number of frames with motion
            if (Core.countNonZero(matDiff) > matDiff.cols() * matDiff.rows()
                    * SettingsContainer.sizeThreshold) {
                if (warmupTimeLeft > warmupTimeout && motionFrames <= detectMotionFrames)
                    motionFrames++;
            }

            // Decrement number of motion frames
            else if (motionFrames > 0)
                motionFrames--;

            // Start new recording
            if (warmupTimeLeft > warmupTimeout && motionFrames >= detectMotionFrames) {
                if (!recorder.isRecording()) {
                    // Enable flashlight
                    if (SettingsContainer.enableFlashlight)
                        setFlashlight(true);

                    // Start recording
                    recorder.startRecording(inputRGBA.width(), inputRGBA.height(), 30);
                }
                stopTimer = 0;
            }

            // Stop recording
            if (recorder.isRecording() && motionFrames <= 0) {
                if (stopTimer == 0)
                    stopTimer = System.currentTimeMillis();
                if (System.currentTimeMillis() - stopTimer >= stopRecordingTimeout) {
                    // Stop recording
                    recorder.stopRecording();

                    // Disable flashlight
                    setFlashlight(false);

                    // Reset warmup timer
                    warmupTimer = System.currentTimeMillis();
                }
            }

            // Set current timestamp
            timestamp.setTime(System.currentTimeMillis());

            // Add timestamp text
            Imgproc.putText(inputRGBA, timestamp.toString(), new Point(10, 20),
                    Core.FONT_HERSHEY_PLAIN, 1, textBackgroundColor, 2);
            Imgproc.putText(inputRGBA, timestamp.toString(), new Point(10, 20),
                    Core.FONT_HERSHEY_PLAIN, 1, textForegroundColor, 1);

            // Record input frame
            if (recorder.isRecording())
                recorder.recordRGBAMat(inputRGBA);

            // Clone object to output frame
            inputRGBA.copyTo(outputRGBA);

            // Create movement mask
            channels.clear();
            channels.add(Mat.zeros(matDiff.rows(), matDiff.cols(), matDiff.type()));
            channels.add(matDiff);
            channels.add(Mat.zeros(matDiff.rows(), matDiff.cols(), matDiff.type()));
            Core.divide(matDiff, new Scalar(2), matDiff);
            channels.add(matDiff);
            Core.merge(channels, matDiff);

            // Add mask to the output image
            Core.add(outputRGBA, matDiff, outputRGBA);

            // Add recording text
            if (recorder.isRecording()) {
                Imgproc.putText(outputRGBA, "Recording...", new Point(10, 35),
                        Core.FONT_HERSHEY_PLAIN, 1, textBackgroundColor, 2);
                Imgproc.putText(outputRGBA, "Recording...", new Point(10, 35),
                        Core.FONT_HERSHEY_PLAIN, 1, textForegroundColor, 1);
            }

            // Add warming up text
            if (warmupTimeLeft < warmupTimeout) {
                int warmupSeconds = (int) ((warmupTimeout - warmupTimeLeft) / 1000);
                Imgproc.putText(outputRGBA, "Warming up: " + warmupSeconds + "s",
                        new Point(10, 50), Core.FONT_HERSHEY_PLAIN, 1,
                        textBackgroundColor, 2);
                Imgproc.putText(outputRGBA, "Warming up: " + warmupSeconds + "s",
                        new Point(10, 50), Core.FONT_HERSHEY_PLAIN, 1,
                        textForegroundColor, 1);
            }

            // Resize to original size
            Imgproc.resize(outputRGBA, outputRGBA, inputFrame.rgba().size());

            // On rotation changed
            if (rotation != rotationLast) {
                // Set MAX_PRIORITY to the current thread
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

                // Set new scaling factor
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
                    cameraBridgeViewBase.setScaleY((float)
                            ((inputRGBA.size().width * inputRGBA.size().width)
                                    / (inputRGBA.size().height * inputRGBA.size().height)));
                else
                    cameraBridgeViewBase.setScaleY(1);
            }

            // Remember new rotation
            rotationLast = rotation;

            // Return frame
            return outputRGBA;
        } catch (Exception e) {
            // Show error message
            Log.e(TAG, "Error processing frame!", e);
        }

        // Return raw frame if error occurs
        return inputFrame.rgba();
    }

    /**
     * Turns on or off flashlight
     *
     * NOTE: Please add following code to the JavaCameraView class
     * (file (in the OpenCV SDK): java/src/org.opencv/android/JavaCameraView.java)
     *
     *     public void turnOffTheFlash() {
     *         Camera.Parameters params = mCamera.getParameters();
     *         params.setFlashMode(params.FLASH_MODE_OFF);
     *         mCamera.setParameters(params);
     *     }
     *
     *     public void turnOnTheFlash() {
     *         Camera.Parameters params = mCamera.getParameters();
     *         params.setFlashMode(params.FLASH_MODE_TORCH);
     *         mCamera.setParameters(params);
     *     }
     *
     * @param state set to true to enable flashlight or false to disable it
     */
    private void setFlashlight(boolean state) {
        if (state == flashlightStateLast)
            return;

        // Check if flashlight supported
        if (activity.getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {

            // Enable or disable flashlight
            if (state)
                cameraBridgeViewBase.turnOnTheFlash();
            else
                cameraBridgeViewBase.turnOffTheFlash();
        }

        flashlightStateLast = state;
    }
}
