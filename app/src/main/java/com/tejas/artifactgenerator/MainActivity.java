package com.tejas.artifactgenerator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
        textZeraExtract = findViewById(R.id.textZeraExtract);

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

        findViewById(R.id.btnGenerateDoc).setOnClickListener(v -> generateWordDocument());

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

    private void generateStepButtons(int stepCount) {
        stepButtonsLayout.removeAllViews();
        selectedStep = -1;
        btnCapture.setVisibility(View.GONE);
        textStatus.setText("Step buttons generated. Click one to begin capturing images.");

        for (int i = 1; i <= stepCount; i++) {
            int stepNumber = i;
            Button stepButton = new Button(this);
            stepButton.setText("Step " + stepNumber);
            stepButton.setOnClickListener(v -> {
                selectedStep = stepNumber;
                btnCapture.setVisibility(View.VISIBLE);
                updateStatus();
            });
            stepButtonsLayout.addView(stepButton);
        }
    }

    private void updateStatus() {
        List<String> images = stepImages.getOrDefault(selectedStep, new ArrayList<>());
        textStatus.setText("Step " + selectedStep + " selected\nCaptured images: " + images.size());
    }

    private void captureImage() {
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
            stepImages.computeIfAbsent(selectedStep, k -> new ArrayList<>()).add(imagePath);
            Toast.makeText(this, "Image saved for Step " + selectedStep, Toast.LENGTH_SHORT).show();
            updateStatus();
        } else if (requestCode == REQUEST_IMAGE_TEST_CASE && resultCode == RESULT_OK) {
            processTestCaseImage(testCaseImageUri);
        } else if (requestCode == REQUEST_IMAGE_PRECONDITION && resultCode == RESULT_OK) {
            processPreconditionImage(preconditionImageUri);
        }
    }

    private void processTestCaseImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        String[] lines = result.getText().split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            String line = lines[i].trim();
                            if (line.matches("(?i)SIS-\\d{4,}")) {
                                testCaseId = line;
                                if (i + 1 < lines.length) {
                                    testCaseTitle = lines[i + 1].trim();
                                }
                                break;
                            }
                        }
                        updateZeraTextResult();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "OCR failed for Test Case", Toast.LENGTH_SHORT).show());
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
                        String[] lines = result.getText().split("\n");
                        StringBuilder sb = new StringBuilder();
                        boolean found = false;

                        for (String line : lines) {
                            if (!found && line.toLowerCase().contains("precondition")) {
                                found = true;
                                continue;
                            }
                            if (found) {
                                if (line.trim().matches("^\\d+\\.|\\*.*") || line.trim().matches("^\\d+.*")) {
                                    sb.append(line.trim()).append("\n");
                                } else {
                                    break;
                                }
                            }
                        }

                        testCasePreconditions = sb.toString().trim();
                        updateZeraTextResult();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "OCR failed for Precondition", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateZeraTextResult() {
        String finalText = "\uD83D\uDD11 Test Case ID: " + testCaseId + "\n\n" +
                "\uD83D\uDCCC Title: " + testCaseTitle + "\n\n" +
                "\uD83D\uDCCB Preconditions:\n" + testCasePreconditions;
        textZeraExtract.setText(finalText);
    }

    private void generateWordDocument () {
        try {
            XWPFDocument document = new XWPFDocument();

            // First page table
            XWPFTable table = document.createTable(7, 2);
            table.setWidth("100%");
            String[] labels = {
                    "TCERID", "Title", "PCC Card No", "Login Details", "Device ID", "Pre-requisites", "Comments"
            };

            for (int i = 0; i < labels.length; i++) {
                table.getRow(i).getCell(0).setText(labels[i]);
                table.getRow(i).getCell(1).setText("");
            }

            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    cell.getCTTc().addNewTcPr().addNewTcBorders().addNewBottom().setVal(STBorder.SINGLE);
                }
            }

            document.createParagraph().setPageBreak(true);

            for (int step : stepImages.keySet()) {
                List<String> images = stepImages.get(step);

                XWPFParagraph stepTitle = document.createParagraph();
                stepTitle.setAlignment(ParagraphAlignment.LEFT);
                XWPFRun stepRun = stepTitle.createRun();
                stepRun.setBold(true);
                stepRun.setFontSize(14);
                stepRun.setText("Step " + step);

                for (int i = 0; i < images.size(); i++) {
                    if (i % 2 == 0 && i != 0) {
                        document.createParagraph().setPageBreak(true);
                    }

                    Bitmap original = BitmapFactory.decodeFile(images.get(i));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    //Bitmap scaled = Bitmap.createScaledBitmap(original, 640, 480, true);

                    int maxWidth = 500;
                    int maxHeight = 640;

                    int originalWidth = original.getWidth();
                    int originalHeight = original.getHeight();

                    float aspectRatio = (float) originalWidth / originalHeight;

                    int scaledWidth, scaledHeight;

                    if (originalWidth > originalHeight) {
                        // Landscape
                        scaledWidth = maxWidth;
                        scaledHeight = Math.round(maxWidth / aspectRatio);
                    } else {
                        // Portrait or square
                        scaledHeight = maxHeight;
                        scaledWidth = Math.round(maxHeight * aspectRatio);
                    }

                    Bitmap scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true);


                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                    InputStream is = new ByteArrayInputStream(baos.toByteArray());

                    XWPFParagraph imagePara = document.createParagraph();
                    imagePara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun imageRun = imagePara.createRun();
                    // Calculate width and height in inches or cm (Word document uses inches)
                    float inchWidth = scaledWidth / 96f; // assuming 96 dpi screen
                    float inchHeight = scaledHeight / 96f;

// Convert inches to EMU
                    int emuWidth = (int) (inchWidth * Units.EMU_PER_INCH);
                    int emuHeight = (int) (inchHeight * Units.EMU_PER_INCH);
                    emuWidth=emuWidth/2;
                    emuHeight=emuHeight/2;

                    imageRun.addPicture(is, Document.PICTURE_TYPE_JPEG, "step_image.jpg", emuWidth, emuHeight);


                    is.close();
                }

                document.createParagraph().setPageBreak(true);
            }

            String fileName = "TestCaseSteps_" + System.currentTimeMillis() + ".docx";
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
