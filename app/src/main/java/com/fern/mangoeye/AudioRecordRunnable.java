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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.nio.ShortBuffer;

class AudioRecordRunnable implements Runnable {
    private final String TAG = this.getClass().getName();

    private final int sampleRate;
    private final FFmpegFrameRecorder fFmpegFrameRecorder;

    private boolean threadRunning;
    private AudioRecord audioRecord;

    /**
     * This class organizes the ability to record audio to a video file
     * @param fFmpegFrameRecorder FFmpegFrameRecorder object
     * @param sampleRate sampling rate in Hz
     */
    AudioRecordRunnable(FFmpegFrameRecorder fFmpegFrameRecorder, int sampleRate) {
        this.fFmpegFrameRecorder = fFmpegFrameRecorder;
        this.sampleRate = sampleRate;
    }


    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        // Set audio thread priority
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        // Get buffer size
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        // Create AudioRecord object
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        // Allocate buffer
        ShortBuffer audioData = ShortBuffer.allocate(bufferSize);

        Log.i(TAG, "Starting audio recording");
        audioRecord.startRecording();

        int bufferReadResult;

        // Start main loop
        threadRunning = true;

        // ffmpeg_audio encoding loop
        while(threadRunning) {
            // Read buffer
            bufferReadResult = audioRecord.read(audioData.array(), 0,
                    audioData.capacity());
            audioData.limit(bufferReadResult);

            if(bufferReadResult > 0) {
                // Write buffer to the recorder
                try {
                    if (fFmpegFrameRecorder != null)
                        fFmpegFrameRecorder.recordSamples(audioData);
                } catch (FFmpegFrameRecorder.Exception e) {
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        Log.v(TAG, "AudioThread Finished, release audioRecord");

        /* encoding finish, release recorder */
        if(audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            Log.v(TAG, "audioRecord released");
        }
    }

    /**
     * @return current audioRecord
     */
    public AudioRecord getAudioRecord() {
        return audioRecord;
    }

    /**
     * Stops recording
     */
    public void stop() {
        threadRunning = false;
    }
}