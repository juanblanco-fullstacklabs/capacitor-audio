package com.justicointeractive.plugins.capacitor.audio;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioPluginService extends Service {
  SimpleExoPlayer player;
  public AudioPluginNotificationManager audioPlayerNotificationManager;
  public MediaSessionCompat mediaSession;
  Map<String, Object> currentItem = new HashMap<>();
  AudioPlugin plugin;

  private final AudioPluginServiceBinder binder = new AudioPluginServiceBinder();

  public AudioPluginService() {  }

  public class AudioPluginServiceBinder extends Binder {
    void setPluginInstance(AudioPlugin plugin){
      AudioPluginService.this.plugin = plugin;
    }
    public AudioPluginService getService() {
      return AudioPluginService.this;
    }
  }

  public class AudioMediaSessionCallback extends MediaSessionCompat.Callback {
    @Override
    public void onPlayFromMediaId(String mediaId, Bundle extras) {
      Log.d("audioPlugin", "onPlayFromMediaId: " + mediaId);
      try {
        List<JSONObject> items = new ArrayList<>();
        JSONObject item = new JSONObject();
        item.put("src", extras.getString("src"));
        item.put("title", extras.getString("title"));
        item.put("artist", extras.getString("artist"));
        item.put("artwork", extras.getString("artwork"));
        items.add(item);
        AudioPluginService.this.playList(items);
        new Thread(() -> {
          try {
            AudioPluginService.this.setPlaying(item);
          }
          catch (Exception ex) {
            ex.printStackTrace();
          }
        }).start();
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void onPlay() {
      AudioPluginService.this.player.play();
    }

    @Override
    public void onPause() {
      AudioPluginService.this.player.pause();
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    player = new SimpleExoPlayer.Builder(this.getApplicationContext())
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .setAudioAttributes(new AudioAttributes.Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.CONTENT_TYPE_MUSIC)
              .build(), true)
      .build();

    player.addListener(new Player.Listener() {
      @Override
      public void onIsPlayingChanged(boolean isPlaying) {
        updateProgressBar();
      }
    });

    mediaSession = new MediaSessionCompat( this.getApplicationContext(), "tag");
    mediaSession.setCallback(new AudioMediaSessionCallback());
    player.addListener(new Player.Listener() {
      @Override
      public void onIsPlayingChanged(boolean isPlaying) {
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, player.getCurrentPosition(), player.getPlaybackParameters().speed)
                .setActions(PlaybackStateCompat.ACTION_PLAY|PlaybackStateCompat.ACTION_PAUSE)
                .build());
      }
    });

    audioPlayerNotificationManager = new AudioPluginNotificationManager(
      this.getApplicationContext(),
      player,
      this,
      new PlayerNotificationManager.NotificationListener() {
      @Override
      public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
        startForeground(notificationId, notification);
      }

      @Override
      public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
        stopForeground(true);
      }
    });
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }


  Handler handler = new Handler();

  private final Runnable updateProgressAction = this::updateProgressBar;

  private void updateProgressBar() {
    // Remove scheduled updates.
    handler.removeCallbacks(updateProgressAction);
    // Schedule an update if necessary.
    int playbackState = player == null ? Player.STATE_IDLE : player.getPlaybackState();
    if (playbackState == Player.STATE_READY) {
      boolean isLive = player.isCurrentWindowLive();
      double durationSecs = isLive || player == null ? 0 : player.getDuration()/1000.0;
      long positionMs = player == null ? 0 : player.getCurrentPosition();
      double positionSecs = positionMs/1000.0;

      JSObject data = new JSObject();
      data.put("duration", durationSecs);
      data.put("currentTime", positionSecs);
      data.put("isLive", isLive);
      plugin.notifyListeners("playTimeUpdate", data);

      long delayMs;
      if (player.getPlayWhenReady() && playbackState == Player.STATE_READY) {
        delayMs = 1000 - (positionMs % 1000);
        if (delayMs < 200) {
          delayMs += 1000;
        }
      } else {
        delayMs = 1000;
      }
      handler.postDelayed(updateProgressAction, delayMs);
    }
  }

  public void playList(PluginCall call) {
    JSArray value = call.getArray("items");

    try {
      this.playList(value.toList());
    } catch (JSONException ex) {
      call.reject("unable to parse playlist");
      return;
    }

    call.resolve();
  }

  public void playList(List<JSONObject> items) throws JSONException {
    player.clearMediaItems();

    for( JSONObject item : items) {
      String src  = item.getString("src");
      MediaItem mediaItem = MediaItem.fromUri(src);
      player.addMediaItem(mediaItem);
    }

    player.prepare();

    player.play();

    player.seekTo(0);

    mediaSession.setActive(true);
  }

  public void setPlaying(PluginCall call) {
    String title = call.getString("title");
    String artist = call.getString("artist");
    String artwork = call.getString("artwork");

    this.setCurrentItem(
      title,
      artist,
      artwork
    );

    call.resolve();
  }

  public void setPlaying(JSONObject jobject) throws JSONException {
    String title = jobject.getString("title");
    String artist = jobject.getString("artist");
    String artwork = jobject.getString("artwork");

    this.setCurrentItem(
      title,
      artist,
      artwork
    );
  }

  public void pausePlay(PluginCall call) {
      player.pause();
      call.resolve();
  }

  public void resumePlay(PluginCall call) {
      player.play();
      call.resolve();
  }

  public void seek(PluginCall call) {
    Double toSeconds = call.getDouble("to");
    if (toSeconds == null) {
      call.reject("missing time to seek to");
      return;
    }
    long toMs = (long)(toSeconds * 1000);
    player.seekTo(toMs);
    call.resolve();
  }

  public void setCurrentItem(String title, String artist, String artwork) {
    MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

    currentItem.put("title", title);
    metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
    currentItem.put("artist", artist);
    metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);

    if (artwork != null) {
      Bitmap artworkBitmap = null;
      try {
        artworkBitmap = BitmapFactory.decodeStream(new URL(artwork).openStream());
      } catch (IOException e) {
        e.printStackTrace();
      }
      currentItem.put("artwork", artworkBitmap);
      metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap);
    }

    audioPlayerNotificationManager.notificationManager.invalidate();
    mediaSession.setMetadata(metadataBuilder.build());
  }
}
