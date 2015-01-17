@Grapes([
    @Grab("org.gebish:geb-core:0.10.0"),
    @Grab("org.seleniumhq.selenium:selenium-firefox-driver:2.43.1"),
    //@Grab("org.seleniumhq.selenium:selenium-chrome-driver:2.43.1"),
    //@Grab("com.github.detro:phantomjsdriver:1.2.0"),
    @Grab("org.seleniumhq.selenium:selenium-support:2.43.1")
])
import geb.*
import net.sourceforge.peers.media.MediaMode
import net.sourceforge.peers.sip.syntaxencoding.SipURI

class LoginModule extends Module {
    static content = {
        username { $("input", type: "text") }
        password { $("input", type: "password") }
 
        button(to: MeetingRoomPage) { 
            $("button", type: "submit")
        }
    }
}

class LoginPage extends Page {
    static url
    static at = { $("input", type:"password").size() == 1 }
    static content = {
        login { module LoginModule }
    }
}

class MeetingRoomPage extends Page {
    static at = { $("bt-meeting-room").size() == 1 }
    static content = {
        hasListeners { $("td", translate:"PARTICIPANTS.INFO.NO_PARTICIPANTS").size() == 0 }
    }
}

class NoOpSoundManager extends net.sourceforge.peers.media.AbstractSoundManager {
    public void init() {}
    public void close() {}
    public byte[] readData() { return null; }
    public int writeData(byte[] buffer, int offset, int length){}
}

class SipConfig implements net.sourceforge.peers.Config {
    private InetAddress publicIpAddress;
    private String user, pass, host

    @Override public InetAddress getLocalInetAddress() {return InetAddress.getByName("192.168.50.1");}
    @Override public InetAddress getPublicInetAddress() { return publicIpAddress; }
    @Override public String getUserPart() { return user; }
    @Override public String getDomain() { return host; }
    @Override public String getPassword() { return pass; }
    @Override  // use microphone and speakers to capture and playback sound
    public MediaMode getMediaMode() { return MediaMode.none; }

    @Override
    public void setPublicInetAddress(InetAddress inetAddress) {
        publicIpAddress = inetAddress;
    }
            
    @Override public SipURI getOutboundProxy() { return null; }
    @Override public int getSipPort() { return 5060; } // use default sip port 5060
    @Override public boolean isMediaDebug() { return false; }
    @Override public String getMediaFile() { return null; }
    @Override public int getRtpPort() { return 0; } // use random rtp port
    
    // in this simple example, we don't need those modifiers, but they are
    // required by the interface
    @Override public void setLocalInetAddress(InetAddress inetAddress) { }
    @Override public void setUserPart(String userPart) { this.user = userPart}
    @Override public void setDomain(String domain) { this.domain = domain}
    @Override public void setPassword(String password) { this.pass = password}
    @Override public void setOutboundProxy(SipURI outboundProxy) { }
    @Override public void setSipPort(int sipPort) { }
    @Override public void setMediaMode(MediaMode mediaMode) { }
    @Override public void setMediaDebug(boolean mediaDebug) { }
    @Override public void setMediaFile(String mediaFile) { }
    @Override public void setRtpPort(int rtpPort) { }
    @Override public void save() { }
}

def cli = new CliBuilder(usage:'blacktiger-test [options]', header:'Options:')
cli.wh(longOpt:'webhost', args:1, argName:'url', 'Host of blacktiger website.', required:true)
cli.wu(longOpt:'webuser', args:1, argName:'username', 'Username for logging into blacktiger', required:true)
cli.wp(longOpt:'webpass', args:1, argName:'password', 'Password for logging into blacktiger', required:true)
cli.sh(longOpt:'siphost', args:1, argName:'url', 'Host of SIP server.', required:true)
cli.su(longOpt:'sipuser', args:1, argName:'username', 'Username for logging into sipphone', required:true)
cli.sp(longOpt:'sippass', args:1, argName:'password', 'Password for logging into sipphone', required:true)
cli.sn(longOpt:'sipnumber', args:1, argName:'number', 'Number to call via sip', required:true)
def options = cli.parse(args)

if(options == null) {
    System.exit(0)
}

def config = new SipConfig(host: options.sh, user:options.su, pass:options.sp)
def userAgent = new net.sourceforge.peers.sip.core.useragent.UserAgent(null, config, null, new NoOpSoundManager());
def sipRequest = null

println "Registering"
userAgent.register();
Thread.sleep(3000);

Browser.drive {
    //LoginPage.url = options.w
    go options.wh
 
    // make sure we actually got to the page
    waitFor { at LoginPage }
    login.username.value(options.wu)
    login.password.value(options.wp)
    login.button.click();
    
    waitFor { at MeetingRoomPage }
 
    assert !hasListeners
    
    println "Calling"
    sipRequest = userAgent.invite("sip:${options.sn}@${options.sh}", null);
    Thread.sleep(3000);
    
    assert hasListeners
 /*
    def firstLink = $("li.g", 0).find("a.l")
    assert firstLink.text() == "Wikipedia"
 
    firstLink.click()
 
    waitFor { title == "Wikipedia" }*/
}



// Peers has a hard time terminating - Lets give a few seconds an then force quit
def th = Thread.start {
    userAgent.terminate(sipRequest)
    userAgent.unregister();
}


println "Shutting down"
Thread.sleep(5000);
System.exit(0)