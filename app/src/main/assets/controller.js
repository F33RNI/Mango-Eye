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

window.onload = updateView;

let selectedElement = null;
let filenameToDelete = "";
let playingFilename = "";

/**
 * Updates scroll view
 */
function updateView() {
    // Main container
    const scrollContainer = document.getElementById("scroll-container");

    // Send and parse GET request
    const xmlHTTP = new XMLHttpRequest();
    xmlHTTP.onreadystatechange = function () {
        if (this.readyState === 4 && this.status === 200) {
            // Show overlay
            document.getElementById("overlay-container").style.display = "block";

            // Parse JSON
            const records = JSON.parse(this.responseText).records;

            // Remove previous items
            try {
                const scrollElements = document.getElementsByClassName("scroll-element");
                while (scrollElements[0]) {
                    scrollElements[0].parentNode.removeChild(scrollElements[0]);
                }
            } catch (ignored) { }

            // If there are videos
            if (records.length > 0) {
                // Create new elements
                let scrollElement;
                let thumbnailDiv;
                let dateDiv;
                let deleteDiv;
                let br;
                for (let i = 0; i < records.length; i++) {
                    // Create new scrollElement
                    scrollElement = document.createElement("div");
                    scrollElement.className = "scroll-element";
                    scrollElement.setAttribute("onclick", "selectElement(this);");

                    // Create new thumbnailDiv
                    thumbnailDiv = document.createElement("div");
                    thumbnailDiv.innerHTML = "<img src=\"data:image/jpeg;base64,"
                    + records[i].thumbnail.replace("\\/", "/") + "\">";
                    thumbnailDiv.onclick = function() {
                        viewVideo(records[i].filename, records[i].date, records[i].type);
                    };

                    // Create new dateDiv
                    dateDiv = document.createElement("div");
                    dateDiv.innerHTML = "<p>" + records[i].date + "</p>";

                    // Create new deleteDiv
                    deleteDiv = document.createElement("div");
                    deleteDiv.innerHTML = "<img src=\"delete.png\" class=\"icon-delete\">";
                    deleteDiv.onclick = function() {
                        deleteVideo(records[i].filename);
                    };

                    // Create BR element
                    br = document.createElement("br");
                    br.className = "scroll-element";

                    // Append all elements
                    scrollElement.appendChild(thumbnailDiv);
                    scrollElement.appendChild(dateDiv);
                    scrollElement.appendChild(deleteDiv);
                    scrollContainer.appendChild(scrollElement);
                    scrollContainer.appendChild(br);
                }
            }

            // No videos
            else {
                // Add empty text
                let scrollElement = document.createElement("div");
                scrollElement.className = "scroll-element";
                scrollElement.innerHTML = "<p style=\"margin: 1em;\">No videos</p>";

                // Append element
                scrollContainer.appendChild(scrollElement);
            }

            // Hide overlay
            document.getElementById("overlay-container").style.display = "none";
        }
    };

    // Send GET request to get JSON data
    xmlHTTP.open("GET", "/data.json", true);
    xmlHTTP.setRequestHeader("Content-Type", "application/json");
    xmlHTTP.send(null);
}

function selectElement(element) {
    if (selectedElement != null)
        selectedElement.style.backgroundColor = "";
    element.style.backgroundColor = "#94daff";
    selectedElement = element;
}

/**
 * Opens new video
 */
function viewVideo(filename, date, type) {
    // Store playing filename
    playingFilename = filename;

    // Show overlay
    document.getElementById("overlay-container").style.display = "block";

    // Start video
    const videoPlayer = document.createElement("video");
    videoPlayer.id = "video-player";
    videoPlayer.setAttribute("controls", "controls");
    videoPlayer.setAttribute("autoplay", "autoplay");
    videoPlayer.innerHTML = "<source src=\"" + "/" + filename + "\" type=\"" + type + "\">";
    document.getElementById("video-container").appendChild(videoPlayer);
    document.getElementById("player-name").innerHTML = "<a>" + date + "</a>";

    // Show player
    document.getElementById("video-container").style.display = "block";
}

function deleteVideoFromPlayer() {
    // Close player
    document.getElementById("video-container").style.display = "none";
    try { document.getElementById("video-player").remove(); } catch (ignored) { }

    // Ask for confirmation
    if (playingFilename != null && playingFilename.length > 0)
        deleteVideo(playingFilename);
    else
        confirmationCancel();
}

/**
 * Asks for confirmation to delete the video
 */
function deleteVideo(filename) {
    // Open confirmation dialog
    document.getElementById("overlay-container").style.display = "block";
    document.getElementById("confirmation-dialog").style.display = "block";

    // Mark file to delete
    filenameToDelete = filename;
}

function confirmationOK() {
    // Hide dialog
    document.getElementById("confirmation-dialog").style.display = "none";

    // Delete file
    if (filenameToDelete != null && filenameToDelete.length > 0)
        removeFile(filenameToDelete);
}

function confirmationCancel() {
    // Reset file
    filenameToDelete = "";

    // Hide dialog, video and overlay
    document.getElementById("confirmation-dialog").style.display = "none";
    document.getElementById("video-container").style.display = "none";
    try { document.getElementById("video-player").remove(); } catch (ignored) { }
    document.getElementById("overlay-container").style.display = "none";
}


function removeFile(filename) {
    // Update view after removing file
    const xmlHTTP = new XMLHttpRequest();
    xmlHTTP.onreadystatechange = function () {
        if (this.readyState === 4 && this.status === 200 && this.responseText == "ok") {
             updateView();
        }
    };

    // Send GET request to remove file
    xmlHTTP.open("GET", "/delete/" + filename, true);
    xmlHTTP.send(null);
}

