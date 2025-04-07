import { WebPlugin } from "@capacitor/core";
import videojs, { VideoJsPlayer } from "video.js";
import type {
  AudioPluginPlugin,
  NowPlayingInfo,
  PlaylistItem,
} from "./definitions";

export class AudioPluginWeb extends WebPlugin implements AudioPluginPlugin {
  current?: VideoJsPlayer;
  currentIndex = 0;
  audios?: VideoJsPlayer[];
  info?: NowPlayingInfo;

  playList({ items }: { items: PlaylistItem[] }) {
    if (this.audios != null) {
      this.audios.forEach((audio) => audio.dispose());
    }
    this.audios = items.map((item) => {
      let audio = videojs(new Audio());
      audio.src(item);
      audio.load();
      return audio;
    });
    this.current = this.audios[0];
    this.currentIndex = 0;
    this.play();
  }

  play() {
    if (this.current == null) {
      throw new Error("no current item to play");
    }
    const audios = this.audios;
    if (audios == null) {
      throw new Error("no playlist");
    }
    this.current.on("ended", () => {
      this.notifyListeners("playEnd", {});
      if (this.current === audios[audios.length - 1]) {
        this.notifyListeners("playAllEnd", {});
      } else {
        this.currentIndex += 1;
        this.current = audios[this.currentIndex];
        this.play();
      }
    });
    this.current.on("pause", () => {
      this.notifyListeners("playPaused", {});
    });
    this.current.on("playing", () => {
      this.notifyListeners("playResumed", {});
    });
    this.current.on("timeupdate", () => {
      this.notifyListeners("playTimeUpdate", {
        currentTime: this.current?.currentTime(),
        duration: this.current?.duration(),
        isLive: this.current?.liveTracker.isLive(),
        isSeekable: true,
      });
    });
    this.current && this.current.play();
  }

  pausePlay() {
    this.current && this.current.pause();
  }

  resumePlay() {
    this.play();
  }

  setPlaying(info: NowPlayingInfo) {
    this.info = info;
    return new Promise<void>((r) => r());
  }

  async setSearchUrl() {
    // not implemented
  }

  async seek(options: { to: number }): Promise<void> {
    if (this.current) {
      if (options != null) {
        this.current.currentTime(options.to);
      }
    }
  }
}
