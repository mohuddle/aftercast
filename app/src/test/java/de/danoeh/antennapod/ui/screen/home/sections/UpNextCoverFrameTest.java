package de.danoeh.antennapod.ui.screen.home.sections;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** One measure and layout pass has to produce the square. A later requestLayout never arrives. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpNextCoverFrameTest {
    private static final int FRAME_WIDTH = 815;
    private static final int FRAME_HEIGHT = 1332;
    private static final int BUTTON_SIZE = 186;
    private static final int BUTTON_MARGIN = 53;

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void onePassMeasuresAndPlacesTheSquareAboveThePlayButton() {
        UpNextCoverFrame frame = newFrame();
        ImageView cover = coverIn(frame);
        measureAndLayout(frame);

        int expected = UpNextCover.squareSize(FRAME_WIDTH, FRAME_HEIGHT, BUTTON_SIZE + BUTTON_MARGIN);
        assertEquals(expected, cover.getMeasuredWidth());
        assertEquals(expected, cover.getMeasuredHeight());
        assertEquals(expected, cover.getWidth());
        assertEquals(expected, cover.getHeight());
        assertEquals((FRAME_WIDTH - expected) / 2, cover.getLeft());
        assertEquals(0, cover.getTop());
    }

    @Test
    public void ensureCoverPlacedFixesACoverLeftAtTheXmlSize() {
        UpNextCoverFrame frame = newFrame();
        ImageView cover = coverIn(frame);
        measureAndLayout(frame);
        cover.measure(View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY));
        cover.layout(0, 0, 1, 1);

        frame.ensureCoverPlaced();

        int expected = UpNextCover.squareSize(FRAME_WIDTH, FRAME_HEIGHT, BUTTON_SIZE + BUTTON_MARGIN);
        assertEquals(expected, cover.getWidth());
        assertEquals(expected, cover.getHeight());
    }

    @Test
    public void measureReportsTheSquareSize() {
        UpNextCoverFrame frame = newFrame();
        int[] reported = new int[1];
        frame.setOnCoverSizedListener(size -> reported[0] = size);

        measureAndLayout(frame);

        int expected = UpNextCover.squareSize(FRAME_WIDTH, FRAME_HEIGHT, BUTTON_SIZE + BUTTON_MARGIN);
        assertEquals(expected, reported[0]);
    }

    private UpNextCoverFrame newFrame() {
        UpNextCoverFrame frame = new UpNextCoverFrame(context);
        ImageView cover = new ImageView(context);
        cover.setId(de.danoeh.antennapod.R.id.cover);
        FrameLayout.LayoutParams coverParams = new FrameLayout.LayoutParams(1, 1);
        coverParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cover.setLayoutParams(coverParams);

        ImageView play = new ImageView(context);
        play.setId(de.danoeh.antennapod.R.id.playButton);
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(BUTTON_SIZE, BUTTON_SIZE);
        playParams.gravity = Gravity.BOTTOM | Gravity.END;
        playParams.bottomMargin = BUTTON_MARGIN;
        play.setLayoutParams(playParams);

        frame.addView(cover);
        frame.addView(play);
        return frame;
    }

    private static ImageView coverIn(UpNextCoverFrame frame) {
        return frame.findViewById(de.danoeh.antennapod.R.id.cover);
    }

    private static void measureAndLayout(UpNextCoverFrame frame) {
        frame.measure(View.MeasureSpec.makeMeasureSpec(FRAME_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(FRAME_HEIGHT, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, FRAME_WIDTH, FRAME_HEIGHT);
    }
}
