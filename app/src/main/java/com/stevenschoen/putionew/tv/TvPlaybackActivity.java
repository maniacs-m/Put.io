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
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.VideoView;

import com.stevenschoen.putionew.PutioApplication;
import com.stevenschoen.putionew.PutioUtils;
import com.stevenschoen.putionew.R;
import com.stevenschoen.putionew.model.files.PutioFile;

/**
 * PlaybackActivity for video playback that loads PlaybackOverlayFragment and handles
 * the MediaSession object used to maintain the state of the media playback.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class TvPlaybackActivity extends Activity {

    /*
     * List of various states that we can be in
     */
    public enum LeanbackPlaybackState {
        PLAYING, PAUSED, IDLE
    }

    public static final String ARG_FILE = "ARG_FILE";
    private static final String TAG = TvPlaybackActivity.class.getSimpleName();
    private VideoView mVideoView; // VideoView is used to play the video (media) in a view.
    private LeanbackPlaybackState mPlaybackState = LeanbackPlaybackState.IDLE;
    private MediaSession mSession; // MediaSession is used to hold the state of our media playback.
    private long mDuration = -1;
    private PutioFile mFile;

    public static void launch(Activity activity, PutioFile file) {
        Intent intent = new Intent(activity, TvPlaybackActivity.class);
        intent.putExtra(ARG_FILE, file);
        activity.startActivity(intent);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFile = getIntent().getParcelableExtra(ARG_FILE);
        if (mFile == null) {
            throw new IllegalStateException("No file selected");
        }

        createMediaSession();

        setContentView(R.layout.tv_playback_activity);
        loadViews();

        playPause(true);
        //Example for handling resizing view for overscan
        //Utils.overScan(this, mVideoView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPlayback();
        mVideoView.suspend();
        mVideoView.setVideoURI(null);
        mSession.release();
    }

    private void setPosition(int position) {
        Log.d("asdf", "setPosition: " + position);
        if (position > mDuration) {
            Log.d("asdf", "seeking to end: " + mDuration);
            mVideoView.seekTo((int) mDuration);
        } else if (position < 0) {
            Log.d("asdf", "seeking to beginning");
            mVideoView.seekTo(0);
        } else {
            Log.d("asdf", "seeking to: " + position);
            mVideoView.seekTo(position);
        }
    }

    private void createMediaSession() {
        if (mSession == null) {
            mSession = new MediaSession(this, "LeanbackSampleApp");
            mSession.setCallback(new MediaSessionCallback());
            mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

            mSession.setActive(true);

            // Set the Activity's MediaController used to invoke transport controls / adjust volume.
            setMediaController(new MediaController(this, mSession.getSessionToken()));
        }
    }

    private void playPause(boolean doPlay) {
        if (mPlaybackState == LeanbackPlaybackState.IDLE) {
            setupCallbacks();
        }

        if (doPlay && mPlaybackState != LeanbackPlaybackState.PLAYING) {
            mPlaybackState = LeanbackPlaybackState.PLAYING;
            mVideoView.start();
        } else {
            mPlaybackState = LeanbackPlaybackState.PAUSED;
            mVideoView.pause();
        }
        updatePlaybackState();
    }

    private void updatePlaybackState() {
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());
        int state = PlaybackState.STATE_PLAYING;
        if (mPlaybackState == LeanbackPlaybackState.PAUSED || mPlaybackState == LeanbackPlaybackState.IDLE) {
            state = PlaybackState.STATE_PAUSED;
        }
        stateBuilder.setState(state, mVideoView.getCurrentPosition(), 1.0f);
        mSession.setPlaybackState(stateBuilder.build());
    }

    private long getAvailableActions() {
        long actions = PlaybackState.ACTION_PLAY |
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID |
                PlaybackState.ACTION_PLAY_FROM_SEARCH |
                PlaybackState.ACTION_SKIP_TO_NEXT |
                PlaybackState.ACTION_SKIP_TO_PREVIOUS;

        if (mPlaybackState == LeanbackPlaybackState.PLAYING) {
            actions |= PlaybackState.ACTION_PAUSE;
        }

        return actions;
    }

    private void updateMetadata() {
        final MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();

        String title = mFile.name.replace("_", " ");

        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, Long.toString(mFile.id));
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title);
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_ART_URI, mFile.screenshot);
        metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, mDuration);

        // And at minimum the title and artist for legacy support
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, title);

        mSession.setMetadata(metadataBuilder.build());
    }

    private void loadViews() {
        mVideoView = (VideoView) findViewById(R.id.videoView);
        mVideoView.setFocusable(false);
        mVideoView.setFocusableInTouchMode(false);

        setVideoPath(false);
        updateMetadata();
    }

    private void setupCallbacks() {
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            int attempts;

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                mVideoView.stopPlayback();
                mPlaybackState = LeanbackPlaybackState.IDLE;

                switch (extra) {
                    case MediaPlayer.MEDIA_ERROR_MALFORMED:
                    case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                        if (attempts < 1) {
                            setVideoPath(true);
                            playPause(true);
                            attempts++;
                            return true;
                        }
                        break;
                }
                return false;
            }
        });
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (mPlaybackState == LeanbackPlaybackState.PLAYING) {
                    mVideoView.start();
                }
                mDuration = mp.getDuration();
            }
        });
        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mPlaybackState = LeanbackPlaybackState.IDLE;
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideoView.isPlaying()) {
            if (!requestVisibleBehind(true)) {
                // Try to play behind launcher, but if it fails, stop playback.
                playPause(false);
            }
        } else {
            requestVisibleBehind(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        playPause(false);
    }

    @Override
    public void onVisibleBehindCanceled() {
        playPause(false);
        super.onVisibleBehindCanceled();
    }

    private void stopPlayback() {
        if (mVideoView != null) {
            mVideoView.stopPlayback();
        }
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(this, TvActivity.class));
        return true;
    }

    // An event was triggered by MediaController.TransportControls and must be handled here.
    // Here we update the media itself to act on the event that was triggered.
    private class MediaSessionCallback extends MediaSession.Callback {

        @Override
        public void onPlay() {
            playPause(true);
        }

        @Override
        public void onPause() {
            playPause(false);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
//            Movie movie = VideoProvider.getMovieById(mediaId);
//            if (movie != null) {
//                setVideoPath(movie.getVideoUrl());
//                mPlaybackState = LeanbackPlaybackState.PAUSED;
//                updateMetadata(movie);
//                playPause(extras.getBoolean(AUTO_PLAY));
//            }
        }

        @Override
        public void onSeekTo(long pos) {
            setPosition((int) pos);
            updatePlaybackState();
        }

        @Override
        public void onFastForward() {
            if (mDuration != -1) {
                // Fast forward 10 seconds.
                setPosition(mVideoView.getCurrentPosition() + (10 * 1000));
                updatePlaybackState();
            }
        }

        @Override
        public void onRewind() {
            // rewind 10 seconds
            setPosition(mVideoView.getCurrentPosition() - (10 * 1000));
            updatePlaybackState();
        }
    }

    private void setVideoPath(boolean forceMp4) {
        PutioUtils utils = ((PutioApplication) getApplication()).getPutioUtils();
        String streamUrl = mFile.getStreamUrl(utils, forceMp4);

        setPosition(0);
        mVideoView.setVideoPath(streamUrl);
    }
}