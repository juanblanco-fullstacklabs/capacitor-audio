
  Pod::Spec.new do |s|
    s.name = 'JuanblancoFullstacklabsCapacitorAudio'
    s.version = '0.0.1'
    s.summary = 'Audio plugin for capacitor enable background playing and control center integration'
    s.license = 'MIT'
    s.homepage = 'github.com/juanblanco-fullstacklabs/capacitor-audio'
    s.author = 'juanblanco-fullstacklabs'
    s.source = { :git => 'github.com/juanblanco-fullstacklabs/capacitor-audio', :tag => s.version.to_s }
    s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target  = '12.0'
    s.dependency 'Capacitor'
  end
