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

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ViewActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getName();

    private final SimpleDateFormat dateFormatShort =
            new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US);
    private final SimpleDateFormat dateFormatFull =
            new SimpleDateFormat("dd.MM.yyyy EEEE HH:mm:ss", Locale.US);

    private File[] files;
    private List<List<String>> indexesAndDatesList;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Screen parameters
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Load activity
        setContentView(R.layout.activity_view);

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(view -> finish());

        // Settings button
        findViewById(R.id.btn_settings).setOnClickListener(view -> {
            // Open Settings Activity
            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        });

        // Exit button
        findViewById(R.id.btn_exit).setOnClickListener(view ->
                new AlertDialog.Builder(ViewActivity.this)
                        .setTitle(R.string.str_exit_app)
                        .setMessage(R.string.str_exit_confirmation)
                        .setPositiveButton(R.string.str_confirmation_btn_exit, (dialog, which) -> {
                            // Exit application
                            ActivityCompat.finishAffinity(this);
                            System.exit(0);
                        })
                        .setNegativeButton(R.string.str_confirmation_btn_cancel, (dialog, which) -> {
                            // Do nothing
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show());

        // Short click - view file
        ((ListView) findViewById(R.id.files_list)).setOnItemClickListener(
                (parent, view, position, id) -> {
                    File file =
                            files[Integer.parseInt(indexesAndDatesList.get(position).get(0))];

                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setDataAndType(FileProvider.getUriForFile(this,
                            getApplicationContext().getPackageName() + ".provider", file),
                            "video/*");
                    intent.setFlags(FLAG_GRANT_READ_URI_PERMISSION
                            | FLAG_GRANT_WRITE_URI_PERMISSION);
                    startActivity(intent);
                });

        // Long click - delete file
        ((ListView) findViewById(R.id.files_list))
                .setOnItemLongClickListener((arg0, arg1, pos, id) -> {
                    File file = files[Integer.parseInt(indexesAndDatesList.get(pos).get(0))];
                    String name = file.getName();
                    new AlertDialog.Builder(ViewActivity.this)
                            .setTitle(R.string.str_delete_file)
                            .setMessage(getString(R.string.str_delete_file_confirmation) + name)
                            .setPositiveButton(R.string.str_confirmation_btn_delete,
                                    (dialog, which) -> {
                                        // Delete file
                                        try {
                                            if (file.delete()) {
                                                Toast.makeText(this,
                                                        "File " + name + " deleted",
                                                        Toast.LENGTH_SHORT).show();
                                                // Update files
                                                updateView();
                                            }
                                        } catch (Exception e) {
                                            Toast.makeText(this,
                                                    "Error deleting file!",
                                                    Toast.LENGTH_SHORT).show();
                                            Log.e(TAG, "Error deleting file!", e);
                                        }
                                    })
                            .setNegativeButton(R.string.str_confirmation_btn_cancel,
                                    (dialog, which) -> {
                                        // Do nothing
                                    })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();

                    return true;
                });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Draw files
        updateView();
    }

    private void updateView() {
        // Reset array list
        indexesAndDatesList = new ArrayList<>();

        files = new File(MainActivity.getSettingsContainer().filesDirectory).listFiles();
        if (files != null)
            for (int i = 0; i < files.length; i++) {
                String name = getName(files[i].getName());
                if (name != null) {
                    List<String> list = new ArrayList<>();
                    list.add(String.valueOf(i));
                    list.add(name);
                    indexesAndDatesList.add(list);
                }
            }

        // Sort arrays
        Collections.sort(indexesAndDatesList, (o1, o2) -> {
            try {
                return dateFormatFull.parse(o2.get(1))
                        .compareTo(dateFormatFull.parse(o1.get(1)));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return -1;
        });

        // Make array
        String[] strings = new String[indexesAndDatesList.size()];
        for (int i = 0; i < indexesAndDatesList.size(); i++)
            strings[i] = indexesAndDatesList.get(i).get(1);

        // Connect ArrayAdapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.textview_layout, R.id.text_view, strings);
        ((ListView) findViewById(R.id.files_list)).setAdapter(adapter);
    }

    private String getName(String originalName) {
        // Remove extensions
        originalName = originalName.substring(0, originalName.indexOf("."));

        // Check file
        if (!originalName.contains("_"))
            return null;

        // Restore timestamp
        String[] parts = originalName.trim().split("_");
        if (parts.length != 6)
            return null;

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            stringBuilder.append(parts[i]);
            if (i <= 1)
                stringBuilder.append(".");
            else if (i == 2)
                stringBuilder.append(" ");
            else if (i <= 4)
                stringBuilder.append(":");
        }

        try {
            Date date = dateFormatShort.parse(stringBuilder.toString());
            return dateFormatFull.format(date);
        } catch (Exception ignored) {
            return null;
        }
    }
}