package de.danoeh.antennapod.ui;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;

import java.lang.ref.WeakReference;

public class CoverLoader {
    private int resource = 0;
    private String uri;
    private String fallbackUri;
    private ImageView imgvCover;
    private boolean textAndImageCombined;
    private TextView fallbackTitle;
    @Nullable private CoverReadyListener readyListener;
    @Nullable private Object expectedTag;
    private int overrideWidth;
    private int overrideHeight;

    public CoverLoader() {
    }

    public CoverLoader withUri(String uri) {
        this.uri = uri;
        return this;
    }

    public CoverLoader withResource(int resource) {
        this.resource = resource;
        return this;
    }

    public CoverLoader withFallbackUri(String uri) {
        fallbackUri = uri;
        return this;
    }

    public CoverLoader withCoverView(ImageView coverView) {
        imgvCover = coverView;
        return this;
    }

    /** Called with the drawable that was set on the cover, after a stale load has been ignored. */
    public CoverLoader withReadyListener(CoverReadyListener listener) {
        readyListener = listener;
        return this;
    }

    /** Ignore a load that finishes after the view has been bound to a different item. */
    public CoverLoader expectTag(Object tag) {
        expectedTag = tag;
        return this;
    }

    /** Decode at this pixel size instead of the view's current measured size. */
    public CoverLoader withOverride(int width, int height) {
        overrideWidth = width;
        overrideHeight = height;
        return this;
    }

    public CoverLoader withPlaceholderView(TextView title) {
        this.fallbackTitle = title;
        return this;
    }

    /**
     * Set cover text and if it should be shown even if there is a cover image.
     * @param fallbackTitle Fallback title text
     * @param textAndImageCombined Show cover text even if there is a cover image?
     */
    @NonNull
    public CoverLoader withPlaceholderView(TextView fallbackTitle, boolean textAndImageCombined) {
        this.fallbackTitle = fallbackTitle;
        this.textAndImageCombined = textAndImageCombined;
        return this;
    }

    public void load() {
        CoverTarget coverTarget = new CoverTarget(
                fallbackTitle, imgvCover, textAndImageCombined, readyListener, expectedTag);

        if (resource != 0) {
            Glide.with(imgvCover).clear(coverTarget);
            imgvCover.setImageResource(resource);
            CoverTarget.setTitleVisibility(fallbackTitle, textAndImageCombined);
            return;
        }

        RequestOptions options = new RequestOptions()
                .fitCenter()
                .dontAnimate();
        if (overrideWidth > 0 && overrideHeight > 0) {
            options = options.override(overrideWidth, overrideHeight);
        }

        RequestBuilder<Drawable> builder = Glide.with(imgvCover)
                .as(Drawable.class)
                .load(uri)
                .apply(options);

        if (fallbackUri != null) {
            builder = builder.error(Glide.with(imgvCover)
                    .as(Drawable.class)
                    .load(fallbackUri)
                    .apply(options));
        }

        builder.into(coverTarget);
    }

    public interface CoverReadyListener {
        void onCoverReady(@NonNull Drawable drawable);
    }

    static class CoverTarget extends CustomViewTarget<ImageView, Drawable> {
        private final WeakReference<TextView> fallbackTitle;
        private final WeakReference<ImageView> cover;
        private final boolean textAndImageCombined;
        @Nullable private final CoverReadyListener readyListener;
        @Nullable private final Object expectedTag;

        public CoverTarget(TextView fallbackTitle, ImageView coverImage, boolean textAndImageCombined,
                           @Nullable CoverReadyListener readyListener, @Nullable Object expectedTag) {
            super(coverImage);
            this.fallbackTitle = new WeakReference<>(fallbackTitle);
            this.cover = new WeakReference<>(coverImage);
            this.textAndImageCombined = textAndImageCombined;
            this.readyListener = readyListener;
            this.expectedTag = expectedTag;
        }

        @Override
        public void onLoadFailed(Drawable errorDrawable) {
            setTitleVisibility(fallbackTitle.get(), true);
        }

        @Override
        public void onResourceReady(@NonNull Drawable resource,
                                    @Nullable Transition<? super Drawable> transition) {
            ImageView ivCover = cover.get();
            if (ivCover == null || (expectedTag != null && !expectedTag.equals(ivCover.getTag()))) {
                return;
            }
            ivCover.setImageDrawable(resource);
            setTitleVisibility(fallbackTitle.get(), textAndImageCombined);
            if (readyListener != null) {
                readyListener.onCoverReady(resource);
            }
        }

        @Override
        protected void onResourceCleared(@Nullable Drawable placeholder) {
            ImageView ivCover = cover.get();
            ivCover.setImageDrawable(placeholder);
            setTitleVisibility(fallbackTitle.get(),  textAndImageCombined);
        }

        static void setTitleVisibility(TextView fallbackTitle, boolean textAndImageCombined) {
            if (fallbackTitle != null) {
                fallbackTitle.setVisibility(textAndImageCombined ? View.VISIBLE : View.GONE);
            }
        }
    }
}