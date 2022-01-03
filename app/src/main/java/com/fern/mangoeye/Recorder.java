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

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;

import android.app.Activity;
import android.media.AudioRecord;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.opencv.core.Mat;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Recorder {
    private final String TAG = this.getClass().getName();

    private final Activity activity;
    private FFmpegFrameRecorder fFmpegFrameRecorder;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    private boolean recording = false;
    private Frame frameYUV = null;
    private long startTime;
    private byte[] rgbaBytes;

    Recorder(Activity activity) {
        this.activity = activity;
    }

    /**
     * Starts recording video and audio
     */
    public void startRecording(int frameWidth, int frameHeight, int frameRate) {
        try {
            Log.i(TAG, "Starting new recording");
            initRecorder(frameWidth, frameHeight, frameRate);
            fFmpegFrameRecorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            activity.runOnUiThread(() -> audioThread.start());
        } catch (Exception e) {
            Log.e(TAG, "Error starting record!", e);
            activity.runOnUiThread(() -> Toast.makeText(activity, R.string.error_starting_record,
                    Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * Stops recording video and audio
     */
    public void stopRecording() {
        // Stop audio thread
        if (audioRecordRunnable != null) {
            audioRecordRunnable.stop();
            try {
                audioThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error finishing audio thread!", e);
            }
        }
        audioRecordRunnable = null;
        audioThread = null;

        // Stop recorder
        if (fFmpegFrameRecorder != null && recording) {
            Log.i(TAG, "Finishing recording");
            try {
                //fFmpegFrameRecorder.flush();
                fFmpegFrameRecorder.stop();
                fFmpegFrameRecorder.release();
                //outputStream.flush();
                //outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Error finishing record!", e);
                activity.runOnUiThread(
                        () -> Toast.makeText(activity, R.string.error_finishing_record,
                                Toast.LENGTH_SHORT).show());
            }
            fFmpegFrameRecorder = null;
            frameYUV = null;
        }
        Log.i(TAG, "Recording finished");
        recording = false;
    }

    public void recordRGBAMat(Mat mat) {
        // Check if audio is recording
        if(audioRecordRunnable.getAudioRecord() == null
                || audioRecordRunnable.getAudioRecord().getRecordingState()
                != AudioRecord.RECORDSTATE_RECORDING) {
            startTime = System.currentTimeMillis();
            return;
        }

        // Convert to byte byte array and frame
        mat.get(0, 0, rgbaBytes);
        ((ByteBuffer) frameYUV.image[0].position(0)).put(rgbaBytes);

        try {
            // Record frame
            long t = 1000 * (System.currentTimeMillis() - startTime);
            if(t > fFmpegFrameRecorder.getTimestamp()) {
                fFmpegFrameRecorder.setTimestamp(t);
            }
            fFmpegFrameRecorder.record(frameYUV, AV_PIX_FMT_RGBA);
            //recorder.flush();
        }
        // Stop recording on error
        catch(FFmpegFrameRecorder.Exception e) {
            Log.e(TAG, "Error recording frame!", e);
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, R.string.error_recording_frame,
                            Toast.LENGTH_SHORT).show());
            stopRecording();
        }
    }

    public boolean isRecording() {
        return recording;
    }

    private void initRecorder(int frameWidth, int frameHeight, int frameRate) {
        Log.w(TAG, "init recorder");

        frameYUV = new Frame(frameWidth, frameHeight, Frame.DEPTH_UBYTE, 4);
        rgbaBytes = new byte[frameWidth * frameHeight * 4];
        Log.i(TAG, "frameYUV created");

        File file = getNewFile();
        if (file == null)
            return;

        Log.i(TAG, "Writing to file: " + file.getAbsolutePath());

        fFmpegFrameRecorder =
                new FFmpegFrameRecorder(file, frameWidth, frameHeight, 1);

        if (SettingsContainer.videoFormat.equals("mkv"))
            fFmpegFrameRecorder.setFormat("matroska");
        else
            fFmpegFrameRecorder.setFormat("mp4");
        fFmpegFrameRecorder.setVideoCodec(AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setAudioCodec(AV_CODEC_ID_AAC);
        fFmpegFrameRecorder.setPixelFormat(AV_PIX_FMT_YUV420P);
        fFmpegFrameRecorder.setSampleRate(22050);
        fFmpegFrameRecorder.setVideoOption("preset", "ultrafast");

        fFmpegFrameRecorder.setVideoQuality(0);
        fFmpegFrameRecorder.setVideoBitrate(2000 * 1024);
        fFmpegFrameRecorder.setFrameRate(frameRate);

        audioRecordRunnable = new AudioRecordRunnable(fFmpegFrameRecorder, 22050);
        audioThread = new Thread(audioRecordRunnable);

        Log.i(TAG, "Recorder initialize success");
    }

    private File getNewFile() {
        try {
            // Get timestamp
            SimpleDateFormat simpleDateFormat =
                    new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.US);
            String newFileName = simpleDateFormat.format(System.currentTimeMillis());

            File newFile = new File(SettingsContainer.externalFilesDir
                    + "/" + newFileName + "." + SettingsContainer.videoFormat);

            // Replace if file exists
            if (newFile.exists())
                if (!newFile.delete())
                    throw new Exception("Unable to replace file");

            return newFile;

        } catch (Exception e) {
            Log.e(TAG, "Error creating new file!", e);
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, R.string.error_creating_new_file,
                            Toast.LENGTH_LONG).show());
            ActivityCompat.finishAffinity(activity);
            //System.exit(0);
        }
        return null;
    }
}
