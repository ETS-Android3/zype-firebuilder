/**
 * This file was modified by Amazon:
 * Copyright 2015-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.amazon.android.tv.tenfoot.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v17.leanback.app.RowsFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import com.amazon.android.contentbrowser.ContentBrowser;
import com.amazon.android.contentbrowser.ContentLoader;
import com.amazon.android.contentbrowser.database.helpers.RecentDatabaseHelper;
import com.amazon.android.contentbrowser.database.records.RecentRecord;
import com.amazon.android.contentbrowser.helper.AuthHelper;
import com.amazon.android.model.Action;
import com.amazon.android.model.PlaylistAction;
import com.amazon.android.model.content.Content;
import com.amazon.android.model.content.ContentContainer;
import com.amazon.android.model.content.constants.ExtraKeys;
import com.amazon.android.model.event.ContentUpdateEvent;
import com.amazon.android.recipe.Recipe;
import com.amazon.android.tv.tenfoot.R;
import com.amazon.android.tv.tenfoot.presenter.CardPresenter;
import com.amazon.android.tv.tenfoot.presenter.CustomListRowPresenter;
import com.amazon.android.tv.tenfoot.presenter.PosterCardPresenter;
import com.amazon.android.ui.fragments.ErrorDialogFragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import static com.amazon.android.contentbrowser.ContentBrowser.BROADCAST_DATA_LOADED;
import static com.amazon.android.contentbrowser.ContentBrowser.BROADCAST_VIDEO_DETAIL_DATA_LOADED;

/* Zype, Evgeny Cherkasov */

/**
 * This fragment displays content of current selected playlist.
 */
public class ZypeContentDetailsPlaylistFragment extends RowsFragment {

    private static final String TAG = ZypeContentDetailsPlaylistFragment.class.getSimpleName();
    private static final int WAIT_BEFORE_FOCUS_REQUEST_MS = 500;
    private OnBrowseRowListener mCallback;
    private ArrayObjectAdapter settingsAdapter = null;
    ArrayObjectAdapter mRowsAdapter = null;

    private ErrorDialogFragment dialogError = null;
    private BroadcastReceiver receiver;

    private boolean isDataLoaded = false;
    private boolean isEmptyFavoritesShown = false;

    CustomListRowPresenter customListRowPresenter;

    // Container Activity must implement this interface.
    public interface OnBrowseRowListener {
        void onItemSelected(Object item, Row row, int rowIndex, int rowsNumber);
        void onItemClicked(Object item);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (receiver != null) {
            LocalBroadcastManager.getInstance(getActivity())
                    .registerReceiver(receiver, new IntentFilter(BROADCAST_VIDEO_DETAIL_DATA_LOADED));
            LocalBroadcastManager.getInstance(getActivity())
                    .registerReceiver(receiver, new IntentFilter(BROADCAST_DATA_LOADED));
        }
//        updateContents();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        EventBus.getDefault().register(this);
        // This makes sure that the container activity has implemented the callback interface.
        // If not, it throws an exception.
        try {
            mCallback = (OnBrowseRowListener) getActivity();
        }
        catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString() +
                    " must implement " +
                    "OnBrowseRowListener: " + e);
        }

        int position = getVideoPositionInPlaylist();
        customListRowPresenter = new CustomListRowPresenter(position);
        customListRowPresenter.setHeaderPresenter(new RowHeaderPresenter());
        // Uncomment this code to remove shadow from the cards
        //customListRowPresenter.setShadowEnabled(false);

        mRowsAdapter = new ArrayObjectAdapter(customListRowPresenter);

        loadContentContainer(mRowsAdapter);

        setAdapter(mRowsAdapter);

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());

        // Wait for WAIT_BEFORE_FOCUS_REQUEST_MS for the data to load before requesting focus.
//        Handler handler = new Handler(Looper.getMainLooper());
//        handler.postDelayed(() -> {
//            if (getView() != null) {
//                VerticalGridView verticalGridView = findGridViewFromRoot(getView());
//                if (verticalGridView != null) {
//                    verticalGridView.requestFocus();
//                }
//            }
//        }, WAIT_BEFORE_FOCUS_REQUEST_MS);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive(): isDataLoaded=" + isDataLoaded + "intent=" + intent.getAction());
                if (isDataLoaded) {
                    updateContents();
                }
                else {
                    loadContentContainer(mRowsAdapter);
                }
                isDataLoaded = true;
            }
        };

    }

    @Override
    public void onPause() {
        super.onPause();
        if (receiver != null) {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(receiver);
        }
    }

    /**
     * Event bus listener method to listen for authentication updates from AUthHelper and update
     * the login action status in settings.
     *
     * @param authenticationStatusUpdateEvent Broadcast event for update in authentication status.
     */
    @Subscribe
    public void onAuthenticationStatusUpdateEvent(AuthHelper.AuthenticationStatusUpdateEvent
                                                          authenticationStatusUpdateEvent) {

        if (settingsAdapter != null) {
            settingsAdapter.clear();
            List<Action> settings = ContentBrowser.getInstance(getActivity()).getSettingsActions();
            if (settings != null && !settings.isEmpty()) {
                for (Action item : settings) {
                    settingsAdapter.add(item);
                }
            }
            else {
                Log.d(TAG, "No settings were found");
            }
            settingsAdapter.notifyArrayItemRangeChanged(0, settingsAdapter.size());
        }
        if (mRowsAdapter != null) {
            mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
        }
    }

    @Subscribe
    public void onContentUpdateEvent(ContentUpdateEvent event) {
        ArrayObjectAdapter rowAdapter = (ArrayObjectAdapter) ((ListRow) mRowsAdapter.get(0)).getAdapter();
        for (int i = 0; i < rowAdapter.size(); i++) {
            Content content = (Content) rowAdapter.get(i);
            if (content.getId().equals(event.videoId)) {
                content.setExtraValue(Content.EXTRA_PLAYBACK_POSITION_PERCENTAGE,
                        ContentBrowser.getInstance(getActivity()).getContentLoader().getContentPlaybackPositionPercentage(content));
                rowAdapter.notifyArrayItemRangeChanged(i, i + 1);
            }
        }
    }

    private int getVideoPositionInPlaylist() {
        Content video = ContentBrowser.getInstance(getActivity()).getLastSelectedContent();
        String playlistId = video.getExtraValueAsString(Content.EXTRA_PLAYLIST_ID);
        ContentContainer playlist = ContentBrowser.getInstance(getActivity()).getPlayList(playlistId);
        if (playlist == null) {
            return -1;
        }
        else {
            return playlist.getContents().indexOf(video);
        }
    }

    private void loadContentContainer(ArrayObjectAdapter rowsAdapter) {
        Log.d(TAG, "loadContentContainer()");

        rowsAdapter.clear();

        Content video = ContentBrowser.getInstance(getActivity()).getLastSelectedContent();
        String playlistId = video.getExtraValueAsString(Content.EXTRA_PLAYLIST_ID);
        ContentContainer playlist = ContentBrowser.getInstance(getActivity()).getPlayList(playlistId);

        if (playlist == null) {
            isDataLoaded = true;
            mCallback.onItemSelected(video, null, -1, 0);
            return;
        }

        CardPresenter cardPresenter = new CardPresenter();
        PosterCardPresenter posterCardPresenter = new PosterCardPresenter();

        HeaderItem header = new HeaderItem(0, playlist.getName());
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
        if (playlist.getExtraStringValue(ContentContainer.EXTRA_THUMBNAIL_LAYOUT).equals("poster")) {
            listRowAdapter = new ArrayObjectAdapter(posterCardPresenter);
        }

        int videoIndex = 0;
        for (Content content : playlist.getContents()) {
            listRowAdapter.add(content);
            if (content.getId().equals(video.getId())) {
                videoIndex = listRowAdapter.indexOf(content);
            }
        }

        if (playlist.getContents().size() < playlist.getExtraValueAsInt(ContentContainer.EXTRA_PLAYLIST_ITEM_COUNT)) {
            if (playlist.getExtraValueAsInt(ExtraKeys.NEXT_PAGE) > 0) {
                PlaylistAction action = new PlaylistAction();
                action.setAction(ContentBrowser.NEXT_PAGE)
                        .setIconResourceId(com.amazon.android.contentbrowser.R.drawable.ic_add_white_48dp)
                        .setLabel1(getString(R.string.action_load_more));
                action.setExtraValue(PlaylistAction.EXTRA_PLAYLIST_ID, playlist.getExtraStringValue(Recipe.KEY_DATA_TYPE_TAG));
                listRowAdapter.add(action);
            }
        }

        rowsAdapter.add(new ListRow(header, listRowAdapter));
        isDataLoaded = true;
    }

    public void updateContents() {
        Log.d(TAG, "updateContents()");

        ArrayObjectAdapter rowsAdapter = mRowsAdapter;

        Content video = ContentBrowser.getInstance(getActivity()).getLastSelectedContent();
        String playlistId = video.getExtraValueAsString(Content.EXTRA_PLAYLIST_ID);
        ContentContainer playlist = ContentBrowser.getInstance(getActivity()).getPlayList(playlistId);

        if (playlist == null) {
            isDataLoaded = true;
            mCallback.onItemSelected(video, null, -1, 0);
            return;
        }

        int index = 0;
        ListRow row = (ListRow) rowsAdapter.get(index);
        ArrayObjectAdapter listRowAdapter = (ArrayObjectAdapter) row.getAdapter();

        // Remove 'Load more' action button
        if (listRowAdapter.size() > 0 && listRowAdapter.get(listRowAdapter.size() - 1) instanceof PlaylistAction) {
            listRowAdapter.remove(listRowAdapter.get(listRowAdapter.size() - 1));
        }
        // Add new contents
        if (playlist.getContentCount() > listRowAdapter.size()) {
            for (int i = listRowAdapter.size(); i < playlist.getContentCount(); i++) {
                listRowAdapter.add(playlist.getContents().get(i));
            }
            // Add a button for loading next page of playlist videos
            if (playlist.getExtraValueAsInt(ExtraKeys.NEXT_PAGE) > 0) {
                PlaylistAction action = new PlaylistAction();
                action.setAction(ContentBrowser.NEXT_PAGE)
                        .setIconResourceId(com.amazon.android.contentbrowser.R.drawable.ic_add_white_48dp)
                        .setLabel1(getString(R.string.action_load_more));
                action.setExtraValue(PlaylistAction.EXTRA_PLAYLIST_ID, playlist.getExtraStringValue(Recipe.KEY_DATA_TYPE_TAG));
                listRowAdapter.add(action);
            }
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {

        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Content) {
                mCallback.onItemClicked(item);
            }
            else if (item instanceof PlaylistAction) {
                PlaylistAction action = (PlaylistAction) item;
                if (action.getAction().equals(ContentBrowser.NEXT_PAGE)) {
                    Log.d(TAG, "Next page button was clicked");
                    Content video = ContentBrowser.getInstance(getActivity()).getLastSelectedContent();
                    String playlistId = video.getExtraValueAsString(Content.EXTRA_PLAYLIST_ID);
                    ContentBrowser.getInstance(getActivity()).loadPlaylistVideos(playlistId);
                }
                else {
                    Log.d(TAG, "Settings with title " + action.getAction() + " was clicked");
                    ContentBrowser.getInstance(getActivity()).settingsActionTriggered(getActivity(),action);
                }
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {

        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {

            mCallback.onItemSelected(item, row, mRowsAdapter.indexOf(row), mRowsAdapter.size());
        }
    }
}
