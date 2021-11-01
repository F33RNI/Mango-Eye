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

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;

import android.app.Activity;
import android.media.AudioRecord;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

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
    private int frameWidth, frameHeight;
    private int frameRate;

    Recorder(Activity activity) {
        this.activity = activity;
        this.frameWidth = MainActivity.getSettingsContainer().frameWidth;
        this.frameHeight = MainActivity.getSettingsContainer().frameHeight;
        this.frameRate = MainActivity.getSettingsContainer().frameRate;
    }

    /**
     * Starts recording video and audio
     */
    public void startRecording() {
        try {
            Log.i(TAG, "Starting new recording");
            initRecorder();
            fFmpegFrameRecorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();
        } catch (Exception e) {
            Toast.makeText(activity, "Error starting record!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error starting record!", e);
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
                Toast.makeText(activity, "Error finishing record!", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error finishing record!", e);
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
            Toast.makeText(activity, "Error recording frame!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error recording frame!", e);
            stopRecording();
        }
    }

    public boolean isRecording() {
        return recording;
    }

    public void setFrameSize(int frameWidth, int frameHeight) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    private void initRecorder() {
        Log.w(TAG, "init recorder");

        if (frameYUV == null) {
            frameYUV = new Frame(frameWidth, frameHeight, Frame.DEPTH_UBYTE, 4);
            rgbaBytes = new byte[frameWidth * frameHeight * 4];
            Log.i(TAG, "frameYUV created");
        }

        File file = getNewFile();
        if (file == null)
            return;

        Log.i(TAG, "Writing to file: " + file.getAbsolutePath());

        fFmpegFrameRecorder =
                new FFmpegFrameRecorder(file, frameWidth, frameHeight, 1);

        fFmpegFrameRecorder.setFormat(MainActivity.getSettingsContainer().videoFormat);
        fFmpegFrameRecorder.setVideoCodec(AV_CODEC_ID_H264);
        fFmpegFrameRecorder.setAudioCodec(AV_CODEC_ID_AAC);
        fFmpegFrameRecorder.setPixelFormat(AV_PIX_FMT_YUV420P);
        fFmpegFrameRecorder.setSampleRate(MainActivity.getSettingsContainer().audioSampleRate);
        fFmpegFrameRecorder.setVideoOption("preset",
                MainActivity.getSettingsContainer().videoPreset);

        fFmpegFrameRecorder.setVideoQuality(0);
        fFmpegFrameRecorder.setVideoBitrate(
                MainActivity.getSettingsContainer().videoBitrate * 1024);
        fFmpegFrameRecorder.setFrameRate(frameRate);

        audioRecordRunnable = new AudioRecordRunnable(fFmpegFrameRecorder,
                MainActivity.getSettingsContainer().audioSampleRate);
        audioThread = new Thread(audioRecordRunnable);

        Log.i(TAG, "Recorder initialize success");
    }

    private File getNewFile() {
        try {
            // Get timestamp
            SimpleDateFormat simpleDateFormat =
                    new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.US);
            String newFileName = simpleDateFormat.format(System.currentTimeMillis());

            File newFile = new File(MainActivity.getSettingsContainer().filesDirectory
                    + "/" + newFileName + "." +
                    MainActivity.getSettingsContainer().videoContainer);

            // Replace if file exists
            if (newFile.exists())
                if (!newFile.delete())
                    throw new Exception("Unable to replace file");

            return newFile;

        } catch (Exception e) {
            Toast.makeText(activity, "Error creating new file!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error creating new file!", e);
            ActivityCompat.finishAffinity(activity);
            //System.exit(0);
        }
        return null;
    }
}
