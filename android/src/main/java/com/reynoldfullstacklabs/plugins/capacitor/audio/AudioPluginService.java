package com.reynoldfullstacklabs.plugins.capacitor.audio;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.getcapacitor.CapConfig;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLEncoder;
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
  RequestQueue requestQueue;
  Handler mainHandler;

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

        if (extras.get("car_app_type") != null) {
          Bundle selectContentParameters = new Bundle();
          selectContentParameters.putString("content_type", extras.getString("contentType"));
          selectContentParameters.putString("item_id", extras.getString("itemId"));
          selectContentParameters.putString("car_app_type", extras.getString("car_app_type"));
          logEventIfFirebaseAnalyticsIsAvailable("select_content", selectContentParameters);
        }

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
    public void onPlayFromSearch(String query, Bundle extras) {
      CapConfig capConfig = CapConfig.loadDefault(AudioPluginService.this.getApplicationContext());
      String defaultRootUrl = capConfig.getPluginConfiguration("capacitor-audio").getString("audioSearchUrl", null);
      SharedPreferences prefs = AudioPluginService.this.getApplicationContext().getSharedPreferences("audioPlugin", Context.MODE_PRIVATE);
      String rootUrl = prefs.getString("audioSearchUrl", defaultRootUrl);
      JsonObjectRequest request = null;
      try {
        request = new JsonObjectRequest(
          Request.Method.GET,
          rootUrl.replace(URLEncoder.encode("$QUERY", "utf-8"), URLEncoder.encode(query, "utf-8")),
          null,
          response -> {
            new Thread(() -> {
              try {
                ArrayList<JSONObject> playList = new ArrayList<>();
                JSONArray items = response.getJSONArray("items");
                for (int itemIndex = 0; itemIndex < items.length(); itemIndex++) {
                  JSONObject item = items.getJSONObject(itemIndex);

                  String itemUrl = item.has("url") ? item.getString("url") : null;

                  if (itemUrl == null || itemUrl.length() == 0) {
                    continue;
                  }

                  JSONObject playListItem = new JSONObject();
                  playListItem.put("src", itemUrl);
                  playListItem.put("title", item.getString("title"));
                  playListItem.put("artist", item.getString("description"));
                  playListItem.put("artwork", item.getString("imageUrl"));
                  playList.add(playListItem);
                }
                mainHandler.post(() -> {
                  try {
                    AudioPluginService.this.playList(playList);
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                });
              } catch (Exception e) {
                e.printStackTrace();
              }


            }).start();
          },
          error -> {
            error.printStackTrace();
          }
        );
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }
      requestQueue.add(request);
    }

    @Override
    public void onPlay() {
      AudioPluginService.this.player.play();
    }

    @Override
    public void onPause() {
      AudioPluginService.this.player.pause();
    }

    @Override
    public void onStop() {
      AudioPluginService.this.player.stop();
    }

    @Override
    public void onSkipToNext() {
      AudioPluginService.this.player.next();
    }

    @Override
    public void onSkipToPrevious() {
      AudioPluginService.this.player.previous();
    }
  }

  @Override
  public void onDestroy() {
    AudioPluginService.this.player.stop();
    super.onDestroy();
  }

  @Override
  public void onCreate() {
    super.onCreate();

    requestQueue = Volley.newRequestQueue(this.getApplicationContext());

    mainHandler = new Handler(this.getMainLooper());

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

        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem != null && currentItem.mediaMetadata != null && currentItem.mediaMetadata.title != null) {
          new Thread(() -> {
            AudioPluginService.this.setCurrentItem(
                    currentItem.mediaMetadata.title.toString(),
                    currentItem.mediaMetadata.artist.toString(),
                    currentItem.mediaMetadata.artworkUri.toString()
            );
          }).start();
        }
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
      if (plugin != null) {
        plugin.notifyListeners("playTimeUpdate", data);
      }

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

      MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();

      if (item.has("title")) {
        metadataBuilder.setTitle(item.getString("title"));
      }
      if (item.has("artist")) {
        metadataBuilder.setArtist(item.getString("artist"));
      }
      if (item.has("album")) {
        metadataBuilder.setAlbumTitle(item.getString("album"));
      }
      if (item.has("artwork")) {
        metadataBuilder.setArtworkUri(Uri.parse(item.getString("artwork")));
      }

      player.addMediaItem(
        new MediaItem.Builder()
          .setUri(src)
          .setMediaMetadata(metadataBuilder.build())
          .build()
      );
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

  public void logEventIfFirebaseAnalyticsIsAvailable(String name, Bundle parameters) {
    try {
      Class firebaseAnalyticsClass = Class.forName("com.google.firebase.analytics.FirebaseAnalytics");
      Method firebaseAnalyticsGetInstanceMethod = firebaseAnalyticsClass.getMethod("getInstance", Context.class);
      Object firebaseAnalyticsInstance = firebaseAnalyticsGetInstanceMethod.invoke(null, this.getApplicationContext());
      Method logEventMethod = firebaseAnalyticsClass.getMethod("logEvent", String.class, Bundle.class);
      logEventMethod.invoke(firebaseAnalyticsInstance, name, parameters);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }
}
