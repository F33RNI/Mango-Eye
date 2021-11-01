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

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.bytedeco.javacv.FFmpegLogCallback;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getName();
    private final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int PERMISSION_REQUEST_CODE = 1;

    public static File settingsFile;
    public static String[] externalFilesDirs;
    private static SettingsContainer settingsContainer;

    private Recorder recorder;
    private CameraView cameraView;
    private RelativeLayout recordLayout;
    private boolean paused = false, pausedOnStart = false;

    private PowerManager.WakeLock wakeLock;

    public static SettingsContainer getSettingsContainer() {
        return settingsContainer;
    }

    public static void setSettingsContainer(SettingsContainer settingsContainer) {
        MainActivity.settingsContainer = settingsContainer;
    }

    /**
     * Checks if OpenCV library is loaded
     */
    private final BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i("OpenCV", "OpenCV loaded successfully");

                // Parse settings
                settingsContainer = new SettingsContainer();
                SettingsHandler settingsHandler = new SettingsHandler(settingsFile,
                        MainActivity.this);
                settingsHandler.readSettings();

                // Request permissions
                if (hasPermissions(MainActivity.this, PERMISSIONS)) {
                    Log.i(TAG, "Permissions granted");

                    initFilesDir();
                } else {
                    // Grant permissions
                    Log.w(TAG, "Not all permissions granted");
                    ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS,
                            PERMISSION_REQUEST_CODE);
                }
            }
            else {
                super.onManagerConnected(status);
                Toast.makeText(MainActivity.this, "OpenCV not loaded!",
                        Toast.LENGTH_SHORT).show();

                // Close the application because OpenCV library not loaded
                finish();
                //System.exit(0);
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FFmpegLogCallback.set();

        // Set flag
        pausedOnStart = true;

        // Screen parameters
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get settings file
        settingsFile = new File(getBaseContext().getExternalFilesDir( null),
                "settings.json");

        // Get list of storages
        File[] files = getBaseContext().getExternalFilesDirs(null);
        externalFilesDirs = new String[files.length];
        for (int i = 0; i < files.length; i++)
            externalFilesDirs[i] = files[i].getAbsolutePath();
        Log.i(TAG, "Available storages: " + Arrays.toString(externalFilesDirs));

        // Load main activity
        setContentView(R.layout.activity_main);

        // Load OpenCV library and init layout
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Internal OpenCV library not found." +
                    " Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,
                    this, baseLoaderCallback);
        } else {
            Log.i(TAG, "OpenCV library found inside package. Using it!");
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    /**
     * Created directory or Uri for application on new and old devices
     */
    private void initFilesDir() {
        Log.i(TAG, "Checking MangoEye directory");

        // Check if storage is available
        if (getStorageIndex() < 0) {
            Toast.makeText(this, "Previous files directory not available! " +
                    "Using storage: " + externalFilesDirs[0],
                    Toast.LENGTH_LONG).show();
            Log.w(TAG,"Previous storage not available! Using storage: "
                    + externalFilesDirs[0]);

            // Update settings
            settingsContainer.filesDirectory = externalFilesDirs[0];
            SettingsHandler.saveSettings(settingsFile, this);
        }

        // Continue if storage exists
        initModules();
    }

    public static int getStorageIndex() {
        int index = -1;
        for (int i = 0; i < externalFilesDirs.length; i++) {
            if (externalFilesDirs[i].trim()
                    .replace("/", "").replace("\\", "")
                    .equals(settingsContainer.filesDirectory.trim()
                            .replace("/", "").replace("\\", ""))) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * Checks app directory, initializes CameraView and controls brightness
     */
    private void initModules() {
        Log.i(TAG, "Initializing modules");

        // Add camera preview to the layout if permissions were granted
        initCameraView();

        // Dim screen
        brightnessControl();

        pausedOnStart = false;
        Log.i(TAG, "Initialization finished");
    }

    /**
     * Checks for permissions
     * Code from: https://stackoverflow.com/a/34343101
     * @param context Activity
     * @param permissions List of permissions
     * @return true if all permissions were granted
     */
    public boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Calls on permission check result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check permissions
        if (hasPermissions(this, PERMISSIONS)) {
            Log.i(TAG, "Permissions granted");

            initFilesDir();
        }
        else {
            Toast.makeText(this, "Permissions not granted!",
                    Toast.LENGTH_LONG).show();

            // Close the application because the permissions are not granted
            finish();
            //System.exit(0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (paused) {
            paused = false;
            initFilesDir();
        }
    }

    /**
     * At the beginning, sets the original brightness,
     * and after lower_brightness_timeout milliseconds - the minimum.
     */
    @SuppressLint("InvalidWakeLockTag")
    void brightnessControl() {
        // Restore old brightness
        try {
            // Back to default
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = -1f;
            getWindow().setAttributes(layoutParams);

        } catch (Exception ignored) { }

        // Dim screen after lower_brightness_timeout
        try {
            if (wakeLock != null)
                wakeLock.release();
            // Set lowerBrightnessTimeout to 0 to keep screen always at default brightness
            if (settingsContainer.dimScreen) {
                /*PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                        this.getLocalClassName());
                wakeLock.acquire(settingsContainer.lowerBrightnessTimeout);*/

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                            layoutParams.screenBrightness = 0f;
                            getWindow().setAttributes(layoutParams);
                        },
                        settingsContainer.lowerBrightnessTimeout);
            }
        } catch (Exception ignored) { }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onPause() {
        super.onPause();

        if (!pausedOnStart) {
            paused = true;

            // Stop recording
            if (recorder != null)
                recorder.stopRecording();

            // Release camera
            if (cameraView != null)
                cameraView.stopCamera();

            // Remove preview from layout
            recordLayout.removeView(cameraView);
        }

        // Release wakeLock
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop recording
        if (recorder!= null)
            recorder.stopRecording();

        // Release camera
        if (cameraView != null)
            cameraView.stopCamera();

        // Release wakeLock
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        // Close thread
        ActivityCompat.finishAffinity(this);
        //System.exit(0);
    }

    /**
     * Initializes cameraView and adds it to the layout
     */
    private void initCameraView() {
        // Create Recorder class
        recorder = new Recorder(this);

        // Create OpenCVHandler class
        OpenCVHandler openCVHandler = new OpenCVHandler();

        // Create CameraView class
        cameraView = new CameraView(this, recorder, openCVHandler);

        // Show hint on short click
        cameraView.setOnClickListener(view -> {
            Toast.makeText(MainActivity.this,
                    "Long press to open list and settings", Toast.LENGTH_LONG).show();
            brightnessControl();
        });

        // Add long click action
        cameraView.setOnLongClickListener(view -> {
            longClick();
            return true;
        });

        recordLayout = findViewById(R.id.record_layout);
        recordLayout.post(() -> runOnUiThread(() -> {
            // Calculate size and margins
            float sizeK = recordLayout.getHeight() / (float) settingsContainer.frameHeight;
            if (recordLayout.getWidth() / (float) settingsContainer.frameWidth < sizeK)
                sizeK = recordLayout.getWidth() / (float) settingsContainer.frameWidth;

            int actualSizeWidth = (int) (settingsContainer.frameWidth * sizeK);
            int actualSizeHeight = (int) (settingsContainer.frameHeight * sizeK);

            LinearLayout.LayoutParams layoutParam =
                    new LinearLayout.LayoutParams(actualSizeWidth, actualSizeHeight);
            layoutParam.topMargin = (recordLayout.getHeight() - actualSizeHeight) / 2;
            layoutParam.leftMargin = (recordLayout.getWidth() - actualSizeWidth) / 2;

            // Add CameraView to record_layout
            recordLayout.addView(cameraView, layoutParam);

            Log.i(TAG, "Camera preview started");
        }));
    }

    /**
     * Opens ViewActivity on long click on CameraView
     */
    private void longClick() {
        // Stop recording
        if (recorder != null && recorder.isRecording())
            recorder.stopRecording();

        // Pause application
        onPause();

        // Open View Activity
        startActivity(new Intent(getApplicationContext(), ViewActivity.class));
    }
}