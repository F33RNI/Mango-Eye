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
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * This class provides reading / saving of settings (the SettingsContainer class) to a JSON file
 */
public class SettingsHandler {
    private final static String TAG = SettingsHandler.class.getName();
    
    private final File settingsFile;
    private final Activity activity;

    private boolean retryFlag = false;

    /**
     * This class reads and saves application settings to a JSON file
     * @param settingsFile JSON file for settings
     * @param activity Activity for toast and finish() function
     */
    public SettingsHandler(File settingsFile,
                           Activity activity) {
        this.settingsFile = settingsFile;
        this.activity = activity;
    }

    /**
     * Reads settings from JSON file
     */
    public void readSettings() {
        // Log settings file location
        Log.i(TAG, "Reading settings from " + settingsFile.getAbsolutePath());

        if (!settingsFile.exists())
            saveSettings(settingsFile, activity);
        try {
            // Read file to JSONObject
            FileReader fileReader = new FileReader(settingsFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuilder.append(line).append("\n");
                line = bufferedReader.readLine();
            }
            bufferedReader.close();
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());

            // Parse json object to SettingsContainer variables
            SettingsContainer.externalFilesDir = jsonObject.getString("storage");
            SettingsContainer.cameraID = jsonObject.getInt("camera_id");
            SettingsContainer.enableFlashlight = jsonObject.getBoolean("enable_flashlight");
            SettingsContainer.videoFormat = jsonObject.getString("video_format");
            SettingsContainer.sensitivity = jsonObject.getInt("sensitivity");
            SettingsContainer.sizeThreshold = jsonObject.getDouble("size_threshold");
            SettingsContainer.serverPort = jsonObject.getInt("server_port");

            // Check externalFilesDir
            boolean storageAccepted = false;
            try {
                File testDirectory = new File(SettingsContainer.externalFilesDir);
                if (!testDirectory.exists()) {
                    if (!testDirectory.mkdirs())
                        throw new Exception();
                }
                if (testDirectory.exists() && testDirectory.isDirectory())
                    storageAccepted = true;
            } catch (Exception ignored) { }

            // If storage is not correct
            if (!storageAccepted) {
                // Set first storage
                SettingsContainer.externalFilesDir
                        = activity.getBaseContext().getExternalFilesDirs(null)[0]
                        .getAbsolutePath();

                // Save with new storage
                saveSettings(settingsFile, activity);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing settings!", e);

            // Remove file and try again
            if (!retryFlag) {
                if (settingsFile.delete()) {
                    Log.w(TAG, "Retrying to read settings");
                    readSettings();
                    retryFlag = true;
                }
            }
            // Exit application
            else {
                System.gc();
                activity.finishAffinity();
                //System.exit(0);
            }
        }
    }

    /**
     * Saves settings to JSON file
     */
    public static void saveSettings(File settingsFile,
                                    Activity activity) {
        try {
            // Create new JSONObject
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("storage", SettingsContainer.externalFilesDir);
            jsonObject.put("camera_id", SettingsContainer.cameraID);
            jsonObject.put("enable_flashlight", SettingsContainer.enableFlashlight);
            jsonObject.put("video_format", SettingsContainer.videoFormat);
            jsonObject.put("sensitivity", SettingsContainer.sensitivity);
            jsonObject.put("size_threshold", SettingsContainer.sizeThreshold);
            jsonObject.put("server_port", SettingsContainer.serverPort);

            // Write JSONObject to file
            FileWriter fileWriter = new FileWriter(settingsFile);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(jsonObject.toString());
            bufferedWriter.close();

        } catch (Exception e) {
            // Show error message
            Toast.makeText(activity, R.string.error_saving_settings,
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error saving settings!", e);

            // Exit application
            System.gc();
            activity.finishAffinity();
            //System.exit(0);
        }
    }
}
