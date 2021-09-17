package com.justicointeractive.plugins.capacitor.audio;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

public class AudioPluginNotificationManager {

  AudioPluginService service;

  private final String CHANNEL_ID = "audio_notification";

  PlayerNotificationManager notificationManager;

  AudioPluginNotificationManager(Context context, SimpleExoPlayer player, AudioPluginService service, PlayerNotificationManager.NotificationListener playerNotificationListener) {
    this.service = service;

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

    // get icon if exists com.google.firebase.messaging.default_notification_icon
    ApplicationInfo applicationInfo = null;
    int icon =
      context.getResources()
        .getIdentifier(
          "ic_launcher",
          "mipmap",
          context.getPackageName());
    try {
      applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      icon = applicationInfo.metaData.getInt(
        "com.google.firebase.messaging.default_notification_icon",
        applicationInfo.metaData.getInt(
          "com.justicointeractive.plugins.capacitor.audio.notification_icon",
          icon));
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }

    notificationManager = new PlayerNotificationManager.Builder(
      context,
      1,
      CHANNEL_ID,
      new PlayerNotificationManager.MediaDescriptionAdapter() {
        @Override
        public CharSequence getCurrentContentTitle(Player player) {
          return (String)service.currentItem.get("title");
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
          return (String)service.currentItem.get("artist");
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
          return (Bitmap)service.currentItem.get("artwork");
        }
      }
    )
      .setSmallIconResourceId(icon)
      .setNotificationListener(playerNotificationListener)
      .build();

    notificationManager.setPlayer(player);
    notificationManager.setMediaSessionToken(service.mediaSession.getSessionToken());
  }
}
