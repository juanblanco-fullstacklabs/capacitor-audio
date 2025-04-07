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
            if let currentItem = player.currentItem {
                var isSeekable = false
                let currentTime = CMTimeGetSeconds(currentItem.currentTime())
                let isLive = CMTIME_IS_INDEFINITE(currentItem.duration)
                var seekableRangeStart: Float64 = 0.0
                var seekableRangeDuration: Float64 = 0.0
                
                if let seekableRange = player.currentItem?.seekableTimeRanges.first?.timeRangeValue {                  
                    seekableRangeStart = CMTimeGetSeconds(seekableRange.start)
                    seekableRangeDuration = CMTimeGetSeconds(seekableRange.duration)
                    isSeekable = seekableRangeDuration > 0
                }
                
                let duration = currentItem.duration.seconds > 0.0 ? CMTimeGetSeconds(currentItem.duration) : 0.0
                
                plugin.notifyListeners("playTimeUpdate", data: [
                    "currentTime": currentTime,
                    "duration": duration,
                    "isLive": isLive,
                    "isSeekable": isSeekable,
                    "seekableRangeStart": seekableRangeStart,
                    "seekableRangeDuration": seekableRangeDuration,
                    "dvrDuration": seekableRangeDuration,
                ])
                
                var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo

                if (nowPlayingInfo != nil) {
                    nowPlayingInfo![MPMediaItemPropertyPlaybackDuration] = duration
                    nowPlayingInfo![MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
                    nowPlayingInfo![MPNowPlayingInfoPropertyIsLiveStream] = isLive
                    MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
                }
            }
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
            let remoteCommandCenter = MPRemoteCommandCenter.shared()
            
            remoteCommandCenter.pauseCommand.addTarget(handler: {e in
                self.notifyListeners("playPaused", data: [:])
                self.audioPlayer?.pause()
                return .success
            })
            
            remoteCommandCenter.nextTrackCommand.addTarget(handler: {e in
                self.notifyListeners("playNext", data: [:])
                self.audioPlayer?.pause()
                return .success
            })
            
            remoteCommandCenter.previousTrackCommand.addTarget(handler: {e in
                self.notifyListeners("playPrevious", data: [:])
                self.audioPlayer?.pause()
                return .success
            })
            
            remoteCommandCenter.playCommand.addTarget(handler: {e in
                self.notifyListeners("playResumed", data: [:])
                self.audioPlayer?.play()
                return .success
            })
            
            remoteCommandCenter.skipForwardCommand.preferredIntervals = [15, 30, 45]
            remoteCommandCenter.skipForwardCommand.addTarget(handler: {e in
                guard let skipEvent = e as? MPSkipIntervalCommandEvent else {
                    return .commandFailed
                }
                self.notifyListeners("skipForward", data: ["interval": skipEvent.interval])
                self.seek(time: self.currentTime() + skipEvent.interval)
                return .success
            })
            
            remoteCommandCenter.skipBackwardCommand.preferredIntervals = [15, 30, 45]
            remoteCommandCenter.skipBackwardCommand.addTarget(handler: {e in
                guard let skipEvent = e as? MPSkipIntervalCommandEvent else {
                    return .commandFailed
                }
                self.notifyListeners("skipBackward", data: ["interval": skipEvent.interval])
                self.seek(time: self.currentTime() - skipEvent.interval)
                return .success
            })
            
            remoteCommandCenter.changePlaybackPositionCommand.isEnabled = true
            remoteCommandCenter.changePlaybackPositionCommand.addTarget(handler: {e in
                if let remoteEvent = e as? MPChangePlaybackPositionCommandEvent {
                    self.audioPlayer?.seek(to: CMTime(seconds: remoteEvent.positionTime, preferredTimescale: CMTimeScale(NSEC_PER_SEC)));
                }
                return .success
            })
            
            let notify = NotificationCenter.default
            notify.addObserver(self, selector: #selector(self.onPlayEnd), name: NSNotification.Name.AVPlayerItemDidPlayToEndTime, object: nil)
            notify.addObserver(self, selector: #selector(self.handleInterruption), name: AVAudioSession.interruptionNotification, object: nil)
            notify.addObserver(self, selector: #selector(self.handleRouteChange), name: AVAudioSession.routeChangeNotification, object: nil)
        }
        self.isInited = true
    }
    
    @objc func handleRouteChange(notification:Notification) {
        NSLog("handleRouteChange")
        guard  let userInfo = notification.userInfo,
               let typeValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
               let type = AVAudioSession.RouteChangeReason(rawValue: typeValue) else {
                   return
               }
        switch type {
        case .categoryChange:
            NSLog("handleRouteChange categoryChange")
        
        case .newDeviceAvailable:
            NSLog("handleRouteChange newDeviceAvailable")
        
        case .noSuitableRouteForCategory:
            NSLog("handleRouteChange noSuitableRouteForCategory")
        
        case .oldDeviceUnavailable:
            NSLog("handleRouteChange oldDeviceUnavailable")
        
        case .override:
            NSLog("handleRouteChange override")
        
        case .routeConfigurationChange:
            NSLog("handleRouteChange routeConfigurationChange")
        
        case .unknown:
            NSLog("handleRouteChange unknown")
        
        case .wakeFromSleep:
            NSLog("handleRouteChange wakeFromSleep")
        default:
            NSLog("handleRouteChange default")
        }
    }
    
    @objc func handleInterruption(notification: Notification) {
        NSLog("handleInterruption")
        guard  let userInfo = notification.userInfo,
               let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
               let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
                   return
               }
        
        switch type {
            
        case .began:
            NSLog("handleInterruption.begin")
            self.notifyListeners("playPaused", data: [:])
            
        case .ended:
            NSLog("handleInterruption.ended")
            
            guard let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt else { return }
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            if options.contains(.shouldResume) {
                NSLog("handleInterruption.ended shouldResume")
                self.audioPlayer?.play()
                self.notifyListeners("playResumed", data: [:])
            } else {
                NSLog("handleInterruption.ended !shouldResume")
            }
            
        default: ()
        }
    }
    
    @objc func setPlaying(_ call: CAPPluginCall) {
        let title = call.getString("title")
        let artist = call.getString("artist")
        let artwork = call.getString("artwork")
        let remoteCommands = call.getArray("remoteCommands", String.self) ?? ["pause","play","skipForward","skipBackward"]
        
        var nowPlayingInfo = [String: Any] ()
        
        nowPlayingInfo[MPMediaItemPropertyTitle] = title
        nowPlayingInfo[MPMediaItemPropertyArtist] = artist
        
        if (artwork != nil) {
            let artworkUrl = URL(string: artwork!)!
            URLSession.shared.dataTask(with: artworkUrl, completionHandler: {(data, response, error) in
                if let imageData = data, let image = UIImage(data: imageData) {
                    let artwork = MPMediaItemArtwork(boundsSize: image.size, requestHandler: { _ -> UIImage in
                        return image
                    })
                    nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
                    MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
                }
            }).resume()
        }
        
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
        
        let remoteCommandCenter = MPRemoteCommandCenter.shared()
        remoteCommandCenter.pauseCommand.isEnabled = remoteCommands.contains("pause")
        remoteCommandCenter.nextTrackCommand.isEnabled = remoteCommands.contains("nextTrack")
        remoteCommandCenter.previousTrackCommand.isEnabled = remoteCommands.contains("previousTrack")
        remoteCommandCenter.playCommand.isEnabled = remoteCommands.contains("play")
        remoteCommandCenter.skipForwardCommand.isEnabled = remoteCommands.contains("skipForward")
        remoteCommandCenter.skipBackwardCommand.isEnabled = remoteCommands.contains("skipBackward")
        
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
            self.seek(time: newTimeOrNil!)
        }
        call.resolve()
    }
    
    func seek(time:Double) {
        audioPlayer!.seek(to: CMTime(seconds: time, preferredTimescale: CMTimeScale(NSEC_PER_SEC)))
    }
    
    func currentTime() -> Double {
        return CMTimeGetSeconds(self.audioPlayer!.player.currentTime())
    }
    
    @objc func setSearchUrl(_ call: CAPPluginCall) {
        // not implemented
        call.resolve()
    }
}
