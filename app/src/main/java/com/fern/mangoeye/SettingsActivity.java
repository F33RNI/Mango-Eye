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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getName();

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screen parameters
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Load activity
        setContentView(R.layout.activity_settings);

        // Back button
        findViewById(R.id.btn_settings_back).setOnClickListener(view -> finish());

        // Restore button
        findViewById(R.id.btn_settings_restore).setOnClickListener(view ->
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(R.string.str_dialog_title_restore)
                        .setMessage(R.string.str_dialog_reset_settings)
                        .setPositiveButton(R.string.str_confirmation_btn_restore, (dialog, which) -> {
                            // Reset settings to default
                            MainActivity.setSettingsContainer(new SettingsContainer());
                            SettingsHandler.saveSettings(MainActivity.settingsFile, this);
                            updateView();
                        })
                        .setNegativeButton(R.string.str_confirmation_btn_cancel, (dialog, which) -> {
                            // Do nothing
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show());

        // Connect seek bars
        ((SeekBar) findViewById(R.id.seekBarDetection)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        ((TextView) findViewById(R.id.textDetection))
                                .setText(String.valueOf(i + 1));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

        ((SeekBar) findViewById(R.id.seekBarThreshold)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        ((TextView) findViewById(R.id.textThreshold))
                                .setText(String.valueOf(i + 1));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

        ((SeekBar) findViewById(R.id.seekMotionFrames)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                        ((TextView) findViewById(R.id.textMotionFrames))
                                .setText(String.valueOf(i + 1));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

        // Connect spinner
        ((Spinner) findViewById(R.id.spinnerStorages))
                .setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parentView, View selectedItemView
                            , int position, long id) {
                        SettingsContainer settingsContainer = MainActivity.getSettingsContainer();
                        settingsContainer.filesDirectory =
                                MainActivity.externalFilesDirs[position];
                        MainActivity.setSettingsContainer(settingsContainer);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parentView) {
                    }

                });

        // Load view
        updateView();

        // Save button
        findViewById(R.id.btn_save_settings).setOnClickListener(view -> updateSettings());
    }

    private void updateView() {
        // Seek Bars
        ((SeekBar) findViewById(R.id.seekBarDetection)).setMax(19);
        ((SeekBar) findViewById(R.id.seekBarDetection)).setProgress(
                MainActivity.getSettingsContainer().newMotionPercents - 1);

        ((SeekBar) findViewById(R.id.seekBarThreshold)).setMax(14);
        ((SeekBar) findViewById(R.id.seekBarThreshold)).setProgress(
                MainActivity.getSettingsContainer().binaryThreshold - 1);

        ((SeekBar) findViewById(R.id.seekMotionFrames)).setMax(14);
        ((SeekBar) findViewById(R.id.seekMotionFrames)).setProgress(
                MainActivity.getSettingsContainer().minMotionFrames - 1);

        // System storages
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_layout, R.id.text_view_spinner, MainActivity.externalFilesDirs);
        ((Spinner) findViewById(R.id.spinnerStorages)).setAdapter(adapter);
        ((Spinner) findViewById(R.id.spinnerStorages)).setSelection(MainActivity.getStorageIndex());

        // Switches
        ((Switch) findViewById(R.id.switchFlashlight)).setChecked(
                MainActivity.getSettingsContainer().enableFlashlightOnMotion);
        ((Switch) findViewById(R.id.switchEnableTimestamp)).setChecked(
                MainActivity.getSettingsContainer().drawTimestamp);
        ((Switch) findViewById(R.id.switchContour)).setChecked(
                MainActivity.getSettingsContainer().contourEnabled);
        ((Switch) findViewById(R.id.switchFlip)).setChecked(
                MainActivity.getSettingsContainer().flipFrame);
        ((Switch) findViewById(R.id.switchDimScreen)).setChecked(
                MainActivity.getSettingsContainer().dimScreen);

        // Timeouts
        ((EditText) findViewById(R.id.editDimTimeot)).setText(String.valueOf(
                MainActivity.getSettingsContainer().lowerBrightnessTimeout / 1000));
        ((EditText) findViewById(R.id.editWarmupTimeot)).setText(String.valueOf(
                MainActivity.getSettingsContainer().warmupTimeout / 1000));
        ((EditText) findViewById(R.id.editRecordingTimeout)).setText(String.valueOf(
                MainActivity.getSettingsContainer().stopRecordingTimeout / 1000));

        // Other fields
        ((EditText) findViewById(R.id.editAudioRate)).setText(String.valueOf(
                MainActivity.getSettingsContainer().audioSampleRate));
        ((EditText) findViewById(R.id.editVideoPreset)).setText(MainActivity
                .getSettingsContainer().videoPreset);
        ((EditText) findViewById(R.id.editVideoBitrate)).setText(String.valueOf(
                MainActivity.getSettingsContainer().videoBitrate));
        ((EditText) findViewById(R.id.editFormat)).setText(MainActivity
                .getSettingsContainer().videoFormat);
        ((EditText) findViewById(R.id.editContainer)).setText(MainActivity
                .getSettingsContainer().videoContainer);
        ((EditText) findViewById(R.id.editFrameWidth)).setText(String.valueOf(
                MainActivity.getSettingsContainer().frameWidth));
        ((EditText) findViewById(R.id.editFrameHeight)).setText(String.valueOf(
                MainActivity.getSettingsContainer().frameHeight));
        ((EditText) findViewById(R.id.editFrameRate)).setText(String.valueOf(
                MainActivity.getSettingsContainer().frameRate));
    }

    private void updateSettings() {
        try {
            // Seek bars
            SettingsContainer settingsContainer = MainActivity.getSettingsContainer();

            settingsContainer.newMotionPercents =
                    Integer.parseInt(((TextView)
                            findViewById(R.id.textDetection)).getText().toString());
            settingsContainer.binaryThreshold =
                    Integer.parseInt(((TextView)
                            findViewById(R.id.textThreshold)).getText().toString());
            settingsContainer.minMotionFrames =
                    Integer.parseInt(((TextView)
                            findViewById(R.id.textMotionFrames)).getText().toString());

            // Switches
            settingsContainer.enableFlashlightOnMotion =
                    ((Switch) findViewById(R.id.switchFlashlight)).isChecked();
            settingsContainer.drawTimestamp =
                    ((Switch) findViewById(R.id.switchEnableTimestamp)).isChecked();
            settingsContainer.contourEnabled =
                    ((Switch) findViewById(R.id.switchContour)).isChecked();
            settingsContainer.flipFrame =
                    ((Switch) findViewById(R.id.switchFlip)).isChecked();
            settingsContainer.dimScreen =
                    ((Switch) findViewById(R.id.switchDimScreen)).isChecked();

            // Timeouts
            settingsContainer.lowerBrightnessTimeout =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editDimTimeot)).getText().toString()) * 1000;
            settingsContainer.warmupTimeout =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editWarmupTimeot)).getText().toString()) * 1000;
            settingsContainer.stopRecordingTimeout =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editRecordingTimeout)).getText().toString()) * 1000;

            // Other fields
            settingsContainer.audioSampleRate =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editAudioRate)).getText().toString());
            settingsContainer.videoPreset = ((EditText)
                    findViewById(R.id.editVideoPreset)).getText().toString();
            settingsContainer.videoBitrate =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editVideoBitrate)).getText().toString());
            settingsContainer.videoFormat = ((EditText)
                    findViewById(R.id.editFormat)).getText().toString();
            settingsContainer.videoContainer = ((EditText)
                    findViewById(R.id.editContainer)).getText().toString();
            settingsContainer.frameWidth =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editFrameWidth)).getText().toString());
            settingsContainer.frameHeight =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editFrameHeight)).getText().toString());
            settingsContainer.frameRate =
                    Integer.parseInt(((EditText)
                            findViewById(R.id.editFrameRate)).getText().toString());

            MainActivity.setSettingsContainer(settingsContainer);
            SettingsHandler.saveSettings(MainActivity.settingsFile, this);
            Toast.makeText(this, "Settings saved successfully",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Wrong settings provided! Nothing saved",
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Wrong settings provided!", e);
        }
        finish();
    }
}