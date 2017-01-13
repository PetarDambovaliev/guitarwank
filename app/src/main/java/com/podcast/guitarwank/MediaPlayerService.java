package com.podcast.guitarwank;

import java.io.IOException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.RemoteViews;
import android.widget.Toast;

public class MediaPlayerService extends Service implements OnCompletionListener,
        OnPreparedListener, OnErrorListener, OnSeekCompleteListener,
        OnInfoListener, OnBufferingUpdateListener {

    private static final String TAG = "TELSERVICE";
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private String sntAudioLink;

    //-- URL location of audio clip PUT YOUR AUDIO URL LOCATION HERE ---
    private static final String URL_STRING = "";

    // Set up the notification ID
    private static final int NOTIFICATION_ID = 1;
    private boolean isPausedInCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private Notification notification;
    private NotificationCompat.Action action;

    // ---Variables for seekbar processing---
    String sntSeekPos;
    int intSeekPos;
    int mediaPosition;
    int mediaMax;
    //Intent intent;
    private final Handler handler = new Handler();
    private static int songEnded;
    public static final String BROADCAST_ACTION = "com.podcast.guitarwank.seekprogress";

    // Set up broadcast identifier and intent
    public static final String BROADCAST_BUFFER = "com.podcast.guitarwank.broadcastbuffer";

    Intent bufferIntent;
    Intent seekIntent;

    // Declare headsetSwitch variable
    private int headsetSwitch = 1;

    // OnCreate
    @Override
    public void onCreate() {
        Log.v(TAG, "Creating Service");
        // android.os.Debug.waitForDebugger();
        // Instantiate bufferIntent to communicate with Activity for progress
        // dialogue
        bufferIntent = new Intent(BROADCAST_BUFFER);
        // ---Set up intent for seekbar broadcast ---
        seekIntent = new Intent(BROADCAST_ACTION);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.reset();

        // Register headset receiver
        registerReceiver(headsetReceiver, new IntentFilter(
                Intent.ACTION_HEADSET_PLUG));

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // ---Set up receiver for seekbar change ---
        registerReceiver(broadcastReceiver, new IntentFilter(
                MainActivity.BROADCAST_SEEKBAR));

        // Manage incoming phone calls during playback. Pause mp on incoming,
        // resume on hangup.
        // -----------------------------------------------------------------------------------
        // Get the telephony manager
        Log.v(TAG, "Starting telephony");
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        Log.v(TAG, "Starting listener");
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // String stateString = "N/A";
                Log.v(TAG, "Starting CallStateChange");
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            isPausedInCall = true;
                        }

                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (isPausedInCall) {
                                isPausedInCall = false;
                                playMedia();
                            }

                        }
                        break;
                }
            }
        };

        // Register the listener with the telephony manager
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);

        // Insert notification start
        initNotification(R.drawable.noticepause, "Pause");

        boolean noticeClick = intent.getExtras().getBoolean("noticeClick");

        if (noticeClick) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                initNotification(R.drawable.noticeplay, "Play");

                if (!MainActivity.switchedSong) {
                    MainActivity.buttonPlayStop.setBackgroundResource(R.drawable.play);
                }

            } else {
                mediaPlayer.start();
                MainActivity.buttonPlayStop.setBackgroundResource(R.drawable.pause);
            }
            return START_STICKY;
        }

        int pausedAt = intent.getExtras().getInt("pausedAt");

        sntAudioLink = intent.getExtras().getString("sentAudioLink");
        mediaPlayer.reset();

        // Set up the MediaPlayer data source using the strAudioLink value
        if (!mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.setDataSource(sntAudioLink);

                // Send message to Activity to display progress dialogue
                sendBufferingBroadcast();
                if (pausedAt == 0) {
                    // Prepare mediaplayer
                    mediaPlayer.prepareAsync();
                } else {
                    mediaPlayer.prepare();
                }

            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
            }
        }
        // --- Set up seekbar handler ---
        setupHandler();

        if (pausedAt > 0) {
            mediaPlayer.seekTo(pausedAt);
        }

        return START_STICKY;
    }

    // ---Send seekbar info to activity----
    private void setupHandler() {
        handler.removeCallbacks(sendUpdatesToUI);
        handler.postDelayed(sendUpdatesToUI, 200); // 1 second
    }

    private Runnable sendUpdatesToUI = new Runnable() {
        public void run() {
            // // Log.d(TAG, "entered sendUpdatesToUI");

            LogMediaPosition();

            handler.postDelayed(this, 300); // 2 seconds

        }
    };

    private void LogMediaPosition() {
        // // Log.d(TAG, "entered LogMediaPosition");
        if (mediaPlayer.isPlaying()) {
            mediaPosition = mediaPlayer.getCurrentPosition();
            // if (mediaPosition < 1) {
            // Toast.makeText(this, "Buffering...", Toast.LENGTH_SHORT).show();
            // }
            mediaMax = mediaPlayer.getDuration();
            //seekIntent.putExtra("time", new Date().toLocaleString());
            seekIntent.putExtra("counter", String.valueOf(mediaPosition));
            seekIntent.putExtra("mediamax", String.valueOf(mediaMax));
            seekIntent.putExtra("song_ended", String.valueOf(songEnded));
            sendBroadcast(seekIntent);
        }
    }

    // --Receive seekbar position if it has been changed by the user in the
    // activity
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSeekPos(intent);
        }
    };

    // Update seek position from Activity
    public void updateSeekPos(Intent intent) {
        int seekPos = intent.getIntExtra("seekpos", 0);
        if (mediaPlayer.isPlaying()) {
            handler.removeCallbacks(sendUpdatesToUI);
            mediaPlayer.seekTo(seekPos);
            setupHandler();
        }

    }

    // ---End of seekbar code

    // If headset gets unplugged, stop music and service.
    private BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        private boolean headsetConnected = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            // Log.v(TAG, "ACTION_HEADSET_PLUG Intent received");
            if (intent.hasExtra("state")) {
                if (headsetConnected && intent.getIntExtra("state", 0) == 0) {
                    headsetConnected = false;
                    headsetSwitch = 0;
                    // Log.v(TAG, "State =  Headset disconnected");
                    // headsetDisconnected();
                } else if (!headsetConnected
                        && intent.getIntExtra("state", 0) == 1) {
                    headsetConnected = true;
                    headsetSwitch = 1;
                    // Log.v(TAG, "State =  Headset connected");
                }

            }

            switch (headsetSwitch) {
                case (0):
                    headsetDisconnected();
                    break;
                case (1):
                    break;
            }
        }

    };

    private void headsetDisconnected() {
        stopMedia();
        stopSelf();

    }

    // --- onDestroy, stop media player and release.  Also stop phoneStateListener, notification, receivers...---
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
        }

        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener,
                    PhoneStateListener.LISTEN_NONE);
        }

        // Cancel the notification
        cancelNotification();

        // Unregister headsetReceiver
        unregisterReceiver(headsetReceiver);

        // Unregister seekbar receiver
        unregisterReceiver(broadcastReceiver);

        // Stop the seekbar handler from sending updates to UI
        handler.removeCallbacks(sendUpdatesToUI);

        // Service ends, need to tell activity to display "Play" button
        resetButtonPlayStopBroadcast();
    }

    // Send a message to Activity that audio is being prepared and buffering
    // started.
    private void sendBufferingBroadcast() {
        // Log.v(TAG, "BufferStartedSent");
        bufferIntent.putExtra("buffering", "1");
        sendBroadcast(bufferIntent);
    }

    // Send a message to Activity that audio is prepared and ready to start
    // playing.
    private void sendBufferCompleteBroadcast() {
        // Log.v(TAG, "BufferCompleteSent");
        bufferIntent.putExtra("buffering", "0");
        sendBroadcast(bufferIntent);
    }

    // Send a message to Activity to reset the play button.
    private void resetButtonPlayStopBroadcast() {
        // Log.v(TAG, "BufferCompleteSent");
        bufferIntent.putExtra("buffering", "2");
        sendBroadcast(bufferIntent);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer arg0, int arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean onInfo(MediaPlayer arg0, int arg1, int arg2) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {

        if (!mediaPlayer.isPlaying()){
            playMedia();
            Toast.makeText(this,
                    "SeekComplete", Toast.LENGTH_SHORT).show();
        }

    }

    //---Error processing ---
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Toast.makeText(this,
                        "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra,
                        Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Toast.makeText(this, "MEDIA ERROR SERVER DIED " + extra,
                        Toast.LENGTH_SHORT).show();
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Toast.makeText(this, "MEDIA ERROR UNKNOWN " + extra,
                        Toast.LENGTH_SHORT).show();
                break;
        }
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer arg0) {

        // Send a message to activity to end progress dialogue

        sendBufferCompleteBroadcast();
        playMedia();

    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        // When song ends, need to tell activity to display "Play" button
        stopMedia();
        stopSelf();

    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    public void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    // Add for Telephony Manager
    public void pauseMedia() {
        // Log.v(TAG, "Pause Media");
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }

    }

    public void stopMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    // Create Notification
    private void initNotification(int buttonImage, String text) {
        int notifReCode = 1;

        //What happen when you will click on button
        Intent intent = new Intent(this, MediaPlayerService.class);
        intent.putExtra("noticeClick", true);
        PendingIntent pendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        //Button
         action = new NotificationCompat.Action.Builder(buttonImage, text, pendingIntent).build();
        RemoteViews notificationView = new RemoteViews(getPackageName(), R.layout.notification_controls);
        //notificationView.setImageViewResource(R.id.playButton, R.drawable.pause);

        //Notification
         notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(action) //add buton
                .setContent(notificationView)
                 .setPriority(Notification.PRIORITY_HIGH)
                .build();

        //Send notification
        NotificationManager notificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, notification);
    }

    // Cancel Notification
    private void cancelNotification() {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

        mNotificationManager.cancel(NOTIFICATION_ID);
    }
}
