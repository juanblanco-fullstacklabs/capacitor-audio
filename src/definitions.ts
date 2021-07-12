export interface AudioPluginPlugin {
  playList(options: { items: PlaylistItem[] }): void;
  setPlaying(info: NowPlayingInfo): void;
  pausePlay(): void;
  resumePlay(): void;
}

export interface PlaylistItem {
  src: string;
}

export interface NowPlayingInfo {
  title: string;
  artist: string;
}
