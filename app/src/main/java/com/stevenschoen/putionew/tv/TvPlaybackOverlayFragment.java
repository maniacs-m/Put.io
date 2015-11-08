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
package com.stevenschoen.putionew.tv;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.ControlButtonPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.support.v17.leanback.widget.PlaybackControlsRow.ClosedCaptioningAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.FastForwardAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.HighQualityAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.MoreActions;
import android.support.v17.leanback.widget.PlaybackControlsRow.PlayPauseAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.RepeatAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.RewindAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.ShuffleAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.SkipNextAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.SkipPreviousAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.ThumbsDownAction;
import android.support.v17.leanback.widget.PlaybackControlsRow.ThumbsUpAction;
import android.support.v17.leanback.widget.PlaybackControlsRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.util.Log;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.stevenschoen.putionew.model.files.PutioFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/*
 * The PlaybackOverlayFragment class handles the Fragment associated with displaying the UI for the
 * media controls such as play / pause / skip forward / skip backward etc.
 *
 * The UI is updated through events that it receives from its MediaController
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class TvPlaybackOverlayFragment extends android.support.v17.leanback.app.PlaybackOverlayFragment {
    private static final String TAG = "PlaybackOverlayFragment";
    private static final boolean SHOW_DETAIL = true;
    private static final boolean HIDE_MORE_ACTIONS = false;
    private static final int BACKGROUND_TYPE = BG_LIGHT;
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 240;
    private static final int DEFAULT_UPDATE_PERIOD = 1000;
    private static final int UPDATE_PERIOD = 16;
    private static final int SIMULATED_BUFFERED_TIME = 10000;
    private static final int CLICK_TRACKING_DELAY = 1000;
    private static final int INITIAL_SPEED = 10000;

    private final Handler mClickTrackingHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mPrimaryActionsAdapter;
    private ArrayObjectAdapter mSecondaryActionsAdapter;
//    private ClosedCaptioningAction mClosedCaptioningAction;
    private FastForwardAction mFastForwardAction;
    private PlayPauseAction mPlayPauseAction;
    private RewindAction mRewindAction;
    private PlaybackControlsRow mPlaybackControlsRow;
    private Handler mHandler;
    private Runnable mRunnable;
    private PutioFile mFile;
    private int mFfwRwdSpeed = INITIAL_SPEED;
    private Timer mClickTrackingTimer;
    private int mClickCount;

    private MediaController mMediaController;
    private MediaController.Callback mMediaControllerCallback = new MediaControllerCallback();
    private int mCurrentPlaybackState;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        mFile = getActivity().getIntent().getParcelableExtra(TvPlaybackActivity.ARG_FILE);

        mHandler = new Handler();

        setupRows();

        setBackgroundType(BACKGROUND_TYPE);
        setFadingEnabled(false);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mMediaController = getActivity().getMediaController();
        Log.d(TAG, "register callback of mediaController");
        mMediaController.registerCallback(mMediaControllerCallback);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        stopProgressAutomation();
        mRowsAdapter = null;
        super.onStop();
    }

    @Override
    public void onDetach() {
        if (mMediaController != null) {
            Log.d(TAG, "unregister callback of mediaController");
            mMediaController.unregisterCallback(mMediaControllerCallback);
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setupRows() {
        ClassPresenterSelector ps = new ClassPresenterSelector();

        PlaybackControlsRowPresenter playbackControlsRowPresenter;
        if (SHOW_DETAIL) {
            playbackControlsRowPresenter = new PlaybackControlsRowPresenter(
                    new DescriptionPresenter());
        } else {
            playbackControlsRowPresenter = new PlaybackControlsRowPresenter();
        }
        playbackControlsRowPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            public void onActionClicked(Action action) {
                if (action.getId() == mPlayPauseAction.getId()) {
                    togglePlayback(mPlayPauseAction.getIndex() == PlayPauseAction.PLAY);
                } else if (action.getId() == mFastForwardAction.getId()) {
                    fastForward();
                } else if (action.getId() == mRewindAction.getId()) {
                    fastRewind();
                }
                if (action instanceof PlaybackControlsRow.MultiAction) {
                    notifyChanged(action);
                }
            }
        });
        playbackControlsRowPresenter.setSecondaryActionsHidden(HIDE_MORE_ACTIONS);

        ps.addClassPresenter(PlaybackControlsRow.class, playbackControlsRowPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(ps);

        addPlaybackControlsRow();

        setAdapter(mRowsAdapter);
    }

    public void togglePlayback(boolean playPause) {
        if (playPause) {
            mMediaController.getTransportControls().play();
        } else {
            mMediaController.getTransportControls().pause();
        }
    }

    private void addPlaybackControlsRow() {
        if (SHOW_DETAIL) {
            mPlaybackControlsRow = new PlaybackControlsRow(mFile);
        } else {
            mPlaybackControlsRow = new PlaybackControlsRow();
        }
        mRowsAdapter.add(mPlaybackControlsRow);

        updatePlaybackRow();

        ControlButtonPresenterSelector presenterSelector = new ControlButtonPresenterSelector();
        mPrimaryActionsAdapter = new ArrayObjectAdapter(presenterSelector);
        mSecondaryActionsAdapter = new ArrayObjectAdapter(presenterSelector);
        mPlaybackControlsRow.setPrimaryActionsAdapter(mPrimaryActionsAdapter);
        mPlaybackControlsRow.setSecondaryActionsAdapter(mSecondaryActionsAdapter);

        Activity activity = getActivity();
        mPlayPauseAction = new PlayPauseAction(activity);
        mFastForwardAction = new FastForwardAction(activity);
        mRewindAction = new RewindAction(activity);
//        mClosedCaptioningAction = new ClosedCaptioningAction(activity);

        // Add main controls to primary adapter.
        mPrimaryActionsAdapter.add(mRewindAction);
        mPrimaryActionsAdapter.add(mPlayPauseAction);
        mPrimaryActionsAdapter.add(mFastForwardAction);

        // Add rest of controls to secondary adapter.
//        mSecondaryActionsAdapter.add(mClosedCaptioningAction);
    }

    private void notifyChanged(Action action) {
        int index = mPrimaryActionsAdapter.indexOf(action);
        if (index >= 0) {
            mPrimaryActionsAdapter.notifyArrayItemRangeChanged(index, 1);
        } else {
            index = mSecondaryActionsAdapter.indexOf(action);
            if (index >= 0) {
                mSecondaryActionsAdapter.notifyArrayItemRangeChanged(index, 1);
            }
        }
    }

    private void updatePlaybackRow() {
        mPlaybackControlsRow.setCurrentTime(0);
        mPlaybackControlsRow.setBufferedProgress(0);
        mRowsAdapter.notifyArrayItemRangeChanged(0, 1);
    }

    private void updateMovieView(String cardImageUrl, long duration) {
        mPlaybackControlsRow.setTotalTime((int) duration);

        // Show the video card image if there is enough room in the UI for it.
        // If you have many primary actions, you may not have enough room.
        updateVideoImage(cardImageUrl);
    }

    private int getUpdatePeriod() {
        if (getView() == null || mPlaybackControlsRow.getTotalTime() <= 0) {
            return DEFAULT_UPDATE_PERIOD;
        }
        return Math.max(UPDATE_PERIOD, mPlaybackControlsRow.getTotalTime() / getView().getWidth());
    }

    private void startProgressAutomation() {
        if (mRunnable == null) {
            mRunnable = new Runnable() {
                @Override
                public void run() {
                    int updatePeriod = getUpdatePeriod();
                    int currentTime = mPlaybackControlsRow.getCurrentTime() + updatePeriod;
                    int totalTime = mPlaybackControlsRow.getTotalTime();
                    mPlaybackControlsRow.setCurrentTime(currentTime);
                    mPlaybackControlsRow.setBufferedProgress(currentTime + SIMULATED_BUFFERED_TIME);

                    if (totalTime > 0 && totalTime <= currentTime) {
                        stopProgressAutomation();
                        next();
                    } else {
                        mHandler.postDelayed(this, updatePeriod);
                    }
                }
            };
            mHandler.postDelayed(mRunnable, getUpdatePeriod());
        }
    }

    private void next() {
        mMediaController.getTransportControls().skipToNext();
    }

    private void prev() {
        mMediaController.getTransportControls().skipToPrevious();
    }

    private void fastForward() {
        startClickTrackingTimer();
        mMediaController.getTransportControls().fastForward();
    }

    private void fastRewind() {
        startClickTrackingTimer();
        mMediaController.getTransportControls().rewind();
    }

    private void stopProgressAutomation() {
        if (mHandler != null && mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
            mRunnable = null;
        }
    }

    protected void updateVideoImage(String uri) {
        Picasso.with(getActivity())
                .load(uri)
                .into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        mPlaybackControlsRow.setImageBitmap(getActivity(), bitmap);
                        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
                    }
                    @Override
                    public void onBitmapFailed(Drawable errorDrawable) { }
                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) { }
                });
    }

    private void startClickTrackingTimer() {
        if (null != mClickTrackingTimer) {
            mClickCount++;
            mClickTrackingTimer.cancel();
        } else {
            mClickCount = 0;
            mFfwRwdSpeed = INITIAL_SPEED;
        }
        mClickTrackingTimer = new Timer();
        mClickTrackingTimer.schedule(new UpdateFfwRwdSpeedTask(), CLICK_TRACKING_DELAY);
    }

    static class DescriptionPresenter extends AbstractDetailsDescriptionPresenter {
        @Override
        protected void onBindDescription(ViewHolder viewHolder, Object item) {
            viewHolder.getTitle().setText(((PutioFile) item).name);
        }
    }

    private class UpdateFfwRwdSpeedTask extends TimerTask {

        @Override
        public void run() {
            mClickTrackingHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mClickCount == 0) {
                        mFfwRwdSpeed = INITIAL_SPEED;
                    } else if (mClickCount == 1) {
                        mFfwRwdSpeed *= 2;
                    } else if (mClickCount >= 2) {
                        mFfwRwdSpeed *= 4;
                    }
                    mClickCount = 0;
                    mClickTrackingTimer = null;
                }
            });
        }
    }

    private class MediaControllerCallback extends MediaController.Callback {

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            // The playback state has changed, so update your UI accordingly.
            // This should not update any media player / state!
            Log.d(TAG, "playback state changed: " + state.getState());

            if (state.getState() == PlaybackState.STATE_PLAYING && mCurrentPlaybackState != PlaybackState.STATE_PLAYING) {
                mCurrentPlaybackState = PlaybackState.STATE_PLAYING;
                startProgressAutomation();
                setFadingEnabled(true);
                mPlayPauseAction.setIndex(PlayPauseAction.PAUSE);
                mPlayPauseAction.setIcon(mPlayPauseAction.getDrawable(PlayPauseAction.PAUSE));
                notifyChanged(mPlayPauseAction);
            } else if (state.getState() == PlaybackState.STATE_PAUSED && mCurrentPlaybackState != PlaybackState.STATE_PAUSED) {
                mCurrentPlaybackState = PlaybackState.STATE_PAUSED;
                stopProgressAutomation();
                setFadingEnabled(false);
                mPlayPauseAction.setIndex(PlayPauseAction.PLAY);
                mPlayPauseAction.setIcon(mPlayPauseAction.getDrawable(PlayPauseAction.PLAY));
                notifyChanged(mPlayPauseAction);
            }

            int currentTime = (int) state.getPosition();
            mPlaybackControlsRow.setCurrentTime(currentTime);
            mPlaybackControlsRow.setBufferedProgress(currentTime + SIMULATED_BUFFERED_TIME);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            Log.d(TAG, "received update of media metadata");
            updateMovieView(
                    metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),
                    metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            );
        }
    }
}