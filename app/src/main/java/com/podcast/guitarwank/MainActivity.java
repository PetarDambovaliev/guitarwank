package com.podcast.guitarwank;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.app.ActionBar.LayoutParams;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

//---Implement OnSeekBarChangeListener to keep track of seek bar changes ---
public class MainActivity extends Activity implements OnSeekBarChangeListener {
    Intent serviceIntent;
    public static Button buttonPlayStop;
    public static ScrollView scrollView;

    // -- PUT THE NAME OF YOUR AUDIO FILE HERE...URL GOES IN THE SERVICE
    String strAudioLink;

    private boolean isOnline;
    private boolean boolMusicPlaying = false;
    TelephonyManager telephonyManager;
    PhoneStateListener listener;

    // --Seekbar variables --
    private SeekBar seekBar;
    private int seekMax;
    private static int songEnded = 0;
    private static int pausedAt = 0;
    boolean mBroadcastIsRegistered;

    // --Set up constant ID for broadcast of seekbar position--
    public static final String BROADCAST_SEEKBAR = "com.podcast.guitarwank.sendseekbar";
    Intent intent;

    // Progress dialogue and broadcast receiver variables
    boolean mBufferBroadcastIsRegistered;
    private ProgressDialog pdBuff = null;

    //firebase
    private DatabaseReference db;
    private static List<Song> songs;

    private ListView layout;

    public static boolean switchedSong = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setFirebase();
    }

    private void createService() {
        try {
            serviceIntent = new Intent(this, MediaPlayerService.class);
            intent = new Intent(BROADCAST_SEEKBAR);

            initViews();
            setListeners();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),
                    e.getClass().getName() + " " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private List<String> createTitles() {
        layout = (ListView) findViewById(R.id.linear);
        List<String> titles = new ArrayList<>();

        //set default first track
        strAudioLink = songs.get(0).getLink();

        for (final Song song : songs) {
            TextView title = new TextView(this);
            title.setText(song.getTitle());
            title.setWidth(LayoutParams.WRAP_CONTENT);
            title.setHeight(LayoutParams.WRAP_CONTENT);

            //Add bottom border
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(0xFF00FF00); // Changes this drawbale to use a single color instead of a gradient
            gd.setCornerRadius(5);
            gd.setStroke(1, 0xFF000000);
            title.setBackground(gd);

            titles.add(title.getText().toString());
        }



        layout.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
                String title = (String)adapter.getItemAtPosition(position);

                for (Song song : songs) {
                    if (song.getTitle() == title) {
                        strAudioLink = song.getLink();
                    }
                }

                seekBar.setProgress(0);
                switchedSong = true;
                playAudio();
                boolMusicPlaying = true;
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        layout.setAdapter(adapter);

        return titles;
    }

    // -- Broadcast Receiver to update position of seekbar from service --
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent serviceIntent) {
            updateUI(serviceIntent);
        }
    };

    private void updateUI(Intent serviceIntent) {
        String counter = serviceIntent.getStringExtra("counter");
        String mediamax = serviceIntent.getStringExtra("mediamax");
        String strSongEnded = serviceIntent.getStringExtra("song_ended");
        int seekProgress = Integer.parseInt(counter);
        seekMax = Integer.parseInt(mediamax);
        songEnded = Integer.parseInt(strSongEnded);
        seekBar.setMax(seekMax);
        seekBar.setProgress(seekProgress);
        if (songEnded == 1) {
            buttonPlayStop.setBackgroundResource(R.drawable.play);
        }
    }
    // --End of seekbar update code--

    // --- Set up initial screen ---
    private void initViews() {
        buttonPlayStop = (Button) findViewById(R.id.playButton);
        if (!switchedSong) {
            buttonPlayStop.setBackgroundResource(R.drawable.play);
        } else {
            buttonPlayStop.setBackgroundResource(R.drawable.pause);
        }


        // --Reference seekbar in main.xml
        seekBar = (SeekBar) findViewById(R.id.seekBar);
    }

    // --- Set up listeners ---
    private void setListeners() {
        buttonPlayStop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonPlayStopClick();
            }
        });
        seekBar.setOnSeekBarChangeListener(this);
    }

    // --- invoked from ButtonPlayStop listener above ----
    private void buttonPlayStopClick() {

        if (!boolMusicPlaying) {
            buttonPlayStop.setBackgroundResource(R.drawable.pause);
            playAudio();
            boolMusicPlaying = true;
        } else {
            if (boolMusicPlaying) {
                if (seekBar.getProgress() > 0 && seekBar.getProgress() < seekMax) {
                    pausedAt = seekBar.getProgress();
                }
                if (!switchedSong) {
                    buttonPlayStop.setBackgroundResource(R.drawable.play);
                } else {
                    buttonPlayStop.setBackgroundResource(R.drawable.pause);
                }

                stopMyPlayService();
                boolMusicPlaying = false;
            }
        }
    }

    // --- Stop service (and music) ---
    private void stopMyPlayService() {
        // --Unregister broadcastReceiver for seekbar
        if (mBroadcastIsRegistered) {
            try {
                unregisterReceiver(broadcastReceiver);
                mBroadcastIsRegistered = false;
            } catch (Exception e) {
                // Log.e(TAG, "Error in Activity", e);
                // TODO Auto-generated catch block

                e.printStackTrace();
                Toast.makeText(
                        getApplicationContext(),
                        e.getClass().getName() + " " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

        try {
            stopService(serviceIntent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),
                    e.getClass().getName() + " " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        boolMusicPlaying = false;
    }

    // --- Start service and play music ---
    private void playAudio() {

        checkConnectivity();
        if (isOnline) {
            stopMyPlayService();

            if (switchedSong) {
                buttonPlayStop.setBackgroundResource(R.drawable.pause);
                //switchedSong = false;
            }

            serviceIntent.putExtra("sentAudioLink", strAudioLink);
            serviceIntent.putExtra("pausedAt", pausedAt);

            try {
                startService(serviceIntent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(
                    getApplicationContext(),
                    e.getClass().getName() + " " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            }

            // -- Register receiver for seekbar--
            registerReceiver(broadcastReceiver, new IntentFilter(
                    MediaPlayerService.BROADCAST_ACTION));
            ;
            mBroadcastIsRegistered = true;

        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Network Not Connected...");
            alertDialog.setMessage("Please connect to a network and try again");
            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // here you can add functions
                }
            });
            alertDialog.setIcon(R.mipmap.ic_launcher);
            buttonPlayStop.setBackgroundResource(R.drawable.play);
            alertDialog.show();
        }
        if (switchedSong) {
            buttonPlayStop.setBackgroundResource(R.drawable.pause);
            //switchedSong = false;
        }
    }

    // Handle progress dialogue for buffering...
    private void showPD(Intent bufferIntent) {
        String bufferValue = bufferIntent.getStringExtra("buffering");
        int bufferIntValue = Integer.parseInt(bufferValue);

        // When the broadcasted "buffering" value is 1, show "Buffering"
        // progress dialogue.
        // When the broadcasted "buffering" value is 0, dismiss the progress
        // dialogue.

        switch (bufferIntValue) {
            case 0:
                // Log.v(TAG, "BufferIntValue=0 RemoveBufferDialogue");
                // txtBuffer.setText("");
                if (pdBuff != null) {
                    pdBuff.dismiss();
                }
                break;

            case 1:
                BufferDialogue();
                break;

            // Listen for "2" to reset the button to a play button
            case 2:
                if  (switchedSong) {
                    buttonPlayStop.setBackgroundResource(R.drawable.pause);
                    switchedSong = false;
                } else {
                    buttonPlayStop.setBackgroundResource(R.drawable.play);
                }

                break;

        }
    }

    // Progress dialogue...
    private void BufferDialogue() {
        pdBuff = ProgressDialog.show(MainActivity.this, "Buffering...",
                "Wank on...", true);
    }

    // Set up broadcast receiver
    private BroadcastReceiver broadcastBufferReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent bufferIntent) {
            showPD(bufferIntent);
        }
    };

    private void checkConnectivity() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                .isConnectedOrConnecting()
                || cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                .isConnectedOrConnecting())
            isOnline = true;
        else
            isOnline = false;
    }

    // -- onPause, unregister broadcast receiver. To improve, also save screen data ---
    @Override
    protected void onPause() {
        // Unregister broadcast receiver
        if (mBufferBroadcastIsRegistered) {
            unregisterReceiver(broadcastBufferReceiver);
            mBufferBroadcastIsRegistered = false;
        }
        super.onPause();
    }


    // -- onResume register broadcast receiver. To improve, retrieve saved screen data ---
    @Override
    protected void onResume() {
        // Register broadcast receiver
        if (!mBufferBroadcastIsRegistered) {
            registerReceiver(broadcastBufferReceiver, new IntentFilter(
                    MediaPlayerService.BROADCAST_BUFFER));
            mBufferBroadcastIsRegistered = true;
        }
        super.onResume();
    }


    // --- When user manually moves seekbar, broadcast new position to service ---
    @Override
    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
        // TODO Auto-generated method stub
        if (fromUser) {
            int seekPos = sb.getProgress();
            intent.putExtra("seekpos", seekPos);
            sendBroadcast(intent);
        }
    }


    // --- The following two methods are alternatives to track seekbar if moved.
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }

    public void setFirebase () {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        db = database.getReference();

        db.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                songs = new ArrayList<Song>();
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                for (DataSnapshot child : dataSnapshot.getChildren()) {
                    String[] value = child.getValue().toString().split("\\|");
                    Song song = new Song(value[0], value[1]);
                    songs.add(song);
                }

                createTitles();
                createService();
                //Log.d(TAG, "Value is: " + value);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                String a = error.toException().toString();
                //Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
    }
}