import { registerPlugin } from "@capacitor/core";
import type { AudioPluginPlugin } from "./definitions";

const AudioPlugin = registerPlugin<AudioPluginPlugin>("Audio", {
  web: () => import("./web").then((m) => new m.AudioPluginWeb()),
});

export * from "./definitions";
export { AudioPlugin };
