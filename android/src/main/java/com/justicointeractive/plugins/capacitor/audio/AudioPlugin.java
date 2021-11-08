package com.justicointeractive.plugins.capacitor.audio;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

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
    @Permission(
      strings = { Manifest.permission.FOREGROUND_SERVICE },
      alias = "foregroundserivice"
    ),
  }
)
public class AudioPlugin extends Plugin {

  private AudioPluginService service;

  public void load() {

    Intent serviceIntent = new Intent(this.getContext(), AudioPluginService.class);
    this.getContext().bindService(serviceIntent, new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        AudioPluginService.AudioPluginServiceBinder binder =
          (AudioPluginService.AudioPluginServiceBinder)iBinder;
        binder.setPluginInstance(AudioPlugin.this);
        service = binder.getService();
      }

      @Override
      public void onServiceDisconnected(ComponentName componentName) {
        service = null;
      }
    }, Context.BIND_AUTO_CREATE);

  }

  @Override
  public void notifyListeners(String eventName, JSObject data) {
    super.notifyListeners(eventName, data);
  }

  @PluginMethod()
  public void playList(PluginCall call) {
    getBridge().executeOnMainThread(() -> {
      service.playList(call);
   });
  }

  @PluginMethod()
  public void setPlaying(PluginCall call) {
    service.setPlaying(call);
  }

  @PluginMethod()
  public void pausePlay(PluginCall call) {
    getBridge().executeOnMainThread(() -> {
      service.pausePlay(call);
    });
  }

  @PluginMethod()
  public void resumePlay(PluginCall call) {
    getBridge().executeOnMainThread(() -> {
      service.resumePlay(call);
    });
  }

  @PluginMethod()
  public void seek(PluginCall call) {
    getBridge().executeOnMainThread(() -> {
      service.seek(call);
    });
  }

  @PluginMethod
  public void setSearchUrl(PluginCall call) {
    String url = call.getString("url");

    SharedPreferences sharedPreferences = this.getContext().getSharedPreferences("audioPlugin", Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putString("audioSearchUrl", url);
    editor.apply();
    call.resolve();
  }
}
