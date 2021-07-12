import { PluginListenerHandle } from "@capacitor/core";

type NoArgEvents =
  | "playEnd"
  | "playAllEnd"
  | "playPaused"
  | "playNext"
  | "playPrevious"
  | "play";

type EventNameDataMap = {
  playTimeUpdate: { currentTime: number; duration: number };
} & Record<NoArgEvents, {}>;

export interface AudioPluginPlugin {
  playList(options: { items: PlaylistItem[] }): void;
  setPlaying(info: NowPlayingInfo): void;
  pausePlay(): void;
  resumePlay(): void;

  addListener<EventName extends keyof EventNameDataMap>(
    eventName: EventName,
    listener: (data: EventNameDataMap[EventName]) => void
  ): PluginListenerHandle;
}

export interface PlaylistItem {
  src: string;
}

export interface NowPlayingInfo {
  title: string;
  artist: string;
  artwork?: string;
}
