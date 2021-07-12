import { PluginListenerHandle } from "@capacitor/core";

type NoArgEvents =
  | "playEnd"
  | "playAllEnd"
  | "playPaused"
  | "playResumed"
  | "playNext"
  | "playPrevious";

type EventNameDataMap = {
  playTimeUpdate: { currentTime: number; duration: number; isLive: boolean };
} & Record<NoArgEvents, {}>;

export interface AudioPluginPlugin {
  playList(options: { items: PlaylistItem[] }): void;
  setPlaying(info: NowPlayingInfo): void;
  pausePlay(): void;
  resumePlay(): void;
  seek(options?: { to: number }): Promise<void>;

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
  isLive?: boolean;
}
