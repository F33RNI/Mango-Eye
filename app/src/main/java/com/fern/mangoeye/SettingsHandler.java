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
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

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
            SettingsContainer settingsContainer = MainActivity.getSettingsContainer();

            settingsContainer.newMotionPercents = (int) jsonObject.get("new_motion_percents");
            settingsContainer.binaryThreshold = (int) jsonObject.get("binary_threshold");
            settingsContainer.minMotionFrames = (int) jsonObject.get("min_motion_frames");
            settingsContainer.enableFlashlightOnMotion =
                    (boolean) jsonObject.get("enable_flashlight_on_motion");
            settingsContainer.drawTimestamp = (boolean) jsonObject.get("draw_timestamp");
            settingsContainer.contourEnabled = (boolean) jsonObject.get("contour_enabled");
            settingsContainer.flipFrame = (boolean) jsonObject.get("flip_frame");
            settingsContainer.dimScreen = (boolean) jsonObject.get("dim_screen");
            settingsContainer.lowerBrightnessTimeout =
                    (int) jsonObject.get("lower_brightness_timeout");
            settingsContainer.warmupTimeout = (int) jsonObject.get("warmup_timeout");
            settingsContainer.stopRecordingTimeout = (int) jsonObject.get("stop_recording_timeout");
            settingsContainer.audioSampleRate = (int) jsonObject.get("audio_sample_rate");
            settingsContainer.videoPreset = (String) jsonObject.get("video_preset");
            settingsContainer.videoBitrate = (int) jsonObject.get("video_bitrate");
            settingsContainer.videoFormat = (String) jsonObject.get("video_format");
            settingsContainer.videoContainer = (String) jsonObject.get("video_container");
            settingsContainer.frameWidth = (int) jsonObject.get("frame_width");
            settingsContainer.frameHeight = (int) jsonObject.get("frame_height");
            settingsContainer.frameRate = (int) jsonObject.get("frame_rate");
            settingsContainer.filesDirectory = (String) jsonObject.get("files_directory");

            MainActivity.setSettingsContainer(settingsContainer);
        } catch (Exception e) {
            // Show error message
            Toast.makeText(activity, "Error parsing settings!",
                    Toast.LENGTH_SHORT).show();
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
                activity.finish();
                System.gc();
                System.exit(0);
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
            jsonObject.put("new_motion_percents",
                    MainActivity.getSettingsContainer().newMotionPercents);
            jsonObject.put("binary_threshold",
                    MainActivity.getSettingsContainer().binaryThreshold);
            jsonObject.put("min_motion_frames",
                    MainActivity.getSettingsContainer().minMotionFrames);
            jsonObject.put("enable_flashlight_on_motion",
                    MainActivity.getSettingsContainer().enableFlashlightOnMotion);
            jsonObject.put("draw_timestamp",
                    MainActivity.getSettingsContainer().drawTimestamp);
            jsonObject.put("contour_enabled",
                    MainActivity.getSettingsContainer().contourEnabled);
            jsonObject.put("flip_frame",
                    MainActivity.getSettingsContainer().flipFrame);
            jsonObject.put("dim_screen",
                    MainActivity.getSettingsContainer().dimScreen);
            jsonObject.put("lower_brightness_timeout",
                    MainActivity.getSettingsContainer().lowerBrightnessTimeout);
            jsonObject.put("warmup_timeout",
                    MainActivity.getSettingsContainer().warmupTimeout);
            jsonObject.put("stop_recording_timeout",
                    MainActivity.getSettingsContainer().stopRecordingTimeout);
            jsonObject.put("audio_sample_rate",
                    MainActivity.getSettingsContainer().audioSampleRate);
            jsonObject.put("video_preset",
                    MainActivity.getSettingsContainer().videoPreset);
            jsonObject.put("video_bitrate",
                    MainActivity.getSettingsContainer().videoBitrate);
            jsonObject.put("video_format",
                    MainActivity.getSettingsContainer().videoFormat);
            jsonObject.put("video_container",
                    MainActivity.getSettingsContainer().videoContainer);
            jsonObject.put("frame_width",
                    MainActivity.getSettingsContainer().frameWidth);
            jsonObject.put("frame_height",
                    MainActivity.getSettingsContainer().frameHeight);
            jsonObject.put("frame_rate",
                    MainActivity.getSettingsContainer().frameRate);
            jsonObject.put("files_directory",
                    MainActivity.getSettingsContainer().filesDirectory);


            // Write JSONObject to file
            FileWriter fileWriter = new FileWriter(settingsFile);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(jsonObject.toString());
            bufferedWriter.close();

        } catch (Exception e) {
            // Show error message
            Toast.makeText(activity, "Error saving settings!",
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error saving settings!", e);

            // Exit application
            activity.finish();
            // System.exit(0);
        }
    }
}
