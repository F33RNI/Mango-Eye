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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.bytedeco.javacpp.Loader;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;

public class MainActivity extends AppCompatActivity implements View.OnLongClickListener {
    private final String TAG = this.getClass().getName();

    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int PERMISSION_REQUEST_CODE = 1;

    public static File settingsFile;

    private OpenCVHandler openCVHandler;

    /**
     * Checks if OpenCV library is loaded and asks for permissions
     */
    private final BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i("OpenCV", "OpenCV loaded successfully");

                // Request permissions
                if (hasPermissions(MainActivity.this, PERMISSIONS)) {
                    Log.i(TAG, "Permissions granted");

                    // Initialize OpenCVHandler class
                    openCVHandler.initView();
                } else {
                    // Grant permissions
                    Log.w(TAG, "Not all permissions granted");
                    ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS,
                            PERMISSION_REQUEST_CODE);
                }
            }
            else {
                super.onManagerConnected(status);
                Toast.makeText(MainActivity.this, R.string.opencv_not_loaded,
                        Toast.LENGTH_LONG).show();

                // Exit back to home screen
                closeActivity();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Open layout
        setContentView(R.layout.activity_main);

        // Remove action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Load and parse settings
        if (!SettingsContainer.settingsLoaded) {
            settingsFile = new File(getBaseContext().getExternalFilesDir(null),
                    "settings.json");
            new SettingsHandler(settingsFile, this).readSettings();
        }

        // Connect layout long click
        findViewById(R.id.mainLayout).setOnLongClickListener(this);

        // Initialize OpenCVHandler class
        openCVHandler = new OpenCVHandler(findViewById(R.id.javaCameraView), this, new Recorder(this));

        // Load FFMPEG libraries
        Log.i(TAG, "Loading ffmpeg libraries");
        Loader.load(org.bytedeco.ffmpeg.global.avutil.class);
        Loader.load(org.bytedeco.ffmpeg.global.avcodec.class);
        Loader.load(org.bytedeco.ffmpeg.global.avformat.class);

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

        // Start web server
        if (!WebServer.isServerListening()) {
            WebServer.setServerPort(SettingsContainer.serverPort);
            WebServer.startServer(this);
        }
    }

    // Activity long press
    @Override
    public boolean onLongClick(View view) {
        // Start settings activity on long click
        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        System.gc();
        finish();
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Enable OpenCV view
        if (openCVHandler != null && openCVHandler.isInitialized())
            openCVHandler.getCameraBridgeViewBase().enableView();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Disable OpenCV view
        if (openCVHandler != null && openCVHandler.isInitialized())
            openCVHandler.getCameraBridgeViewBase().disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Disable OpenCV view
        if (openCVHandler != null && openCVHandler.isInitialized())
            openCVHandler.getCameraBridgeViewBase().disableView();
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

            // Initialize OpenCVHandler class
            openCVHandler.initView();
        }
        else {
            Toast.makeText(this, R.string.permissions_not_granted,
                    Toast.LENGTH_LONG).show();

            // Exit back to home screen
            closeActivity();
        }
    }

    private void closeActivity() {
        System.gc();
        finishAffinity();
        finish();
    }
}