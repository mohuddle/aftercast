package de.danoeh.antennapod.ui.screen.home.sections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UpNextCoverTest {

    @Test
    public void squareFillsTheFrameWidthWhenTheFrameIsTall() {
        assertEquals(100, UpNextCover.squareSize(100, 400, 20));
    }

    @Test
    public void rowIsTallEnoughForTheSquareAndShorterThanTheOldFullScreenCard() {
        int height = UpNextCover.rowHeight(1000, 1f, 1f);
        int cover = Math.round((Math.round(1000 * UpNextCover.CARD_WIDTH_FRACTION) - 32)
                * UpNextCover.WIDTH_FRACTION);
        assertEquals(868, height);
        assertTrue(cover * 2 > height);
    }

    @Test
    public void squareLeavesRoomForThePlayButton() {
        assertEquals(50, UpNextCover.squareSize(100, 80, 30));
    }

    @Test
    public void squareIsZeroBeforeTheFrameIsMeasured() {
        assertEquals(0, UpNextCover.squareSize(0, 200, 0));
    }

    @Test
    public void lightArtworkIsDarkenedForTheCard() {
        int darkened = UpNextCover.darken(Color.WHITE);
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(darkened, hsl);
        assertEquals(UpNextCover.MAX_LIGHTNESS, hsl[2], 0.01f);
    }

    @Test
    public void anAlreadyDarkColorKeepsItsLightness() {
        int darkened = UpNextCover.darken(0xFF1A2420);
        float[] original = new float[3];
        float[] result = new float[3];
        ColorUtils.colorToHSL(0xFF1A2420, original);
        ColorUtils.colorToHSL(darkened, result);
        assertEquals(original[2], result[2], 0.001f);
    }
}
