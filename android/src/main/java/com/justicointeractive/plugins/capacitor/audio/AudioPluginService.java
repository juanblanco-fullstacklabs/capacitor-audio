package com.justicointeractive.plugins.capacitor.audio;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.json.JSONException;
import org.json.JSONObject;

public class AudioPluginService extends Service {
  SimpleExoPlayer player;
  AudioPluginNotificationManager audioPlayerNotificationManager;

  AudioPlugin plugin;

  private final AudioPluginServiceBinder binder = new AudioPluginServiceBinder();

  public AudioPluginService() {  }

  public class AudioPluginServiceBinder extends Binder {
    void setPluginInstance(AudioPlugin plugin){
      AudioPluginService.this.plugin = plugin;
    }
    AudioPluginService getService() {
      return AudioPluginService.this;
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    player = new SimpleExoPlayer.Builder(this.getApplicationContext())
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .build();

    player.addListener(new Player.Listener() {
      @Override
      public void onIsPlayingChanged(boolean isPlaying) {
        updateProgressBar();
      }
    });

    audioPlayerNotificationManager = new AudioPluginNotificationManager(this.getApplicationContext(), player, new PlayerNotificationManager.NotificationListener() {
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

    player.clearMediaItems();

    try {
      for( JSONObject item : value.<JSONObject>toList()) {
        String src  = item.getString("src");
        MediaItem mediaItem = MediaItem.fromUri(src);
        player.addMediaItem(mediaItem);
      }
    } catch (JSONException ex) {
      call.reject("unable to parse playlist");
      return;
    }

    player.prepare();

    player.play();

    player.seekTo(0);

    call.resolve();
  }

  public void setPlaying(PluginCall call) {
    String title = call.getString("title");
    String artist = call.getString("artist");
    String artwork = call.getString("artwork");

    audioPlayerNotificationManager.setCurrentItem(
      title,
      artist,
      artwork
    );

    call.resolve();
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
}
