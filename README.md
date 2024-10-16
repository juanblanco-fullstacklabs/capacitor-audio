# Capacitor Audio

## Introduction

This is a simple but powerful Plugin for develop radio like Apps using Capacitor.

[GitHub Repo](https://github.com/reynoldfullstacklabs/capacitor-audio)

## APIs

```javascript
// Play a list of audio source
AudioPlugin.playList({
  items: [
    {
      src: "...",
    },
    {
      src: "...",
    },
  ],
});

// Set Playing Info in Control center
AudioPlugin.setPlaying({
  title: "My Radio",
  artist: "Ma Tao",
  artwork: "https://example.s3.amazonaws.com/example.jpg",
  remoteCommands: [
    "pause",
    "nextTrack",
    "previousTrack",
    "play",
    "skipForward",
    "skipBackward",
  ],
});

// pause playing
AudioPlugin.pausePlay();

//resume playing
AudioPlugin.resumePlay();
```

Events

```javascript
// trigger when one item of playlist play to endtime
AudioPlugin.addListener("playEnd", () => {
  console.log("PlayEnd");
});

// trigger when one playlist all endtime
AudioPlugin.addListener("playAllEnd", () => {});
// trigger when user request next in control center
AudioPlugin.addListener("playNext", () => {});

// trigger when user request prevous in control center
AudioPlugin.addListener("playPrevious", () => {});

// trigger when play paused (from control center)
AudioPlugin.addListener("playPaused", () => {});

// trigger when play resumed (from control center)
AudioPlugin.addListener("playResumed", () => {});
```
