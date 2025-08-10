package com.tejas.artifactgenerator;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverlayDialog extends Dialog {

    private final Context context;
    private final Uri imageUri;
    private final List<Text.TextBlock> blocks;
    private final OnBlockSelectedListener listener;
    private final String mode;


    private OverlayView overlayView;

    public interface OnBlockSelectedListener {
        void onTestCaseSelected(String testCaseId, String testCaseTitle);
        void onPreconditionSelected(String preconditionText);
    }

    public OverlayDialog(@NonNull Context context, Uri imageUri, List<Text.TextBlock> blocks, String mode, OnBlockSelectedListener listener) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.context = context;
        this.imageUri = imageUri;
        this.blocks = blocks;
        this.listener = listener;
        this.mode = mode;
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String extractTestCaseId(String text) {
        Pattern pattern = Pattern.compile("SIS-\\d+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : "Unknown-ID";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Layout setup
        FrameLayout container = new FrameLayout(context);
        overlayView = new OverlayView(context);
        Bitmap bitmap = loadBitmapFromUri(imageUri);
        overlayView.setData(bitmap, blocks);
        container.addView(overlayView);

        if (mode.equals("precondition")) {
            Button okButton = new Button(context);
            okButton.setText("OK");
            okButton.setBackgroundColor(Color.DKGRAY);
            okButton.setTextColor(Color.WHITE);

            Button clearButton = new Button(context);
            clearButton.setText("Clear");
            clearButton.setBackgroundColor(Color.GRAY);
            clearButton.setTextColor(Color.WHITE);

            FrameLayout.LayoutParams okParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            okParams.gravity = Gravity.END | Gravity.BOTTOM;
            okParams.setMargins(0, 0, 30, 30);
            okButton.setLayoutParams(okParams);

            FrameLayout.LayoutParams clearParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            clearParams.gravity = Gravity.START | Gravity.BOTTOM;
            clearParams.setMargins(30, 0, 0, 30);
            clearButton.setLayoutParams(clearParams);

            container.addView(okButton);
            container.addView(clearButton);

            okButton.setOnClickListener(v -> {
                List<Text.TextBlock> selectedBlocks = overlayView.getSelectedBlocks();
                if (selectedBlocks.isEmpty()) {
                    Toast.makeText(context, "No blocks selected", Toast.LENGTH_SHORT).show();
                    return;
                }

                StringBuilder combined = new StringBuilder();
                for (Text.TextBlock block : selectedBlocks) {
                    combined.append(block.getText().replace("\n", " ").trim()).append("\n");
                }

                listener.onPreconditionSelected(combined.toString().trim());
                dismiss();
            });

            clearButton.setOnClickListener(v -> {
                overlayView.clearSelections();
                Toast.makeText(context, "Selections cleared", Toast.LENGTH_SHORT).show();
            });
        }


        setContentView(container);

        // Logic for tapping blocks (test_case)
        if (mode.equals("test_case")) {
            List<Text.TextBlock> selectedBlocks = new ArrayList<>();

            overlayView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = event.getX();
                    float y = event.getY();

                    float scaleX = overlayView.getWidth() / (float) bitmap.getWidth();
                    float scaleY = overlayView.getHeight() / (float) bitmap.getHeight();

                    for (Text.TextBlock block : blocks) {
                        Rect rect = block.getBoundingBox();
                        if (rect != null) {
                            Rect scaled = new Rect(
                                    (int) (rect.left * scaleX),
                                    (int) (rect.top * scaleY),
                                    (int) (rect.right * scaleX),
                                    (int) (rect.bottom * scaleY)
                            );

                            if (scaled.contains((int) x, (int) y)) {
                                if (!selectedBlocks.contains(block)) {
                                    selectedBlocks.add(block);
                                }

                                if (selectedBlocks.size() == 2) {
                                    String testCaseId = extractTestCaseId(selectedBlocks.get(0).getText());
                                    String testCaseTitle = selectedBlocks.get(1).getText().replace("\n", " ").trim();
                                    listener.onTestCaseSelected(testCaseId, testCaseTitle);
                                    dismiss();
                                } else {
                                    Toast.makeText(context, "Tap one more block", Toast.LENGTH_SHORT).show();
                                }
                                return true;
                            }
                        }
                    }
                    Toast.makeText(context, "Tap on a valid block", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }
}
