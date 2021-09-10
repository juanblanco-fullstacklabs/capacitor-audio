package com.justicointeractive.plugins.capacitor.audio;

import android.Manifest;
import android.os.Handler;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;

import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
  name = "Audio",
  permissions = {
    @Permission(
      strings = { Manifest.permission.ACCESS_NETWORK_STATE },
      alias = "network"
    ),
    @Permission(
      strings = { Manifest.permission.INTERNET }, 
      alias = "internet"
    ),
    @Permission(
      strings = { Manifest.permission.WAKE_LOCK },
      alias = "wakelock"
    ),
  }
)
public class AudioPlugin extends Plugin {

  SimpleExoPlayer player;
  AudioPluginNotificationManager audioPlayerNotificationManager;

  public void load() {
    player = new SimpleExoPlayer.Builder(this.getContext())
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .build();

    player.addListener(new Player.Listener() {
      @Override
      public void onIsPlayingChanged(boolean isPlaying) {
        updateProgressBar();
      }
    });

    audioPlayerNotificationManager = new AudioPluginNotificationManager(getContext(), player);
  }

  Handler handler = new Handler();

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
      notifyListeners("playTimeUpdate", data);

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

  private final Runnable updateProgressAction = this::updateProgressBar;

  @PluginMethod()
  public void playList(PluginCall call) {
    JSArray value = call.getArray("items");

    getBridge().executeOnMainThread(() -> {

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
   });
  }


  @PluginMethod()
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

  @PluginMethod()
  public void pausePlay(PluginCall call) {
    getBridge().executeOnMainThread(() -> {
      player.pause();
      call.resolve();
    });
  }

  @PluginMethod()
  public void resumePlay(PluginCall call) {
    getBridge().executeOnMainThread(() -> {
      player.play();
      call.resolve();
    });
  }

  @PluginMethod()
  public void seek(PluginCall call) {
    Double toSeconds = call.getDouble("to");
    if (toSeconds == null) {
      call.reject("missing time to seek to");
      return;
    }
    long toMs = (long)(toSeconds * 1000);
    getBridge().executeOnMainThread(() -> {
      player.seekTo(toMs);
      call.resolve();
    });
  }
}
