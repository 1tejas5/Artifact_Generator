package com.tejas.artifactgenerator;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private List<Text.TextBlock> textBlocks = new ArrayList<>();
    private Bitmap imageBitmap;
    private final List<Text.TextBlock> selectedBlocks = new ArrayList<>();

    private float downX, downY, upX, upY;
    private boolean isDragging = false;
    private RectF selectionRect = null;

    public OverlayView(Context context) {
        super(context);
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(Bitmap bitmap, List<Text.TextBlock> blocks) {
        this.imageBitmap = bitmap;
        this.textBlocks = blocks;
        invalidate();
    }

    public List<Text.TextBlock> getSelectedBlocks() {
        return selectedBlocks;
    }

    public void clearSelections() {
        selectedBlocks.clear();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (textBlocks == null || imageBitmap == null) return false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                isDragging = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                upX = event.getX();
                upY = event.getY();
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                isDragging = false;
                upX = event.getX();
                upY = event.getY();
                selectBlocksInRect();
                selectionRect = null;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void selectBlocksInRect() {
        selectedBlocks.clear();

        float scaleX = getWidth() / (float) imageBitmap.getWidth();
        float scaleY = getHeight() / (float) imageBitmap.getHeight();

        float left = Math.min(downX, upX);
        float top = Math.min(downY, upY);
        float right = Math.max(downX, upX);
        float bottom = Math.max(downY, upY);

        RectF dragRect = new RectF(left, top, right, bottom);
        this.selectionRect = dragRect;

        for (Text.TextBlock block : textBlocks) {
            Rect rect = block.getBoundingBox();
            if (rect != null) {
                RectF scaled = new RectF(
                        rect.left * scaleX,
                        rect.top * scaleY,
                        rect.right * scaleX,
                        rect.bottom * scaleY
                );
                if (RectF.intersects(scaled, dragRect)) {
                    selectedBlocks.add(block);
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (imageBitmap != null) {
            Rect dest = new Rect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(imageBitmap, null, dest, null);
        }

        if (textBlocks != null) {
            Paint rectPaint = new Paint();
            rectPaint.setColor(Color.RED);
            rectPaint.setStyle(Paint.Style.STROKE);
            rectPaint.setStrokeWidth(3);

            Paint selectedPaint = new Paint();
            selectedPaint.setColor(Color.argb(100, 30, 144, 255)); // light blue
            selectedPaint.setStyle(Paint.Style.FILL);

            float scaleX = getWidth() / (float) imageBitmap.getWidth();
            float scaleY = getHeight() / (float) imageBitmap.getHeight();

            for (Text.TextBlock block : textBlocks) {
                Rect rect = block.getBoundingBox();
                if (rect != null) {
                    RectF scaledRect = new RectF(
                            rect.left * scaleX,
                            rect.top * scaleY,
                            rect.right * scaleX,
                            rect.bottom * scaleY
                    );

                    if (selectedBlocks.contains(block)) {
                        canvas.drawRect(scaledRect, selectedPaint);
                    }

                    canvas.drawRect(scaledRect, rectPaint);
                }
            }
        }

        // Draw selection rectangle while dragging
        if (isDragging && imageBitmap != null && selectionRect != null) {
            Paint dragPaint = new Paint();
            dragPaint.setColor(Color.argb(70, 0, 0, 0));
            dragPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(selectionRect, dragPaint);
        }
    }
}
