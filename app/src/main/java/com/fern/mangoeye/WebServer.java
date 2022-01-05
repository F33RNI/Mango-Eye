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

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import com.koushikdutta.async.http.server.AsyncHttpServer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class WebServer {
    private static final String TAG = WebServer.class.getName();

    public static String serverHost = "";
    public static int serverPort = 5000;
    private static boolean timerStarted = false;

    private static final SimpleDateFormat simpleDateFormat =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);

    private static final List<File> files = new ArrayList<>();

    private static AsyncHttpServer server;
    private static boolean serverListening = false;

    /**
     * Start the server
     * @param context Android context
     */
    public static void startServer(Context context) {
        try {
            Log.i(TAG, "Trying to start the server");

            // Initialize and start IP checking timer
            if (!timerStarted) {
                Timer timer = new Timer();
                TimerTask timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        serverHost = WebServer.getIPAddress(true);
                    }
                };
                timer.scheduleAtFixedRate(timerTask, 1000, 10000);
                timerStarted = true;
            }

            server = new AsyncHttpServer();

            // Main page (index.html)
            server.get("/", (request, response) -> {
                response.setContentType("text/html");
                response.send(readAssetToString("index.html", context));
            });

            // CSS stylesheet file
            server.get("/stylesheet.css", (request, response) -> {
                response.setContentType("text/css");
                response.send(readAssetToString("stylesheet.css", context));
            });

            // JS file
            server.get("/controller.js", (request, response) -> {
                response.setContentType("text/javascript");
                response.send(readAssetToString("controller.js", context));
            });

            // Delete icon
            server.get("/delete.png", (request, response) -> {
                response.setContentType("image/png");
                try {
                    InputStream inputStream = context.getAssets().open("delete.png");
                    response.sendStream(inputStream, inputStream.available());
                } catch (Exception e) {
                    response.send("");
                }
            });

            // MP4 video
            server.get("/.._.._...._.._.._..\\.mp4", (request, response) -> {
                response.setContentType("video/mp4");
                try {
                    String[] urlPath = request.getPath().split("/");
                    InputStream inputStream =
                            new FileInputStream(SettingsContainer.externalFilesDir
                                    + "/" + urlPath[urlPath.length - 1]);
                    response.sendStream(inputStream, inputStream.available());
                } catch (Exception e) {
                    response.send("");
                }
            });

            // MKV video
            server.get("/.._.._...._.._.._..\\.mkv", (request, response) -> {
                response.setContentType("video/x-matroska");
                try {
                    String[] urlPath = request.getPath().split("/");
                    InputStream inputStream =
                            new FileInputStream(SettingsContainer.externalFilesDir
                                    + "/" + urlPath[urlPath.length - 1]);
                    response.sendStream(inputStream, inputStream.available());
                } catch (Exception e) {
                    response.send("");
                }
            });

            // Delete video
            server.get("/delete/.._.._...._.._.._..\\....", (request, response) -> {
                try {
                    String[] urlPath = request.getPath().split("/");
                    if (new File(SettingsContainer.externalFilesDir
                            + "/" + urlPath[urlPath.length - 1]).delete())
                        response.code(200);
                        response.send("ok");
                } catch (Exception e) {
                    response.send("");
                }
            });

            // JSON data file
            server.get("/data.json", (request, response) -> {
                response.setContentType("application/json");
                try {
                    JSONArray jsonArray = new JSONArray();

                    File[] filesArray = new File(SettingsContainer.externalFilesDir).listFiles();
                    assert filesArray != null;

                    files.clear();
                    files.addAll(Arrays.asList(filesArray));

                    // Sort array of files by date
                    Collections.sort(files, (o1, o2) -> {
                        try {
                            return new Date(o2.lastModified())
                                    .compareTo(new Date(o1.lastModified()));
                        } catch (Exception ignored) { }
                        return -1;
                    });

                    for (File file : files) {
                        // Don't add file if recording is in process
                        if (!file.getName().equals(Recorder.recordingFileName)) {
                            String thumbnail = getThumbnailFromFile(file);
                            if (thumbnail.length() > 0) {
                                JSONObject item = new JSONObject();
                                item.put("filename", file.getName());
                                item.put("type",
                                        file.getName().toLowerCase(Locale.ROOT).endsWith("mkv") ?
                                                "video/x-matroska" : "video/mp4");
                                item.put("date", simpleDateFormat.format(file.lastModified()));
                                item.put("thumbnail", thumbnail);
                                jsonArray.put(item);
                            }
                        }
                    }

                    JSONObject jsonData = new JSONObject();
                    jsonData.put("records", jsonArray);

                    response.code(200);
                    response.send(jsonData.toString());
                } catch (Exception e) {
                    response.send("{\"records\":[]}");
                }
            });

            // Start server
            server.listen(serverPort);

            // Set flag to true
            serverListening = true;

            // Print hostname and port to the logs
            Log.i(TAG, "The server is running on " +
                    getIPAddress(true) + ":" + serverPort);
        } catch (Exception e) {
            Log.e(TAG, "Error starting the server!", e);
            System.exit(1);
        }
    }

    /**
     * Stops the server
     */
    public static void stopServer() {
        Log.i(TAG,"Stopping the server");

        // Stop the server
        server.stop();

        // Clear the flag
        serverListening = false;
    }

    /**
     * @return true if the server is listening selected port
     */
    public static boolean isServerListening() {
        return serverListening;
    }

    /**
     * Sets server port
     * @param serverPort server port as integer
     */
    public static void setServerPort(int serverPort) {
        WebServer.serverPort = serverPort;
    }

    /**
     * Reads assists file to string
     * @param fileName name of the file
     * @param context Android context
     * @return content of the file
     */
    private static String readAssetToString(String fileName, Context context) {
        String response = "";
        try {
            InputStream inputStream = context.getAssets().open(fileName);

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();

            for (String line; (line = bufferedReader.readLine()) != null; ) {
                stringBuilder.append(line).append('\n');
            }

            inputStream.close();
            bufferedReader.close();

            return stringBuilder.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error reading " + fileName + " file from assets!", e);
        }

        return response;
    }

    /**
     * Reads thumbnail from the video file as base64
     * @param file video file
     * @return base64 string without new line character
     */
    private static String getThumbnailFromFile(File file) {
        try {
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(),
                    MediaStore.Video.Thumbnails.MICRO_KIND);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Bitmap.createScaledBitmap(bitmap, 80, 60, true)
                    .compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) { }
        return "";
    }

    /**
     * Gets IP address from first non-localhost interface
     * @param useIPv4   true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        assert sAddr != null;
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return "";
    }
}
