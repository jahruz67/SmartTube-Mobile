package com.liskovsoft.smartyoutubetv2.mobile.ui.search;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native portrait Search screen. Implements {@link SearchView} and is driven by the
 * existing {@link SearchPresenter} — query handling, result paging and the
 * filter dialog are all reused from the TV code; only the view layer is new.
 *
 * The toolbar holds the query field; below it the screen shows either query suggestions
 * (while typing) or a 2-column grid of results (after a search). Results from every
 * search group are flattened into one grid, keyed by group id so a continuation replaces
 * just its own slice.
 */
public class MobileSearchFragment extends Fragment implements SearchView {
    private final ActivityResultLauncher<Intent> mVoiceSearch = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> onVoiceSearchResult(result.getResultCode(), result.getData()));
    private static final long SUGGESTION_DELAY_MS = 150;
    private SearchPresenter mPresenter;
    private EditText mSearchInput;
    private RecyclerView mResultsList;
    private SwipeRefreshLayout mSwipeRefresh;
    private RecyclerView mSuggestionsList;
    private ProgressBar mProgressBar;
    private ProgressBar mLoadingMoreProgress;
    private View mEmptyContainer;
    private View mRetryButton;
    private TextView mEmptyMessage;
    private Snackbar mErrorSnackbar;
    private VideoCardAdapter mResultsAdapter;
    private TagAdapter mTagsAdapter;
    private MediaServiceSearchTagProvider mTagsProvider;
    private final Map<Integer, List<Video>> mGroups = new LinkedHashMap<>();
    private final LruCache<String, List<Tag>> mSuggestionCache = new LruCache<>(20);
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private int mSuggestionGeneration;
    private String mSuggestionQuery;
    private String mSubmittedQuery;
    private boolean mProgressShowing;
    private boolean mLoadingMore;
    private boolean mSwipeRefreshing;
    private boolean mReplaceOnNextResult;
    private boolean mWantSuggestions;
    private boolean mSearchSubmitted;
    private boolean mSuppressWatcher;
    private boolean mFragmentCreated = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_search_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSearchInput = view.findViewById(R.id.search_input);
        mResultsList = view.findViewById(R.id.results_list);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSuggestionsList = view.findViewById(R.id.suggestions_list);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mLoadingMoreProgress = view.findViewById(R.id.loading_more_progress);
        mEmptyContainer = view.findViewById(R.id.empty_container);
        mRetryButton = view.findViewById(R.id.empty_button);
        mEmptyMessage = view.findViewById(R.id.empty_message);
        mRetryButton.setOnClickListener(v -> retrySubmittedSearch());

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
        view.findViewById(R.id.btn_clear).setOnClickListener(v -> {
            mSearchInput.setText("");
            focusSearchField();
        });
        view.findViewById(R.id.btn_voice).setOnClickListener(v -> startVoiceRecognition());

        int span = getResources().getInteger(R.integer.mobile_grid_span);
        int cardWidth = getResources().getDisplayMetrics().widthPixels / span;
        mResultsAdapter = new VideoCardAdapter(cardWidth, mVideoClick, mVideoLongClick);
        mResultsList.setLayoutManager(new GridLayoutManager(getContext(), span));
        mResultsList.setAdapter(mResultsAdapter);
        mResultsList.addOnScrollListener(mScrollListener);

        mSwipeRefresh.setColorSchemeResources(R.color.brand_accent);
        mSwipeRefresh.setEnabled(false);
        mSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.mobile_surface);
        mSwipeRefresh.setOnRefreshListener(() -> {
            // Re-run the last submitted query. Nothing to refresh while the user is still
            // typing (suggestions view) or before any search ran.
            String query = mSubmittedQuery;
            if (mSearchSubmitted && !TextUtils.isEmpty(query)) {
                mSwipeRefreshing = true;
                submitSearch(query);
            } else {
                stopSwipeRefresh();
            }
        });

        mTagsAdapter = new TagAdapter(this::onTagPicked);
        mSuggestionsList.setLayoutManager(new LinearLayoutManager(getContext()));
        mSuggestionsList.setAdapter(mTagsAdapter);

        mSearchInput.addTextChangedListener(mQueryWatcher);
        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(mSearchInput.getText().toString());
                return true;
            }
            return false;
        });

        mPresenter = SearchPresenter.instance(getContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mFragmentCreated && mPresenter != null) {
            mPresenter.onViewResumed();
        }
        mFragmentCreated = false;
    }

    @Override
    public void onDestroyView() {
        mWantSuggestions = false;
        cancelSuggestions();
        mSuggestionCache.evictAll();
        dismissSearchError();
        if (mSearchInput != null) {
            mSearchInput.removeTextChangedListener(mQueryWatcher);
        }
        if (mPresenter != null && (mPresenter.getView() == null || mPresenter.getView() == this)) {
            mPresenter.onViewDestroyed();
        }
        mProgressShowing = false;
        mLoadingMore = false;
        stopSwipeRefresh();
        mTagsProvider = null;
        mSearchInput = null;
        mPresenter = null;
        super.onDestroyView();
    }

    // ----- SearchView -----

    @Override
    public void updateSearch(VideoGroup group) {
        if (group == null || getView() == null) {
            return;
        }
        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                clearSearch();
                break;
            case VideoGroup.ACTION_SYNC:
                // Percent-watched refresh: synced in place, so mGroups (same Video objects) stays current.
                if (mResultsAdapter != null) mResultsAdapter.sync(group.getVideos());
                return;
            case VideoGroup.ACTION_REMOVE:
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                for (List<Video> videos : mGroups.values()) {
                    videos.removeAll(group.getVideos());
                }
                mResultsAdapter.remove(group.getVideos());
                return;
            default:
                break;
        }
        if (group.isEmpty()) {
            return;
        }
        if (mReplaceOnNextResult) {
            clearResults();
        }
        if (group.getAction() == VideoGroup.ACTION_APPEND && appendSearchResults(group)) {
            hideEmptyMessage();
            return;
        }
        List<Video> previous = mGroups.get(group.getId());
        int offset = searchGroupOffset(group.getId());
        mGroups.put(group.getId(), new ArrayList<>(group.getVideos()));
        if (previous == null) {
            mResultsAdapter.appendVideos(group.getVideos());
        } else {
            mResultsAdapter.replaceVideos(offset, previous.size(), group.getVideos());
        }
        hideEmptyMessage();
    }

    @Override
    public void clearSearch() {
        if (mSwipeRefreshing && !mGroups.isEmpty()) {
            // Keep the current results usable until refresh succeeds.
            mReplaceOnNextResult = true;
            return;
        }
        clearResults();
    }

    private void clearResults() {
        mReplaceOnNextResult = false;
        mGroups.clear();
        if (mResultsAdapter != null) {
            mResultsAdapter.clear();
        }
        hideEmptyMessage();
    }

    @Override
    public void clearSearchTags() {
        mSuggestionCache.evictAll();
        if (mTagsAdapter != null) {
            mTagsAdapter.clear();
        }
    }

    @Override
    public void removeSearchTag(Tag tag) {
        mSuggestionCache.evictAll();
        if (mTagsAdapter != null) {
            mTagsAdapter.remove(tag);
        }
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        cancelSuggestions();
        mSuggestionCache.evictAll();
        mTagsProvider = provider;
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgressShowing = show;
        mLoadingMore = false;
        if (show) {
            hideEmptyMessage();
            dismissSearchError();
        }
        if (!show) {
            stopSwipeRefresh();
        }
        updateLoadingVisibility();
    }

    @Override
    public void showLoadingMore(boolean show) {
        mProgressShowing = show;
        mLoadingMore = show;
        if (show) {
            dismissSearchError();
        }
        updateLoadingVisibility();
    }

    private void updateLoadingVisibility() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(mProgressShowing && !mLoadingMore && !mSwipeRefreshing
                    && !mWantSuggestions ? View.VISIBLE : View.GONE);
        }
        if (mLoadingMoreProgress != null) {
            mLoadingMoreProgress.setVisibility(mLoadingMore && !mWantSuggestions ? View.VISIBLE : View.GONE);
        }
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setEnabled(mSearchSubmitted && !mWantSuggestions
                    && (!mProgressShowing || mSwipeRefreshing));
        }
        if (mEmptyContainer != null && mSearchSubmitted && mGroups.isEmpty() && !mProgressShowing
                && !TextUtils.isEmpty(mEmptyMessage.getText())) {
            mEmptyContainer.setVisibility(mWantSuggestions ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onSearchCompleted() {
        if (mReplaceOnNextResult) {
            clearResults();
        }
        if (mSearchSubmitted && mGroups.isEmpty()) {
            showEmptyMessage(getString(R.string.mobile_search_no_results));
        }
    }

    @Override
    public void showSearchError(boolean loadingMore) {
        mReplaceOnNextResult = false;
        if (mGroups.isEmpty()) {
            showEmptyMessage(getString(R.string.videos_loading_error));
            mRetryButton.setVisibility(View.VISIBLE);
            return;
        }
        if (mWantSuggestions) {
            return;
        }
        dismissSearchError();
        mErrorSnackbar = Snackbar.make(mResultsList, R.string.videos_loading_error, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.mobile_search_retry, v -> {
                    if (loadingMore) {
                        Video last = mResultsAdapter.getLast();
                        if (last != null && mPresenter != null) mPresenter.onScrollEnd(last);
                    } else {
                        retrySubmittedSearch();
                    }
                });
        mErrorSnackbar.show();
    }

    private void retrySubmittedSearch() {
        if (mSubmittedQuery != null) {
            submitSearch(mSubmittedQuery);
        }
    }

    private void dismissSearchError() {
        if (mErrorSnackbar != null) {
            mErrorSnackbar.dismiss();
            mErrorSnackbar = null;
        }
    }

    private void stopSwipeRefresh() {
        mSwipeRefreshing = false;
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public void startSearch(String searchText) {
        if (mSearchInput == null) {
            return;
        }
        if (!TextUtils.isEmpty(searchText)) {
            mSuppressWatcher = true;
            mSearchInput.setText(searchText);
            mSearchInput.setSelection(searchText.length());
            mSuppressWatcher = false;
            submitSearch(searchText);
        } else {
            focusSearchField();
        }
    }

    @Override
    public String getSearchText() {
        return mSearchInput != null ? mSearchInput.getText().toString() : "";
    }

    @Override
    public void startVoiceRecognition() {
        // Intent-based recognition: Google's speech UI runs in its own process and handles
        // the mic permission itself, so the app needs no RECORD_AUDIO permission. If no
        // recognizer is installed, fall back to the keyboard.
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.mobile_search_voice_prompt));
        try {
            mVoiceSearch.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(), R.string.mobile_search_voice_unavailable,
                    Toast.LENGTH_SHORT).show();
            focusSearchField();
        }
    }

    private void onVoiceSearchResult(int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || mSearchInput == null) {
            return;
        }
        List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            return;
        }
        String spoken = results.get(0);
        if (TextUtils.isEmpty(spoken)) {
            return;
        }
        // Mirror onTagPicked: set the field without re-triggering the suggestions watcher,
        // then run the search.
        mSuppressWatcher = true;
        mSearchInput.setText(spoken);
        mSearchInput.setSelection(spoken.length());
        mSuppressWatcher = false;
        submitSearch(spoken);
    }

    @Override
    public void finishReally() {
        if (isAdded() && getActivity() != null) {
            getActivity().finish();
        }
    }

    // ----- helpers -----

    private void submitSearch(String query) {
        if (query == null || mSearchInput == null || mPresenter == null) {
            return;
        }
        query = query.trim();
        if (query.isEmpty()) {
            return;
        }
        mWantSuggestions = false;
        cancelSuggestions();
        mSuggestionsList.setVisibility(View.GONE);
        hideKeyboard();
        mSearchInput.clearFocus();
        mSearchSubmitted = true;
        if (mProgressShowing && !mSwipeRefreshing && TextUtils.equals(query, mSubmittedQuery)) {
            updateLoadingVisibility();
            return;
        }
        mSubmittedQuery = query;
        mResultsList.scrollToPosition(0);
        mPresenter.onSearch(query);
    }

    private void onTagPicked(Tag tag) {
        if (tag == null || tag.tag == null) {
            return;
        }
        mSuppressWatcher = true;
        mSearchInput.setText(tag.tag);
        mSearchInput.setSelection(tag.tag.length());
        mSuppressWatcher = false;
        submitSearch(tag.tag);
    }

    private boolean appendSearchResults(VideoGroup group) {
        List<Video> all = group.getVideos();
        List<Video> previous = mGroups.get(group.getId());
        if (previous == null) {
            mGroups.put(group.getId(), new ArrayList<>(all));
            mResultsAdapter.appendVideos(all);
            return true;
        }
        int oldCount = previous.size();
        if (all.size() < oldCount) {
            return false;
        }
        for (int i = 0; i < oldCount; i++) {
            if (previous.get(i) != all.get(i)) {
                return false;
            }
        }
        List<Video> tail = all.subList(oldCount, all.size());
        previous.addAll(tail);
        mResultsAdapter.insertVideos(searchGroupOffset(group.getId()) + oldCount, tail);
        return true;
    }

    private int searchGroupOffset(int groupId) {
        int offset = 0;
        for (Map.Entry<Integer, List<Video>> entry : mGroups.entrySet()) {
            if (entry.getKey() == groupId) {
                break;
            }
            offset += entry.getValue().size();
        }
        return offset;
    }

    private void loadSuggestions(String query) {
        if (query.equals(mSuggestionQuery)) {
            return;
        }
        cancelSuggestions();
        // Keep the suggestion surface steady while the next query is debounced and loaded.
        mSuggestionsList.setVisibility(View.VISIBLE);
        dismissSearchError();
        hideEmptyMessage();
        stopSwipeRefresh();
        updateLoadingVisibility();
        mSuggestionQuery = query;
        List<Tag> cached = mSuggestionCache.get(query);
        if (cached != null) {
            mTagsAdapter.setTags(cached);
            return;
        }
        if (mTagsProvider == null) {
            mTagsAdapter.clear();
            return;
        }
        int generation = mSuggestionGeneration;
        mUiHandler.postDelayed(() -> {
            if (generation != mSuggestionGeneration || !mWantSuggestions || getView() == null) {
                return;
            }
            mTagsProvider.search(query, results -> mUiHandler.post(() -> {
                if (!isAdded() || getView() == null || !mWantSuggestions
                        || generation != mSuggestionGeneration) {
                    return;
                }
                if (results != null) {
                    mSuggestionCache.put(query, new ArrayList<>(results));
                }
                mTagsAdapter.setTags(results);
            }));
        }, SUGGESTION_DELAY_MS);
    }

    private void cancelSuggestions() {
        mSuggestionGeneration++;
        mSuggestionQuery = null;
        mUiHandler.removeCallbacksAndMessages(null);
        if (mTagsProvider != null) {
            mTagsProvider.cancel();
        }
    }

    private final TextWatcher mQueryWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            if (mSuppressWatcher) {
                return;
            }
            String query = s.toString().trim();
            if (query.isEmpty()) {
                mWantSuggestions = false;
                cancelSuggestions();
                mSuggestionsList.setVisibility(View.GONE);
                if (mTagsAdapter != null) {
                    mTagsAdapter.clear();
                }
                updateLoadingVisibility();
                return;
            }
            mWantSuggestions = true;
            loadSuggestions(query);
        }
    };

    // Hosting activity declares configChanges="orientation|..." so it is NOT recreated on
    // rotation; re-read the grid span (values-sw600dp-land widens it) and resize cards.
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int span = getResources().getInteger(R.integer.mobile_grid_span);
        if (mResultsList != null && mResultsList.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mResultsList.getLayoutManager()).setSpanCount(span);
        }
        if (mResultsAdapter != null) {
            mResultsAdapter.setCardWidth(getResources().getDisplayMetrics().widthPixels / span);
        }
    }

    private final VideoCardAdapter.OnVideoAction mVideoClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemSelected(video);
            mPresenter.onVideoItemClicked(video);
        }
    };

    private final VideoCardAdapter.OnVideoAction mVideoLongClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemLongClicked(video);
        }
    };

    private final RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0 || mProgressShowing || mWantSuggestions || mResultsAdapter == null) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (!(lm instanceof GridLayoutManager)) {
                return;
            }
            GridLayoutManager grid = (GridLayoutManager) lm;
            int lastVisible = grid.findLastVisibleItemPosition();
            if (lastVisible >= mResultsAdapter.getItemCount() - grid.getSpanCount() * 3) {
                Video last = mResultsAdapter.getLast();
                if (last != null && mPresenter != null) {
                    mPresenter.onScrollEnd(last);
                }
            }
        }
    };

    private void focusSearchField() {
        if (mSearchInput == null) {
            return;
        }
        EditText input = mSearchInput;
        input.requestFocus();
        input.post(() -> {
            if (getView() == null || mSearchInput != input || !input.hasFocus()) {
                return;
            }
            InputMethodManager imm = (InputMethodManager)
                    input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                mSearchInput.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    private void showEmptyMessage(String message) {
        if (mEmptyMessage != null) {
            mEmptyMessage.setText(message);
            mRetryButton.setVisibility(View.GONE);
            mEmptyContainer.setVisibility(mWantSuggestions ? View.GONE : View.VISIBLE);
        }
    }

    private void hideEmptyMessage() {
        if (mEmptyContainer != null) {
            mEmptyContainer.setVisibility(View.GONE);
        }
    }
}
