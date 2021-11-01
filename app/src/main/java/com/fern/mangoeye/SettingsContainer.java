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

public class SettingsContainer {
    public int newMotionPercents;
    public int binaryThreshold;
    public int minMotionFrames;
    public boolean enableFlashlightOnMotion;
    public boolean drawTimestamp;
    public boolean contourEnabled;
    public boolean flipFrame;
    public boolean dimScreen;
    public int lowerBrightnessTimeout;
    public int warmupTimeout;
    public int stopRecordingTimeout;
    public int audioSampleRate;
    public String videoPreset;
    public int videoBitrate;
    public String videoFormat;
    public String videoContainer;
    public int frameWidth;
    public int frameHeight;
    public int frameRate;
    public String filesDirectory;


    /**
     * This class stores application settings
     */
    SettingsContainer() {
        this.newMotionPercents = 2;
        this.binaryThreshold = 5;
        this.minMotionFrames = 7;
        this.enableFlashlightOnMotion = true;
        this.drawTimestamp = true;
        this.contourEnabled = true;
        this.flipFrame = false;
        this.dimScreen = true;
        this.lowerBrightnessTimeout = 10000;
        this.warmupTimeout = 5000;
        this.stopRecordingTimeout = 10000;
        this.audioSampleRate = 22050;
        this.videoPreset = "superfast";
        this.videoBitrate = 2000;
        this.videoFormat = "mp4";
        this.videoContainer = "mp4";
        this.frameWidth = 1280;
        this.frameHeight = 720;
        this.frameRate = 30;
        this.filesDirectory = "";
    }
}
