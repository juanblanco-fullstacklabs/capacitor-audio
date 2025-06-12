import { PluginListenerHandle } from "@capacitor/core";

type NoArgEvents =
  | "playEnd"
  | "playAllEnd"
  | "playPaused"
  | "playResumed"
  | "playNext"
  | "playPrevious";

type SkipCommands = "skipForward" | "skipBackward";

type EventNameDataMap = Record<
  "playTimeUpdate",
  { currentTime: number; duration: number; isLive: boolean, isSeekable: boolean; seekableRangeStart: number; seekableRangeDuration: number; dvrDuration: number; }
> &
  Record<NoArgEvents, {}> &
  Record<SkipCommands, { interval: number }>;

export interface AudioPluginPlugin {
  playList(options: { items: PlaylistItem[] }): void;
  setPlaying(info: NowPlayingInfo): void;
  pausePlay(): void;
  resumePlay(): void;
  seek(options?: { to: number }): Promise<void>;
  setSearchUrl(options: { url: string }): Promise<void>;

  addListener<EventName extends keyof EventNameDataMap>(
    eventName: EventName,
    listenerFunc: (data: EventNameDataMap[EventName]) => void
  ): Promise<PluginListenerHandle>;
}

export interface PlaylistItem {
  src: string;
  type?: string;
  title?: string;
  artist?: string;
  album?: string;
  artwork?: string;
}

export interface NowPlayingInfo {
  title: string;
  artist: string;
  artwork?: string;
  remoteCommands?: RemoteCommand[];
}

export type RemoteCommand =
  | "pause"
  | "nextTrack"
  | "previousTrack"
  | "play"
  | "skipForward"
  | "skipBackward";
