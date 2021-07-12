import Foundation
import Capacitor
import AVFoundation
import MediaPlayer

class Player: NSObject {
    var playItems: [AVPlayerItem]
    var player: AVQueuePlayer
    var periodicTimeObserverHandle: Any
    
    init(items: [AVPlayerItem], plugin: CAPPlugin) {
        self.playItems = items
        let player = AVQueuePlayer(items: items)
        self.player = player
        periodicTimeObserverHandle = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC)), queue: .main) { _ in
            plugin.notifyListeners("playTimeUpdate", data: ["currentTime": CMTimeGetSeconds(player.currentItem!.currentTime()), "duration": CMTimeGetSeconds(player.currentItem!.duration) ])
        }
    }
    func play() {
        self.player.play()
    }
    func pause() {
        self.player.pause()
    }
    func toEnd() -> Bool {
        return self.player.currentItem == playItems.last
    }
    func seek(to: CMTime) {
        self.player.seek(to: to)
    }
    func destroy(){
        self.player.removeTimeObserver(self.periodicTimeObserverHandle)
        self.player.removeAllItems()
    }
}

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(AudioPlugin)
public class AudioPlugin: CAPPlugin {
    
    var isInited = false
    
    @objc func onPlayEnd(){
        self.notifyListeners("playEnd", data: [:])
        if (self.audioPlayer?.toEnd() ?? false) {
            self.notifyListeners("playAllEnd", data: [:])
        }
    }
    
    func initAudio() {
        if (self.isInited) {
            return
        }
        let session = AVAudioSession.sharedInstance()
        do{
            try session.setCategory(AVAudioSession.Category.playback)
            try session.setActive(true)
        }catch {
            print(error)
            return
        }
        DispatchQueue.main.sync {
            let command = MPRemoteCommandCenter.shared()
            command.pauseCommand.isEnabled = true
            command.pauseCommand.addTarget(handler: {e in self.notifyListeners("playPaused", data: [:]); self.audioPlayer?.pause(); return MPRemoteCommandHandlerStatus.success })
            
            command.nextTrackCommand.isEnabled = true
            command.nextTrackCommand.addTarget(handler: {e in self.notifyListeners("playNext", data: [:]); self.audioPlayer?.pause(); return MPRemoteCommandHandlerStatus.success})
            
            command.previousTrackCommand.isEnabled = true
            command.previousTrackCommand.addTarget(handler: {e in self.notifyListeners("playPrevious", data: [:]); self.audioPlayer?.pause(); return MPRemoteCommandHandlerStatus.success})
            
            command.playCommand.isEnabled = true
            command.playCommand.addTarget(handler: {e in self.notifyListeners("playResumed", data: [:]); self.audioPlayer?.play(); return MPRemoteCommandHandlerStatus.success})
            
            let nofity = NotificationCenter.default
            nofity.addObserver(self, selector: #selector(self.onPlayEnd), name: NSNotification.Name.AVPlayerItemDidPlayToEndTime, object: nil)
        }
        self.isInited = true
    }
    
    @objc func setPlaying(_ call: CAPPluginCall) {
        let title = call.getString("title")
        let artist = call.getString("artist")
        let artwork = call.getString("artwork")
        
        var nowPlayingInfo = [String: Any] ()
        
        nowPlayingInfo[MPMediaItemPropertyTitle] = title
        nowPlayingInfo[MPMediaItemPropertyArtist] = artist
        
        if (artwork != nil) {
            let artworkUrl = URL(string: artwork!)!
            URLSession.shared.dataTask(with: artworkUrl, completionHandler: {(data, response, error) in
                let image =  UIImage(data: data!)!
                let artwork = MPMediaItemArtwork(boundsSize: image.size, requestHandler: { _ -> UIImage in
                    return image
                })
                nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
                MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
            }).resume()
        }
        
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
        
        call.resolve(["status": "ok"])
    }
    
    var audioPlayer: Player?
    @objc func playList(_ call: CAPPluginCall) {
        let audios = call.getArray("items", [String: String].self)
        if (audios == nil) {
            call.reject("Must provide items")
        }
        let urls = audios!.map({item in URL(string: item["src"]!)})
        let urls_ = urls.filter({u in u != nil}).map({u in u!})
        let items = urls_.map({u in AVPlayerItem(url: u)})
        self.initAudio()
        if (self.audioPlayer != nil) {
            self.audioPlayer!.destroy();
        }
        self.audioPlayer = Player(items: items, plugin: self)
        self.audioPlayer?.play()
    }
    
    @objc func pausePlay(_ call: CAPPluginCall) {
        self.audioPlayer?.pause()
    }
    @objc func resumePlay(_ call: CAPPluginCall) {
        self.audioPlayer?.play()
    }
    @objc func seek(_ call: CAPPluginCall) {
        let newTimeOrNil = call.getDouble("to")
        if (newTimeOrNil != nil) {
            audioPlayer!.seek(to: CMTime(seconds: newTimeOrNil!, preferredTimescale: CMTimeScale(NSEC_PER_SEC)))
        }
        call.resolve()
    }
}
