package net.sourceforge.subsonic.androidapp.activity;


import java.util.List;
import java.io.File;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.KeyEvent;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import net.sourceforge.subsonic.androidapp.R;
import net.sourceforge.subsonic.androidapp.domain.MusicDirectory;
import net.sourceforge.subsonic.androidapp.service.StreamService;
import net.sourceforge.subsonic.androidapp.service.DownloadService;
import static net.sourceforge.subsonic.androidapp.service.StreamService.PlayerState.COMPLETED;
import static net.sourceforge.subsonic.androidapp.service.StreamService.PlayerState.PAUSED;
import static net.sourceforge.subsonic.androidapp.service.StreamService.PlayerState.STARTED;
import static net.sourceforge.subsonic.androidapp.service.StreamService.PlayerState.STOPPED;
import net.sourceforge.subsonic.androidapp.util.Constants;
import net.sourceforge.subsonic.androidapp.util.ImageLoader;
import net.sourceforge.subsonic.androidapp.util.Pair;
import net.sourceforge.subsonic.androidapp.util.SimpleServiceBinder;
import net.sourceforge.subsonic.androidapp.util.TwoLineListAdapter;
import net.sourceforge.subsonic.androidapp.util.Util;
import net.sourceforge.subsonic.androidapp.util.HorizontalSlider;

public class StreamQueueActivity extends OptionsMenuActivity implements AdapterView.OnItemClickListener {

    private static final String TAG = StreamQueueActivity.class.getSimpleName();
    private final StreamServiceConnection streamServiceConnection = new StreamServiceConnection();
    private final DownloadServiceConnection downloadServiceConnection = new DownloadServiceConnection();
    private ImageLoader imageLoader;
    private StreamService streamService;
    private DownloadService downloadService;

    private ViewFlipper flipper;
    private TextView currentTextView;
    private TextView albumArtTextView;
    private ImageView albumArtImageView;
    private ListView playlistView;
    private BroadcastReceiver broadcastReceiver;
    private TextView positionTextView;
    private TextView durationTextView;
    private TextView statusTextView;
    private HorizontalSlider progressBar;
    private ImageView previousButton;
    private ImageView nextButton;
    private ImageView pauseButton;
    private ImageView startButton;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.stream_queue);

        flipper = (ViewFlipper) findViewById(R.id.stream_queue_flipper);
        currentTextView = (TextView) findViewById(R.id.stream_queue_current);
        albumArtTextView = (TextView) findViewById(R.id.stream_queue_album_art_text);
        albumArtImageView = (ImageView) findViewById(R.id.stream_queue_album_art_image);
        positionTextView = (TextView) findViewById(R.id.stream_queue_position);
        durationTextView = (TextView) findViewById(R.id.stream_queue_duration);
        statusTextView = (TextView) findViewById(R.id.stream_queue_status);
        progressBar = (HorizontalSlider) findViewById(R.id.stream_queue_progress_bar);
        playlistView = (ListView) findViewById(R.id.stream_queue_list);
        previousButton = (ImageView) findViewById(R.id.stream_queue_previous);
        nextButton = (ImageView) findViewById(R.id.stream_queue_next);
        pauseButton = (ImageView) findViewById(R.id.stream_queue_pause);
        startButton = (ImageView) findViewById(R.id.stream_queue_start);

        currentTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFullscreenAlbumArt(true);
            }
        });

        albumArtImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFullscreenAlbumArt(false);
            }
        });

        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamService.previous();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamService.next();
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamService.pause();
            }
        });

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start();
            }
        });

        progressBar.setOnSliderChangeListener(new HorizontalSlider.OnSliderChangeListener() {
            @Override
            public void onSliderChanged(View view, int position) {
                streamService.seekTo(position);
            }
        });
        playlistView.setOnItemClickListener(this);

        bindService(new Intent(this, StreamService.class), streamServiceConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, DownloadService.class), downloadServiceConnection, Context.BIND_AUTO_CREATE);
        imageLoader = new ImageLoader();
    }

    private void showFullscreenAlbumArt(boolean fullscreen) {
        boolean empty = streamService == null || streamService.getCurrent() == null;
        int newDisplayedChild = fullscreen && !empty ? 0 : 1;

        if (flipper.getDisplayedChild() != newDisplayedChild) {

            if (newDisplayedChild == 0) {
                flipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.push_down_in));
                flipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.push_down_out));
            } else {
                flipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.push_up_in));
                flipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.push_up_out));
            }

            flipper.setDisplayedChild(newDisplayedChild);
        }
    }

    private void start() {
        StreamService.PlayerState state = streamService.getPlayerState();
        if (state == PAUSED || state == COMPLETED) {
            streamService.start();
        } else if (state == STOPPED) {
            streamService.play(streamService.getCurrentIndex());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Constants.INTENT_ACTION_STREAM_PROGRESS.equals(intent.getAction())) {
                    onStreamProgressChanged();
                } else if (Constants.INTENT_ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction())) {
                    onDownloadProgressChanged();
                } else if (Constants.INTENT_ACTION_STREAM_PLAYLIST.equals(intent.getAction())) {
                    onPlaylistChanged();
                } else if (Constants.INTENT_ACTION_STREAM_CURRENT.equals(intent.getAction())) {
                    onCurrentChanged();
                }
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_STREAM_PROGRESS));
        registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_DOWNLOAD_PROGRESS));
        registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_STREAM_PLAYLIST));
        registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_STREAM_CURRENT));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                Log.i(TAG, "Got MEDIA_PLAY_PAUSE key event.");
                if (streamService.getPlayerState() == STARTED) {
                    streamService.pause();
                } else {
                    start();
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                Log.i(TAG, "Got MEDIA_PREVIOUS key event.");
                streamService.previous();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                Log.i(TAG, "Got MEDIA_NEXT key event.");
                streamService.next();
                break;
            default:
        }
        return super.onKeyDown(keyCode, event);
    }

    private void onPlaylistChanged() {
        if (streamService == null) {
            return;
        }

        List<MusicDirectory.Entry> queue = streamService.getPlaylist();

        playlistView.setAdapter(new SongListAdapter(queue));
        if (queue.isEmpty()) {
            currentTextView.setText(null);
            currentTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    private void onCurrentChanged() {
        if (streamService == null) {
            return;
        }
        MusicDirectory.Entry current = streamService.getCurrentSong();
        if (current != null) {
            currentTextView.setText(current.getTitle());
            albumArtTextView.setText(current.getTitle() + " - " + current.getArtist());
            imageLoader.loadImage(currentTextView, current, 48);
            imageLoader.loadImage(albumArtImageView, current, 320);
        }
    }

    private void onStreamProgressChanged() {
        if (streamService == null) {
            return;
        }
        Pair<MusicDirectory.Entry, Pair<Long, Long>> current = streamService.getCurrent();
        if (current != null) {

            int millisPlayed = current.getSecond().getFirst().intValue();
            int millisTotal = current.getSecond().getSecond().intValue();

            positionTextView.setText(Util.formatDuration(millisPlayed / 1000));
            durationTextView.setText(Util.formatDuration(millisTotal / 1000));
            progressBar.setMax(millisTotal == 0 ? 100 : millisTotal); // Work-around for apparent bug.
            progressBar.setProgress(millisPlayed);

            File file = streamService.getSongFile(current.getFirst(), false);
            progressBar.setSlidingEnabled(file.exists());
        }

        StreamService.PlayerState playerState = streamService.getPlayerState();
        statusTextView.setText(playerState.toString());

        if (playerState == STARTED) {
            pauseButton.setVisibility(View.VISIBLE);
            startButton.setVisibility(View.GONE);
        } else {
            pauseButton.setVisibility(View.GONE);
            startButton.setVisibility(View.VISIBLE);
        }
    }

    private void onDownloadProgressChanged() {
        if (downloadService == null || streamService == null) {
            return;
        }

        Pair<MusicDirectory.Entry, Pair<Long, Long>> current = downloadService.getCurrent();

        if (current != null && current.getFirst() == streamService.getCurrentSong() ) {
            Long bytesDownloaded = current.getSecond().getFirst();
            Long bytesTotal = current.getSecond().getSecond();
            if (bytesTotal != null) {
                positionTextView.setText(Util.formatBytes(bytesDownloaded));
                durationTextView.setText(Util.formatBytes(bytesTotal));
                progressBar.setMax(bytesTotal.intValue());
                progressBar.setProgress(bytesDownloaded.intValue());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(streamServiceConnection);
        unbindService(downloadServiceConnection);
        imageLoader.cancel();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        streamService.play(position);
    }

    private class StreamServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            streamService = ((SimpleServiceBinder<StreamService>) service).getService();
            Log.i(TAG, "Connected to Stream Service");
            onPlaylistChanged();
            onCurrentChanged();
            onStreamProgressChanged();
            showFullscreenAlbumArt(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            streamService = null;
            Log.i(TAG, "Disconnected from Stream Service");
        }
    }

    private class DownloadServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            downloadService = ((SimpleServiceBinder<DownloadService>) service).getService();
            Log.i(TAG, "Connected to Download Service");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            downloadService = null;
            Log.i(TAG, "Disconnected from Download Service");
        }
    }
    private class SongListAdapter extends TwoLineListAdapter<MusicDirectory.Entry> {
        private final List<MusicDirectory.Entry> queue;

        public SongListAdapter(List<MusicDirectory.Entry> queue) {
            super(StreamQueueActivity.this, queue);
            this.queue = queue;
        }

        @Override
        protected String getFirstLine(MusicDirectory.Entry song) {
            return (queue.indexOf(song) + 1) + "  " + song.getTitle();
        }

        @Override
        protected String getSecondLine(MusicDirectory.Entry song) {
            StringBuilder builder = new StringBuilder();
            builder.append(song.getAlbum()).append(" - ").append(song.getArtist());
            return builder.toString();
        }
    }
}