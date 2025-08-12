package com.tejas.artifactgenerator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final int REQUEST_IMAGE_TEST_CASE = 111;
    private static final int REQUEST_IMAGE_PRECONDITION = 112;
    private EditText editStepCount;
    private Button btnGenerateSteps, btnCapture, btnShareDoc, btnCaptureTestCase, btnCapturePreconditions;
    private LinearLayout stepButtonsLayout;
    private TextView textStatus, textZeraExtract;
    private int selectedStep = -1;
    private File photoFile;
    private File zeraPhotoFile;
    private File generatedDocFile;
    private Uri testCaseImageUri;
    private Uri preconditionImageUri;
    private final Map<Integer, List<String>> stepImages = new HashMap<>();
    private String testCaseId = "";
    private String testCaseTitle = "";
    private String testCasePreconditions = "";
    private String selectedPrefix = "C1"; // default
    private static final String PREFS_NAME = "TestCasePrefs";
    private static final String KEY_SELECTED_PREFIX = "selectedPrefix";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);

        editStepCount = findViewById(R.id.editStepCount);
        btnGenerateSteps = findViewById(R.id.btnGenerateSteps);
        btnCapture = findViewById(R.id.btnCapture);
        btnShareDoc = findViewById(R.id.btnShareDoc);
        btnCaptureTestCase = findViewById(R.id.btnCaptureTestCase);
        btnCapturePreconditions = findViewById(R.id.btnCapturePreconditions);
        stepButtonsLayout = findViewById(R.id.stepButtonsLayout);
        textStatus = findViewById(R.id.textStatus);

        btnGenerateSteps.setOnClickListener(v -> {
            String stepCountStr = editStepCount.getText().toString().trim();
            if (stepCountStr.isEmpty()) {
                Toast.makeText(this, "Please enter number of steps", Toast.LENGTH_SHORT).show();
                return;
            }
            int stepCount = Integer.parseInt(stepCountStr);
            generateStepButtons(stepCount);
        });

        btnCapture.setOnClickListener(v -> {
            if (selectedStep == -1) {
                Toast.makeText(this, "Please select a step first", Toast.LENGTH_SHORT).show();
                return;
            }
            captureImage();
        });

        Spinner spinnerCasePrefix = findViewById(R.id.spinnerCasePrefix);

// Load saved prefix from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedPrefix = prefs.getString(KEY_SELECTED_PREFIX, "C1");

// Create adapter
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.case_prefix_array,
                R.layout.simple_spinner
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCasePrefix.setAdapter(adapter);


// Set spinner to saved selection
        int savedPosition = adapter.getPosition(selectedPrefix);
        spinnerCasePrefix.setSelection(savedPosition);

        spinnerCasePrefix.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPrefix = parent.getItemAtPosition(position).toString();

                // Save selection to SharedPreferences
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putString(KEY_SELECTED_PREFIX, selectedPrefix);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPrefix = "C1";
            }
        });



        findViewById(R.id.btnGenerateDoc).setOnClickListener(v -> generateWordDocument());

        // Somewhere in MainActivity
        findViewById(R.id.btnContactUs).setOnClickListener(
                v -> startActivity(new Intent(MainActivity.this, ContactActivity.class))
        );


        btnShareDoc.setOnClickListener(v -> {
            if (generatedDocFile != null && generatedDocFile.exists()) {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", generatedDocFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share Word Document"));
            } else {
                Toast.makeText(this, "No document to share", Toast.LENGTH_SHORT).show();
            }
        });

        btnCaptureTestCase.setOnClickListener(v -> dispatchZeraPictureIntent(REQUEST_IMAGE_TEST_CASE));
        btnCapturePreconditions.setOnClickListener(v -> dispatchZeraPictureIntent(REQUEST_IMAGE_PRECONDITION));
    }

    private void dispatchZeraPictureIntent(int requestCode) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            zeraPhotoFile = createImageFile();
            Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".provider", zeraPhotoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);

            if (requestCode == REQUEST_IMAGE_TEST_CASE) {
                testCaseImageUri = photoURI;
            } else {
                preconditionImageUri = photoURI;
            }
            startActivityForResult(takePictureIntent, requestCode);
        } catch (IOException ex) {
            Toast.makeText(this, "Failed to create Zera image file", Toast.LENGTH_SHORT).show();
        }
    }

    private Map<Integer, Boolean> stepToggleMap = new HashMap<>();

    private void generateStepButtons(int stepCount) {
        stepButtonsLayout.removeAllViews();
        selectedStep = -1;
        btnCapture.setVisibility(View.GONE);
        textStatus.setText("Step buttons generated. Click one to begin capturing images.");

        for (int i = 1; i <= stepCount; i++) {
            int stepNumber = i;

            // Create a horizontal layout for the step row
            LinearLayout stepRow = new LinearLayout(this);
            stepRow.setOrientation(LinearLayout.HORIZONTAL);
            stepRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            stepRow.setPadding(0, 10, 0, 10);

            // Step Button
            Button stepButton = new Button(this);
            stepButton.setText("Step " + stepNumber);
            stepButton.setOnClickListener(v -> {
                selectedStep = stepNumber;
                btnCapture.setVisibility(View.VISIBLE);
                updateStatus();
            });

            // Toggle Switch
            Switch stepToggle = new Switch(this);
            stepToggle.setText("2 Imgs");
            stepToggle.setTextColor(Color.RED);
            stepToggle.setChecked(false); // default is 1 image (unchecked)

            // Save toggle state to map
            stepToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                stepToggleMap.put(stepNumber, isChecked);
                // Optional: Log or toast
                // Toast.makeText(this, "Step " + stepNumber + ": " + (isChecked ? "2 images" : "1 image"), Toast.LENGTH_SHORT).show();
            });

            // Add both to row
            stepRow.addView(stepButton);
            stepRow.addView(stepToggle);

            // Add row to parent layout
            stepButtonsLayout.addView(stepRow);
        }
    }


    private void updateStatus() {
        List<String> images = stepImages.getOrDefault(selectedStep, new ArrayList<>());
        textStatus.setText("Step " + selectedStep + " selected\nCaptured images: " + images.size());
    }
    private void captureImage() {
        if (selectedStep == -1) {
            Toast.makeText(this, "Please select a step first", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            photoFile = createImageFile();
            Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to create image file", Toast.LENGTH_SHORT).show();
        }
    }
    private File createImageFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "IMG_" + timestamp;
        File storageDir = getExternalFilesDir("Pictures");
        return File.createTempFile(fileName, ".jpg", storageDir);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && selectedStep != -1) {
            String imagePath = photoFile.getAbsolutePath();
            List<String> images = stepImages.computeIfAbsent(selectedStep, k -> new ArrayList<>());
            images.add(imagePath);
            updateStatus();
            Toast.makeText(this, "Image saved for Step " + selectedStep, Toast.LENGTH_SHORT).show();

            boolean wantsTwoImages = stepToggleMap.getOrDefault(selectedStep, false);



        } else if (requestCode == REQUEST_IMAGE_TEST_CASE && resultCode == RESULT_OK) {
            processTestCaseImage(testCaseImageUri);
        } else if (requestCode == REQUEST_IMAGE_PRECONDITION && resultCode == RESULT_OK) {
            processPreconditionImage(preconditionImageUri);
        }
    }

    private List<Text.TextBlock> ocrTextBlocks = new ArrayList<>(); // Global variable

    private void processTestCaseImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        ocrTextBlocks.clear(); // Clear previous blocks

                        for (Text.TextBlock block : result.getTextBlocks()) {
                            ocrTextBlocks.add(block);
                        }

                        // ✅ Create and show OverlayDialog with TEST_CASE mode
                        OverlayDialog dialog = new OverlayDialog(
                                this,
                                uri,
                                ocrTextBlocks,
                                "test_case",
                                new OverlayDialog.OnBlockSelectedListener() {
                                    @Override
                                    public void onTestCaseSelected(String id, String title) {
                                        testCaseId = id;
                                        testCaseTitle = title;
                                    }

                                    @Override
                                    public void onPreconditionSelected(String preconditionText) {
                                        // No action needed here
                                    }
                                }
                        );
                        dialog.show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "OCR failed for Test Case", Toast.LENGTH_SHORT).show());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void processPreconditionImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        ocrTextBlocks.clear(); // Clear previous blocks

                        for (Text.TextBlock block : result.getTextBlocks()) {
                            ocrTextBlocks.add(block);
                        }

                        // ✅ Show the overlay dialog with precondition drag-select mode
                        OverlayDialog dialog = new OverlayDialog(
                                this,
                                uri,
                                ocrTextBlocks,
                                "precondition",
                                new OverlayDialog.OnBlockSelectedListener() {
                                    @Override
                                    public void onTestCaseSelected(String id, String title) {
                                        // No action needed here
                                    }

                                    @Override
                                    public void onPreconditionSelected(String preconditionText) {
                                        testCasePreconditions = preconditionText;
                                    }
                                }
                        );
                        dialog.show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "OCR failed for Precondition", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateWordDocument() {
        try {
        XWPFDocument document = new XWPFDocument();

        // First page table
        XWPFTable table = document.createTable(7, 2);
        table.setWidth("100%");
        String[] labels = {
                "TCERID", "Title", "Card No", "Login Details", "Device ID", "Pre-requisites", "Comments"
        };

        // Set label texts and empty right cells
        for (int i = 0; i < labels.length; i++) {
            table.getRow(i).getCell(0).setText(labels[i]);
            table.getRow(i).getCell(1).setText("");
            if(labels[i].equals("TCERID"))
                table.getRow(i).getCell(1).setText(testCaseId);
            if(labels[i].equals("Title"))
                table.getRow(i).getCell(1).setText(testCaseTitle);
            if(labels[i].equals("Pre-requisites"))
                table.getRow(i).getCell(1).setText(testCasePreconditions);

        }

        // Add bottom border to all cells
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                cell.getCTTc().addNewTcPr().addNewTcBorders().addNewBottom().setVal(STBorder.SINGLE);
            }
        }

        document.createParagraph().setPageBreak(true);

        for (int step : stepImages.keySet()) {
            List<String> images = stepImages.get(step);

            // Step title paragraph
            XWPFParagraph stepTitle = document.createParagraph();
            stepTitle.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun stepRun = stepTitle.createRun();
            stepRun.setBold(true);
            stepRun.setFontSize(14);
            stepRun.setText("Step " + step);

            for (int i = 0; i < images.size(); i++) {
                if (i != 0 && i % 2 == 0) {
                    document.createParagraph().setPageBreak(true);
                }

                Bitmap original = BitmapFactory.decodeFile(images.get(i));
                if (original == null) {
                    continue; // Skip missing or corrupt images
                }

                // Define max dimensions for resized bitmap to balance clarity and file size


                int origWidth = original.getWidth();
                int origHeight = original.getHeight();
                final int maxWidth = origWidth-2000;
                final int maxHeight = origHeight-2000;

                Log.d("ImageInfo", "Original Width: " + origWidth + ", Original Height: " + origHeight);

                float aspectRatio = (float) origWidth / origHeight;
                int scaledWidth, scaledHeight;

                // Calculate scaled dimensions preserving aspect ratio
                if (origWidth > origHeight) {
                    scaledWidth = maxWidth;
                    scaledHeight = Math.round(maxWidth / aspectRatio);
                } else {
                    scaledHeight = maxHeight;
                    scaledWidth = Math.round(maxHeight * aspectRatio);
                }

                // Matrix-based scaling for better quality
                Matrix matrix = new Matrix();
                float scaleWidth = scaledWidth / (float) origWidth;
                float scaleHeight = scaledHeight / (float) origHeight;
                matrix.setScale(scaleWidth, scaleHeight);

                Bitmap scaled = Bitmap.createBitmap(original, 0, 0, scaledWidth, scaledHeight);

                // Enhance quality: anti-aliasing and filtering
                Canvas canvas = new Canvas(scaled);
                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setFilterBitmap(true);
                paint.setDither(true);
                canvas.drawBitmap(scaled, 0, 0, paint);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                // JPEG compression at 85 to 90 quality for balance between size and clarity
                scaled.compress(Bitmap.CompressFormat.JPEG, 40, baos);

                InputStream is = new ByteArrayInputStream(baos.toByteArray());

                XWPFParagraph imagePara = document.createParagraph();
                imagePara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun imageRun = imagePara.createRun();

                int emuWidth, emuHeight;
                boolean wantsTwoImgs = stepToggleMap.getOrDefault(step, false);

                if (!wantsTwoImgs) {
                    // Max 3 inches width for two images side by side
                    emuWidth = (int) (3.0 * Units.EMU_PER_INCH);
                    emuHeight = (int) ((3.0 * scaledHeight / (float) scaledWidth) * Units.EMU_PER_INCH);
                } else {
                    // Max 6 inches width for single image
                    emuWidth = (int) (6.0 * Units.EMU_PER_INCH);
                    emuHeight = (int) ((6.0 * scaledHeight / (float) scaledWidth) * Units.EMU_PER_INCH);
                }

                imageRun.addPicture(is, Document.PICTURE_TYPE_JPEG, "step_image.jpg", emuWidth, emuHeight);

                is.close();
                original.recycle();
                scaled.recycle();
            }

            document.createParagraph().setPageBreak(true);
        }
            long millis = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String date = sdf.format(new Date(millis));

        String fileName = selectedPrefix +"_"+ testCaseId+"_"+ date+"_Passed"+ ".docx";
        File file = new File(getExternalFilesDir(null), fileName);
        FileOutputStream out = new FileOutputStream(file);
        document.write(out);
        out.close();
        document.close();

        generatedDocFile = file;
        btnShareDoc.setVisibility(View.VISIBLE);

        Toast.makeText(this, "Word file saved under 10MB!", Toast.LENGTH_LONG).show();

    } catch (Exception e) {
        e.printStackTrace();
        Toast.makeText(this, "Error generating Word doc", Toast.LENGTH_SHORT).show();
    }
}
}
