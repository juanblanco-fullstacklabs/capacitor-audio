export interface AudioPluginPlugin {
  playList(options: { items: PlaylistItem[] }): void;
  play(): void;
  pausePlay(): void;
  resumePlay(): void;
  setPlaying(info: NowPlayingInfo): void;
}

export interface PlaylistItem {
  src: string;
}

export interface NowPlayingInfo {
  title: string;
  artist: string;
}
