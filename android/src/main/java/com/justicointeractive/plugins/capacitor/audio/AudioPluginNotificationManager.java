package com.justicointeractive.plugins.capacitor.audio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class AudioPluginNotificationManager {

  private final String CHANNEL_ID = "audio_notification";

  PlayerNotificationManager notificationManager;
  Map<String, Object> currentItem = new HashMap<>();

  AudioPluginNotificationManager(Context context, SimpleExoPlayer player) {

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID,
        "Media Controller",
        NotificationManager.IMPORTANCE_LOW
      );
      channel.setDescription("A notification that allows you to control media playback");

      NotificationManager systemNotificationManager = context.getSystemService(NotificationManager.class);
      if (systemNotificationManager != null){
        systemNotificationManager.createNotificationChannel(channel);
      }
    }

    notificationManager = new PlayerNotificationManager.Builder(
      context,
      1,
      CHANNEL_ID,
      new PlayerNotificationManager.MediaDescriptionAdapter() {
        @Override
        public CharSequence getCurrentContentTitle(Player player) {
          return (String)currentItem.get("title");
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
          return null;
        }

        @Nullable
        @Override
        public CharSequence getCurrentSubText(Player player) {
          return null;
        }

        @Nullable
        @Override
        public CharSequence getCurrentContentText(Player player) {
          return (String)currentItem.get("artist");
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
          return (Bitmap)currentItem.get("artwork");
        }
      }
    )
      .setSmallIconResourceId(
        context.getResources()
          .getIdentifier("ic_launcher", "mipmap", context.getPackageName()))
      .build();

    notificationManager.setPlayer(player);
    MediaSessionCompat mediaSessionCompat = new MediaSessionCompat( context, "tag");
    notificationManager.setMediaSessionToken(mediaSessionCompat.getSessionToken());
  }

  public void setCurrentItem(String title, String artist, String artwork) {
    currentItem.put("title", title);
    currentItem.put("artist", artist);

    if (artwork != null) {
      Bitmap artworkBitmap = null;
      try {
        artworkBitmap = BitmapFactory.decodeStream(new URL(artwork).openStream());
      } catch (IOException e) {
        e.printStackTrace();
      }
      currentItem.put("artwork", artworkBitmap);
    }

    notificationManager.invalidate();
  }
}
