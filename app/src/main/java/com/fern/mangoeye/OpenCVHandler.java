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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * This class determines if there are new movements in the frame
 */
public class OpenCVHandler {
    private final Size blurSize = new Size(9, 9);
    private List<MatOfPoint> contours;
    private final Scalar contoursColor = new Scalar(0, 255, 0, 255);
    private Mat hierarchy;
    private Mat kernel;
    private Mat matGray;
    private Mat matRGBA;
    private Mat matRef;
    private Mat matRefFloat;
    private Mat matYUV;
    private boolean motionDetected;
    private int motionFrames;
    private final Scalar textBackgroundColor = new Scalar(255, 255, 255, 255);
    private final Scalar textForegroundColor = new Scalar(0, 0, 0, 255);
    private final Timestamp timestamp = new Timestamp(0);

    /**
     * Initialized class variables
     * @param width current frame width
     * @param height current frame height
     */
    public void initMats(int width, int height) {
        matYUV = new Mat((height / 2) + height, width, CvType.CV_8UC1);
        matRGBA = new Mat();
        matGray = new Mat();
        matRefFloat = new Mat();
        matRef = new Mat();
        kernel = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(5, 5));
        contours = new ArrayList<>();
        hierarchy = new Mat();
    }

    /**
     * Detects new motion in frame and converts it to RGBA
     * @param data new YUV frame from camera
     */
    public void feedNewYUVData(byte[] data) {
        // Retrieve new data from camera
        matYUV.put(0, 0, data);

        // Convert to RGBA
        Imgproc.cvtColor(matYUV, matRGBA, 95, 4);

        // Flip frame if needed
        if (MainActivity.getSettingsContainer().flipFrame) {
            Mat mat = matRGBA;
            Core.rotate(mat, mat, 1);
        }

        // Convert to gray
        Imgproc.cvtColor(matRGBA, matGray, 10, 1);

        // Blur gray frame
        Imgproc.GaussianBlur(matGray, matGray, blurSize, 0, 0);

        // Fill reference frame on first run
        if (matRefFloat.empty()) {
            matGray.convertTo(matRefFloat, CvType.CV_32FC1);
        }

        // Accumulate reference frame
        Imgproc.accumulateWeighted(matGray, matRefFloat, 0.5d);
        matRefFloat.convertTo(matRef, CvType.CV_8UC1);

        // Find difference in frames
        Core.absdiff(matGray, matRef, matGray);

        // Threshold difference
        Imgproc.threshold(matGray, matGray,
                MainActivity.getSettingsContainer().binaryThreshold, 255, 0);

        // Filter difference
        Imgproc.erode(matGray, matGray, kernel);
        Imgproc.dilate(matGray, matGray, kernel);

        // Clear contours list
        contours.clear();

        // Find contours
        Imgproc.findContours(matGray, contours, hierarchy, 0, 2);
        if (contours.size() > 0) {

            // Find largest contour
            int maxContourArea = 0;
            int maxContourAreaIndex = 0;
            for (int i = 0; i < contours.size(); i++) {
                int contourArea = (int) Imgproc.contourArea(contours.get(i));
                if (contourArea > maxContourArea) {
                    maxContourArea = contourArea;
                    maxContourAreaIndex = i;
                }
            }

            // Check if new motion detected
            if (maxContourArea > matGray.rows() * matGray.cols() *
                    (MainActivity.getSettingsContainer().newMotionPercents / 100)) {

                // Increment number of motion frames
                if (motionFrames < MainActivity.getSettingsContainer().minMotionFrames) {
                    motionFrames++;
                }

                // Draw largest contour
                if (MainActivity.getSettingsContainer().contourEnabled) {
                    Rect rect = Imgproc.boundingRect(contours.get(maxContourAreaIndex));
                    Imgproc.rectangle(matRGBA, rect.tl(), rect.br(), contoursColor, 2);
                }
            } else {
                // Decrement number of motion frames if no motion detected
                if (motionFrames > 0) {
                    motionFrames = motionFrames - 1;
                }
            }
        } else {
            // Decrement number of motion frames if no motion detected
            if (motionFrames> 0) {
                motionFrames = motionFrames - 1;
            }
        }

        // Set motionDetected flag if motionFrames more than minMotionFrames
        if (motionFrames >= MainActivity.getSettingsContainer().minMotionFrames) {
            motionDetected = true;
        } else if (motionFrames <= 0) {
            motionDetected = false;
        }

        // Set current timestamp
        timestamp.setTime(System.currentTimeMillis());

        // Draw current timestamp
        if (MainActivity.getSettingsContainer().drawTimestamp) {
            Imgproc.putText(matRGBA, timestamp.toString(), new Point(10, 50), 2,
                    1, textBackgroundColor, 2);
            Imgproc.putText(matRGBA, timestamp.toString(), new Point(10, 50), 2,
                    1, textForegroundColor, 1);
        }
    }

    /**
     * Resets reference frame
     */
    public void resetRefFrame() {
        this.matRefFloat = new Mat();
    }

    /**
     * @return current RGBA frame
     */
    public Mat getMatRGBA() {
        return this.matRGBA;
    }

    /**
     * @return true if new motion detected
     */
    public boolean isMotionDetected() {
        return this.motionDetected;
    }
}
