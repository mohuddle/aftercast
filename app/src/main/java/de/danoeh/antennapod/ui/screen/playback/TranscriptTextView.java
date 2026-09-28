package de.danoeh.antennapod.ui.screen.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spanned;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * Draws the playing sentence's underline itself. Newsreader reports no underline
 * thickness, so {@code setUnderlineText} produces no visible line.
 */
public class TranscriptTextView extends AppCompatTextView {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TranscriptTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Layout layout = getLayout();
        CharSequence text = getText();
        if (layout == null || !(text instanceof Spanned)) {
            return;
        }
        Spanned spanned = (Spanned) text;
        Mark[] marks = spanned.getSpans(0, text.length(), Mark.class);
        if (marks.length == 0) {
            return;
        }
        int start = spanned.getSpanStart(marks[0]);
        int end = spanned.getSpanEnd(marks[0]);
        if (start < 0 || end <= start) {
            return;
        }
        Paint textPaint = getPaint();
        float density = getResources().getDisplayMetrics().density;
        linePaint.setColor(textPaint.getColor());
        linePaint.setStrokeWidth(Math.max(1.5f * density, textPaint.getTextSize() / 16f));
        int firstLine = layout.getLineForOffset(start);
        int lastLine = layout.getLineForOffset(end - 1);
        float padLeft = getTotalPaddingLeft();
        float padTop = getTotalPaddingTop();
        float gap = textPaint.getTextSize() / 10f;
        for (int line = firstLine; line <= lastLine; line++) {
            float left = line == firstLine ? layout.getPrimaryHorizontal(start) : layout.getLineLeft(line);
            float right = line == lastLine ? layout.getPrimaryHorizontal(end) : layout.getLineRight(line);
            if (right < left) {
                float swap = left;
                left = right;
                right = swap;
            }
            float y = padTop + layout.getLineBaseline(line) + gap;
            canvas.drawLine(padLeft + left, y, padLeft + right, y, linePaint);
        }
    }

    /** Marks the sentence that is playing. The view paints the line. */
    public static final class Mark {
    }
}
