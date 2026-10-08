package de.danoeh.antennapod.ui.screen.home.sections;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.danoeh.antennapod.R;

/**
 * Puts the episode cover in a square on this frame.
 *
 * <p>Up Next binds rows while {@code RecyclerView} is laying out. A
 * {@code requestLayout()} from there is swallowed, so changing the cover's
 * layout params left it at the 1dp size from XML. Measuring the cover in this
 * pass applies the square before the row is drawn.
 */
public class UpNextCoverFrame extends FrameLayout {
    /** Matches the action button in {@code up_next_card.xml} when it has not been measured yet. */
    private static final float FALLBACK_BUTTON_DP = 40f;

    public interface OnCoverSizedListener {
        void onCoverSized(int sizePx);
    }

    @Nullable private View coverView;
    @Nullable private View playButton;
    @Nullable private OnCoverSizedListener sizedListener;
    private int coverSize;

    public UpNextCoverFrame(@NonNull Context context) {
        super(context);
    }

    public UpNextCoverFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public UpNextCoverFrame(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOnCoverSizedListener(@Nullable OnCoverSizedListener listener) {
        sizedListener = listener;
    }

    /**
     * Sizes the cover when this frame already has bounds. A recycled row often
     * keeps its bounds and is not measured again, so waiting for the next
     * layout pass would leave the previous 1dp measurement in place.
     */
    public void ensureCoverPlaced() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        int size = applyCover(getWidth(), getHeight());
        if (size <= 0 || coverView == null) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) coverView.getLayoutParams();
        int childLeft = getPaddingLeft()
                + Math.max(0, (getWidth() - getPaddingLeft() - getPaddingRight() - size) / 2)
                + params.leftMargin - params.rightMargin;
        int childTop = getPaddingTop() + params.topMargin;
        coverView.layout(childLeft, childTop, childLeft + size, childTop + size);
        if (sizedListener != null) {
            sizedListener.onCoverSized(size);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        applyCover(getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int size = applyCover(right - left, bottom - top);
        super.onLayout(changed, left, top, right, bottom);
        if (size > 0 && sizedListener != null) {
            sizedListener.onCoverSized(size);
        }
    }

    /** @return the cover square in pixels, or 0 when this frame has no size yet */
    private int applyCover(int width, int height) {
        resolveChildren();
        if (coverView == null) {
            return 0;
        }
        int reserved = reservedBottom();
        int size = UpNextCover.squareSize(width, height, reserved);
        if (size <= 0) {
            coverSize = 0;
            return 0;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) coverView.getLayoutParams();
        params.width = size;
        params.height = size;
        params.topMargin = 0;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        int spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        coverView.measure(spec, spec);
        coverSize = size;
        return size;
    }

    private int reservedBottom() {
        if (playButton == null || playButton.getVisibility() != View.VISIBLE) {
            return 0;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playButton.getLayoutParams();
        int buttonHeight = playButton.getMeasuredHeight();
        if (buttonHeight <= 0) {
            buttonHeight = params.height;
        }
        if (buttonHeight <= 0) {
            buttonHeight = Math.round(FALLBACK_BUTTON_DP * getResources().getDisplayMetrics().density);
        }
        return buttonHeight + params.bottomMargin;
    }

    private void resolveChildren() {
        if (coverView == null) {
            coverView = findViewById(R.id.cover);
        }
        if (playButton == null) {
            playButton = findViewById(R.id.playButton);
        }
    }
}
