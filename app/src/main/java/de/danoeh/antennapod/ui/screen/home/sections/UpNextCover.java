package de.danoeh.antennapod.ui.screen.home.sections;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

/**
 * Up Next shows the whole cover as a square on a dark field sampled from the artwork.
 * The square fills the card width, and the row is only tall enough for that square,
 * the action button, and the title.
 */
final class UpNextCover {
    static final float CARD_WIDTH_FRACTION = 0.72f;
    /** Square side as a fraction of the cover frame, which is already inset by the card padding. */
    static final float WIDTH_FRACTION = 1f;
    static final float MAX_LIGHTNESS = 0.32f;
    /** Card padding in {@code up_next_card.xml}. */
    private static final float CARD_PADDING_DP = 16f;
    /** Action button, the gap above it, and its bottom margin. Matches the card layout. */
    private static final float BUTTON_BAND_DP = 8f + 40f + 8f;
    /** Title and show padding that does not scale with the font. */
    private static final float TEXT_PADDING_DP = 20f;
    /** Two lines of title plus one line of show name. */
    private static final float TEXT_BLOCK_SP = 68f;
    /** Vertical card padding and the progress track. Margins sit outside the card. */
    private static final float CHROME_DP = 36f;
    static final int FALLBACK_COLOR = 0xFF2A2826;
    static final int TITLE_COLOR = 0xFFF7F4EE;
    static final int SHOW_COLOR = 0xD9F7F4EE;

    private UpNextCover() {
    }

    /** Side length of the square cover. {@code reservedBottom} keeps the play button off the art. */
    static int squareSize(int frameWidth, int frameHeight, int reservedBottom) {
        if (frameWidth <= 0 || frameHeight <= 0) {
            return 0;
        }
        int byWidth = Math.round(frameWidth * WIDTH_FRACTION);
        int byHeight = Math.max(0, frameHeight - Math.max(0, reservedBottom));
        return Math.min(byWidth, byHeight);
    }

    /**
     * Height of the horizontal row. A tall phone used to stretch each card to the
     * bottom of the screen and leave a wide empty field around a small square.
     */
    static int rowHeight(int parentWidth, float density, float fontScale) {
        if (parentWidth <= 0 || density <= 0f) {
            return 0;
        }
        int cardWidth = Math.round(parentWidth * CARD_WIDTH_FRACTION);
        int pad = Math.round(CARD_PADDING_DP * density);
        int frameWidth = Math.max(0, cardWidth - 2 * pad);
        int cover = Math.round(frameWidth * WIDTH_FRACTION);
        int buttonBand = Math.round(BUTTON_BAND_DP * density);
        float scale = fontScale <= 0f ? 1f : fontScale;
        int text = Math.round(TEXT_PADDING_DP * density + TEXT_BLOCK_SP * density * Math.max(scale, 1f));
        int chrome = Math.round(CHROME_DP * density);
        return cover + buttonBand + text + chrome;
    }

    static int cardColor(Palette palette, @ColorInt int fallback) {
        int color = palette.getDarkMutedColor(0);
        if (color == 0) {
            color = palette.getDarkVibrantColor(0);
        }
        if (color == 0) {
            color = palette.getMutedColor(0);
        }
        if (color == 0) {
            color = palette.getDominantColor(fallback);
        }
        return darken(color);
    }

    static int darken(@ColorInt int color) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        if (hsl[2] > MAX_LIGHTNESS) {
            hsl[2] = MAX_LIGHTNESS;
        }
        return ColorUtils.HSLToColor(hsl);
    }

    @Nullable
    static Bitmap softwareBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap source = ((BitmapDrawable) drawable).getBitmap();
            if (source == null) {
                return null;
            }
            if (source.getConfig() != Bitmap.Config.HARDWARE) {
                return source;
            }
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        Drawable copy = drawable.getConstantState() == null
                ? drawable : drawable.getConstantState().newDrawable().mutate();
        int width = Math.max(1, copy.getIntrinsicWidth());
        int height = Math.max(1, copy.getIntrinsicHeight());
        float scale = Math.min(1f, 160f / Math.max(width, height));
        int bitmapWidth = Math.max(1, Math.round(width * scale));
        int bitmapHeight = Math.max(1, Math.round(height * scale));
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        copy.setBounds(0, 0, bitmapWidth, bitmapHeight);
        copy.draw(canvas);
        return bitmap;
    }
}
