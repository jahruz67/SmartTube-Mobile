package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flat list of video cards. Used both for grid sections and (horizontally) inside shelves.
 * The card width is fixed per adapter; the thumbnail keeps a 16:9 ratio.
 *
 * Public so the phone Search screen ({@code mobile.ui.search}) can reuse the same card.
 */
public class VideoCardAdapter extends RecyclerView.Adapter<VideoCardAdapter.ViewHolder> {
    private static final Object WATCHED_PAYLOAD = new Object();
    public interface OnVideoAction {
        void onVideo(Video video);
    }

    /** Standard 16:9 video card. */
    public static final int STYLE_VIDEO = 0;
    /** Vertical 9:16 Shorts card with the title overlaid on the thumbnail. */
    public static final int STYLE_SHORT = 1;
    /** Playlist card: stacked-thumbnail look plus a video-count badge. */
    public static final int STYLE_PLAYLIST = 2;

    private int mCardWidth;
    private final OnVideoAction mClick;
    private final OnVideoAction mLongClick;
    private final List<Video> mVideos = new ArrayList<>();
    private final Map<Video, List<Integer>> mPositionsByVideo = new HashMap<>();
    private boolean mAdaptiveStyles;
    private boolean mVerticalShorts;

    public VideoCardAdapter(int cardWidth, OnVideoAction click, OnVideoAction longClick) {
        mCardWidth = cardWidth;
        mClick = click;
        mLongClick = longClick;
    }

    /**
     * Update the per-card width (e.g. after an orientation change, where the grid span
     * and therefore the column width change). The host activities declare
     * {@code configChanges="orientation|..."} so they are NOT recreated on rotation -
     * the fragment re-reads the span and calls this to keep card width == column width.
     */
    public void setCardWidth(int cardWidth) {
        if (mCardWidth != cardWidth) {
            mCardWidth = cardWidth;
            notifyDataSetChanged();
        }
    }

    /**
     * Opt in to per-item card styles (a 9:16 card for Shorts, a playlist card for playlists)
     * instead of the uniform 16:9 card. Used by the channel tabs; Home/Search keep the default.
     */
    public void setAdaptiveStyles(boolean adaptive) {
        if (mAdaptiveStyles != adaptive) {
            mAdaptiveStyles = adaptive;
            notifyDataSetChanged();
        }
    }

    /**
     * With adaptive styles on, draw Shorts as vertical 9:16 cards. Only for an all-Shorts grid:
     * one tall card in a mixed grid (e.g. search results) leaves ragged rows.
     */
    public void setVerticalShorts(boolean vertical) {
        if (mVerticalShorts != vertical) {
            mVerticalShorts = vertical;
            notifyDataSetChanged();
        }
    }

    public static int styleOf(Video video) {
        if (video == null) {
            return STYLE_VIDEO;
        }
        if (video.isShorts) {
            return STYLE_SHORT;
        }
        if (video.isPlaylistAsChannel() || video.isBadgePlaylistInChannel()
                || (video.videoId == null && video.hasPlaylist())) {
            return STYLE_PLAYLIST;
        }
        return STYLE_VIDEO;
    }

    private static int thumbHeight(int style, int cardWidth) {
        return style == STYLE_SHORT ? cardWidth * 16 / 9 : cardWidth * 9 / 16;
    }

    public void setVideos(List<Video> videos) {
        mVideos.clear();
        if (videos != null) {
            mVideos.addAll(videos);
        }
        rebuildPositionIndex();
        notifyDataSetChanged();
    }

    /**
     * Append a continuation's new tail with a range-insert instead of a full
     * {@code notifyDataSetChanged}. Used by shelves so a horizontally-scrolled row keeps
     * its scroll position when more items page in — a full reset (or re-setting the
     * adapter on the row) would snap the shelf back to the start.
     */
    public void appendVideos(List<Video> videos) {
        insertVideos(mVideos.size(), videos);
    }

    /** Insert a continuation before later search groups, preserving their order. */
    public void insertVideos(int position, List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }
        int count = videos.size();
        mVideos.addAll(position, videos);
        shiftIndexedPositions(position, count);
        for (int i = 0; i < count; i++) {
            addIndexedPosition(mVideos.get(position + i), position + i);
        }
        notifyItemRangeInserted(position, videos.size());
    }

    /** Replace one search group's slice without rebinding the other groups. */
    public void replaceVideos(int position, int oldCount, List<Video> videos) {
        mVideos.subList(position, position + oldCount).clear();
        mVideos.addAll(position, videos);
        rebuildPositionIndex();

        int sharedCount = Math.min(oldCount, videos.size());
        if (sharedCount > 0) {
            notifyItemRangeChanged(position, sharedCount);
        }
        if (oldCount > sharedCount) {
            notifyItemRangeRemoved(position + sharedCount, oldCount - sharedCount);
        } else if (videos.size() > sharedCount) {
            notifyItemRangeInserted(position + sharedCount, videos.size() - sharedCount);
        }
    }

    public void clear() {
        int count = mVideos.size();
        mVideos.clear();
        mPositionsByVideo.clear();
        if (count > 0) {
            notifyItemRangeRemoved(0, count);
        }
    }

    public void remove(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }

        Set<Video> requested = new HashSet<>(videos);
        List<Integer> removed = new ArrayList<>();
        for (int i = 0; i < mVideos.size(); i++) {
            if (requested.contains(mVideos.get(i))) {
                removed.add(i);
            }
        }
        if (removed.isEmpty()) {
            return;
        }

        // Remove from the end so earlier positions remain valid. Group adjacent rows into a
        // single range notification while preserving the order and duplicate semantics of removeAll.
        for (int i = removed.size() - 1; i >= 0;) {
            int end = removed.get(i);
            int start = end;
            i--;
            while (i >= 0 && removed.get(i) == start - 1) {
                start = removed.get(i);
                i--;
            }
            for (int position = end; position >= start; position--) {
                mVideos.remove(position);
            }
            notifyItemRangeRemoved(start, end - start + 1);
        }
        rebuildPositionIndex();
    }

    /**
     * Apply a presenter {@code ACTION_SYNC} (e.g. updated percent-watched after returning from
     * the player) to matching cards in place. Mirrors TV's {@code VideoGroupObjectAdapter.sync}:
     * every position is checked since a list (History) can hold the same video more than once.
     */
    public void sync(List<Video> changed) {
        if (changed == null || changed.isEmpty()) {
            return;
        }
        for (Video video : changed) {
            List<Integer> matches = video != null ? mPositionsByVideo.get(video) : null;
            if (matches == null) {
                continue;
            }
            for (int position : matches) {
                mVideos.get(position).sync(video);
                notifyItemChanged(position, WATCHED_PAYLOAD);
            }
        }
    }

    private void rebuildPositionIndex() {
        mPositionsByVideo.clear();
        for (int i = 0; i < mVideos.size(); i++) {
            addIndexedPosition(mVideos.get(i), i);
        }
    }

    private void addIndexedPosition(Video video, int position) {
        if (video == null) {
            return;
        }
        List<Integer> positions = mPositionsByVideo.get(video);
        if (positions == null) {
            positions = new ArrayList<>();
            mPositionsByVideo.put(video, positions);
        }
        if (positions.isEmpty() || positions.get(positions.size() - 1) < position) {
            positions.add(position);
            return;
        }
        int index = 0;
        while (index < positions.size() && positions.get(index) < position) {
            index++;
        }
        positions.add(index, position);
    }

    private void shiftIndexedPositions(int fromPosition, int amount) {
        for (List<Integer> positions : mPositionsByVideo.values()) {
            for (int i = 0; i < positions.size(); i++) {
                int position = positions.get(i);
                if (position >= fromPosition) {
                    positions.set(i, position + amount);
                }
            }
        }
    }

    public Video getLast() {
        return mVideos.isEmpty() ? null : mVideos.get(mVideos.size() - 1);
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (!mAdaptiveStyles) {
            return STYLE_VIDEO;
        }
        int style = styleOf(mVideos.get(position));
        return style == STYLE_SHORT && !mVerticalShorts ? STYLE_VIDEO : style;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == STYLE_SHORT ? R.layout.mobile_short_card
                : viewType == STYLE_PLAYLIST ? R.layout.mobile_playlist_card : R.layout.mobile_video_card;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        if (view.getLayoutParams() != null) {
            view.getLayoutParams().width = mCardWidth;
        }
        ViewHolder holder = new ViewHolder(view, viewType);
        holder.thumbFrame.getLayoutParams().height = thumbHeight(viewType, mCardWidth);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(WATCHED_PAYLOAD)) {
            bindWatchedBar(holder.progress, holder.duration, mVideos.get(position));
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Re-apply width on every bind so setCardWidth() (orientation change) resizes
        // recycled cards, keeping each card the width of its grid column.
        if (holder.itemView.getLayoutParams() != null) {
            holder.itemView.getLayoutParams().width = mCardWidth;
        }
        holder.thumbFrame.getLayoutParams().height = thumbHeight(holder.style, mCardWidth);

        Video video = mVideos.get(position);
        holder.title.setText(video.getTitle());
        String author;
        String stats = null;
        if (holder.style == STYLE_VIDEO) {
            Video.AuthorAndStats authorAndStats = video.getAuthorAndStats();
            author = authorAndStats.author;
            stats = authorAndStats.stats;
        } else {
            author = video.getAuthor();
        }
        if (holder.style != STYLE_VIDEO && (author == null || author.isEmpty())) {
            // Shorts/playlist tiles often carry no author; show their secondary line
            // (view count / "View full playlist") instead.
            CharSequence second = video.getSecondTitle();
            author = second != null ? second.toString() : null;
        }
        if (holder.style == STYLE_VIDEO && author != null && !author.isEmpty()) {
            // Second line: "196K views • 2 wk ago" (the published time was missing on cards).
            if (stats != null) {
                author = author + "\n" + stats;
            }
        }
        holder.author.setText(author != null ? author : "");

        // Duration/length badge overlaid on the thumbnail (YouTube-style). video.badge holds
        // the duration text ("12:34") for normal videos and occasionally a label ("LIVE").
        // Explicit GONE branch matters: cards are recycled, so a badge-less video must clear
        // a badge left over from a recycled holder.
        String badge = video.badge;
        if (badge != null && !badge.isEmpty()) {
            holder.duration.setText(badge);
            holder.duration.setVisibility(View.VISIBLE);
        } else {
            holder.duration.setVisibility(View.GONE);
        }

        bindWatchedBar(holder.progress, holder.duration, video);

        Glide.with(holder.itemView.getContext())
                .load(video.getCardImageUrl())
                .into(holder.thumb);

        holder.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onVideo(video);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (mLongClick != null) {
                mLongClick.onVideo(video);
            }
            return true;
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.itemView.getContext()).clear(holder.thumb);
        super.onViewRecycled(holder);
    }

    /**
     * YouTube-style red "watched" bar along the thumbnail's bottom edge. Same semantics as the
     * TV card ({@code VideoCardPresenter}): tiny progress is rounded up to 1% so it stays visible.
     * Hidden when unknown/unwatched and on Shorts (YouTube shows no bar on Shorts tiles). When
     * shown, the duration badge is lifted above the bar so they don't overlap.
     * Shared with the up-next row ({@code UpNextRowAdapter}).
     */
    public static void bindWatchedBar(ProgressBar bar, TextView badge, Video video) {
        float percent = video.percentWatched;
        boolean show = percent > 0 && !video.isShorts;
        // Explicit GONE branch: views are recycled.
        if (show) {
            bar.setProgress(percent < 1 ? 1 : Math.min(100, Math.round(percent)), false);
            bar.setVisibility(View.VISIBLE);
        } else {
            bar.setVisibility(View.GONE);
        }

        if (badge.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) badge.getLayoutParams();
            float density = badge.getResources().getDisplayMetrics().density;
            int bottom = Math.round((show ? 4 + 3 : 4) * density);
            if (lp.bottomMargin != bottom) {
                lp.bottomMargin = bottom;
                badge.setLayoutParams(lp);
            }
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final int style;
        final View thumbFrame;
        final ImageView thumb;
        final TextView duration;
        final ProgressBar progress;
        final TextView title;
        final TextView author;

        ViewHolder(View itemView, int style) {
            super(itemView);
            this.style = style;
            thumbFrame = itemView.findViewById(R.id.card_thumb_frame);
            thumb = itemView.findViewById(R.id.card_thumb);
            duration = itemView.findViewById(R.id.card_duration);
            progress = itemView.findViewById(R.id.card_progress);
            title = itemView.findViewById(R.id.card_title);
            author = itemView.findViewById(R.id.card_author);
        }
    }
}
