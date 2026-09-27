package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.actionbutton.ItemActionButton;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.databinding.UpNextCardBinding;
import de.danoeh.antennapod.event.EpisodeDownloadEvent;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackStatus;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.CoverLoader;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler;
import de.danoeh.antennapod.ui.screen.InboxFragment;
import de.danoeh.antennapod.ui.screen.episode.ItemPagerFragment;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Large horizontal scroller of what to play next. Uses the queue when it has episodes,
 * and otherwise the newest unplayed inbox episodes.
 */
public class UpNextSection extends HomeSection {
    public static final String TAG = "UpNextSection";
    private static final int FALLBACK_COUNT = 15;
    private static final int QUEUE_CAP = 30;

    private UpNextAdapter adapter;
    private Disposable disposable;
    private boolean fromQueue;
    private List<FeedItem> items = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        adapter = new UpNextAdapter((MainActivity) requireActivity());
        viewBinding.recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        viewBinding.recyclerView.setAdapter(adapter);
        viewBinding.recyclerView.setClipToPadding(false);
        viewBinding.recyclerView.setClipChildren(false);
        int side = (int) (4 * getResources().getDisplayMetrics().density);
        viewBinding.recyclerView.setPadding(side, 0, side, 0);
        view.setPadding(0, 0, 0, 0);
        new PagerSnapHelper().attachToRecyclerView(viewBinding.recyclerView);
        viewBinding.emptyLabel.setText(R.string.up_next_empty);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
    }

    @Override
    public void onDestroyView() {
        if (disposable != null) {
            disposable.dispose();
        }
        super.onDestroyView();
    }

    @Override
    protected String getSectionTitle() {
        return getString(R.string.up_next_label);
    }

    @Override
    protected String getMoreLinkTitle() {
        return getString(R.string.queue_label);
    }

    @Override
    protected void handleMoreClick() {
        MainActivity activity = (MainActivity) requireActivity();
        if (fromQueue) {
            activity.loadChildFragment(new QueueFragment());
        } else {
            activity.loadChildFragment(new InboxFragment());
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (!getUserVisibleHint() || !isVisible() || !isMenuVisible()) {
            return false;
        }
        FeedItem selected = adapter == null ? null : adapter.longPressedItem;
        if (selected == null) {
            return false;
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selected);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueChanged(QueueEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedItemChanged(FeedItemEvent event) {
        loadItems();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(EpisodeDownloadEvent event) {
        loadItems();
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(this::readItems)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (viewBinding == null) {
                        return;
                    }
                    fromQueue = result.fromQueue;
                    items = result.items;
                    viewBinding.moreButton.setText(fromQueue ? R.string.queue_label : R.string.inbox_label);
                    adapter.setItems(items);
                    applyScrollerHeight(!items.isEmpty());
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private UpNextLoad readItems() {
        List<FeedItem> queue = DBReader.getQueue();
        if (!queue.isEmpty()) {
            if (queue.size() > QUEUE_CAP) {
                return new UpNextLoad(new ArrayList<>(queue.subList(0, QUEUE_CAP)), true);
            }
            return new UpNextLoad(new ArrayList<>(queue), true);
        }
        List<FeedItem> fresh = DBReader.getEpisodes(0, FALLBACK_COUNT,
                new FeedItemFilter(FeedItemFilter.NEW), UserPreferences.getInboxSortedOrder());
        return new UpNextLoad(new ArrayList<>(fresh), false);
    }

    private void applyScrollerHeight(boolean hasItems) {
        ViewGroup.LayoutParams params = viewBinding.recyclerView.getLayoutParams();
        if (hasItems) {
            viewBinding.recyclerView.setVisibility(View.VISIBLE);
            viewBinding.emptyLabel.setVisibility(View.GONE);
            viewBinding.recyclerView.post(this::sizeScrollerToLeaveAPeek);
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            viewBinding.recyclerView.setVisibility(View.GONE);
            viewBinding.emptyLabel.setVisibility(View.VISIBLE);
        }
        viewBinding.recyclerView.setLayoutParams(params);
    }

    /**
     * Leaves just the next section's title in view, so Continue listening peeks above the fold.
     */
    private void sizeScrollerToLeaveAPeek() {
        if (viewBinding == null || !isAdded()) {
            return;
        }
        int[] location = new int[2];
        viewBinding.recyclerView.getLocationOnScreen(location);
        int bottomLimit = requireActivity().getWindow().getDecorView().getHeight();
        View bottomNav = requireActivity().findViewById(R.id.bottomNavigationView);
        if (bottomNav != null && bottomNav.getVisibility() == View.VISIBLE && bottomNav.getHeight() > 0) {
            int[] navLocation = new int[2];
            bottomNav.getLocationOnScreen(navLocation);
            bottomLimit = navLocation[1];
        }
        View player = requireActivity().findViewById(R.id.audioplayerFragment);
        if (player != null && player.getVisibility() == View.VISIBLE) {
            bottomLimit -= getResources().getDimensionPixelSize(R.dimen.external_player_height);
        }
        float density = getResources().getDisplayMetrics().density;
        int peek = (int) (44 * density);
        int height = bottomLimit - location[1] - peek;
        int min = (int) (160 * density);
        if (height < min) {
            height = min;
        }
        ViewGroup.LayoutParams params = viewBinding.recyclerView.getLayoutParams();
        params.height = height;
        viewBinding.recyclerView.setLayoutParams(params);
    }

    private static final class UpNextLoad {
        final List<FeedItem> items;
        final boolean fromQueue;

        UpNextLoad(List<FeedItem> items, boolean fromQueue) {
            this.items = items;
            this.fromQueue = fromQueue;
        }
    }

    private final class UpNextAdapter extends RecyclerView.Adapter<UpNextHolder> {
        private final MainActivity activity;
        private List<FeedItem> data = new ArrayList<>();
        private FeedItem longPressedItem;

        UpNextAdapter(MainActivity activity) {
            this.activity = activity;
            setHasStableIds(true);
        }

        void setItems(List<FeedItem> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public UpNextHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            UpNextCardBinding binding = UpNextCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            int width = parent.getWidth();
            if (width <= 0) {
                width = parent.getResources().getDisplayMetrics().widthPixels;
            }
            width = (int) (width * 0.72f);
            int gap = (int) (10 * parent.getResources().getDisplayMetrics().density);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    width, ViewGroup.LayoutParams.MATCH_PARENT);
            params.setMargins(gap, 0, gap, 0);
            binding.getRoot().setLayoutParams(params);
            return new UpNextHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull UpNextHolder holder, int position) {
            FeedItem item = data.get(position);
            holder.bind(item);
            holder.binding.card.setOnClickListener(v ->
                    activity.loadChildFragment(ItemPagerFragment.newInstance(data, item)));
            holder.binding.card.setOnLongClickListener(v -> {
                longPressedItem = item;
                return false;
            });
            holder.binding.card.setOnCreateContextMenuListener((menu, v, menuInfo) ->
                    activity.getMenuInflater().inflate(R.menu.feeditemlist_context, menu));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).getId();
        }

        @Override
        public void onViewRecycled(@NonNull UpNextHolder holder) {
            holder.binding.card.setOnClickListener(null);
            holder.binding.card.setOnLongClickListener(null);
            holder.binding.card.setOnCreateContextMenuListener(null);
            holder.binding.playButton.setOnClickListener(null);
        }
    }

    private final class UpNextHolder extends RecyclerView.ViewHolder {
        final UpNextCardBinding binding;

        UpNextHolder(UpNextCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FeedItem item) {
            Feed feed = item.getFeed();
            binding.showLabel.setText(feed == null ? "" : feed.getTitle());
            binding.titleLabel.setText(item.getTitle());
            String feedImage = feed == null ? null : feed.getImageUrl();
            new CoverLoader()
                    .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                    .withFallbackUri(feedImage)
                    .withCoverView(binding.cover)
                    .load();

            FeedMedia media = item.getMedia();
            int background = R.attr.colorSurfaceContainer;
            if (media != null && PlaybackStatus.isCurrentlyPlaying(media)) {
                background = R.attr.colorSecondaryContainer;
            }
            binding.card.setCardBackgroundColor(ThemeUtils.getColorFromAttr(adapter.activity, background));

            if (media != null && media.getDuration() > 0 && media.getPosition() > 0) {
                int percent = (int) (100f * media.getPosition() / media.getDuration());
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.progressBar.setProgress(percent, false);
            } else {
                binding.progressBar.setVisibility(View.GONE);
            }

            ImageView playButton = binding.playButton;
            if (media == null) {
                playButton.setVisibility(View.GONE);
            } else {
                playButton.setVisibility(View.VISIBLE);
                ItemActionButton.forItem(item).configure(playButton, playButton, adapter.activity);
                playButton.setFocusable(false);
            }
        }
    }
}
