package app.aavideoplayercar.movies;
import app.aavideoplayercar.data.*;
import app.aavideoplayercar.iptv.AccountSecrets;
import app.aavideoplayercar.network.HttpDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
public final class MoviePlayback {
    private MoviePlayback() {}
    public static String identity(String addon,String type,String videoId) {
        return UUID.nameUUIDFromBytes((addon+"\n"+type+"\n"+videoId).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public static MovieVideo prepare(AppDatabase db,String addon,String type,String videoId,String title,AddonClient.Stream stream) throws IOException {
        return prepare(db,addon,type,videoId,title,stream,-1);
    }
    public static MovieVideo prepare(AppDatabase db,String addon,String type,String videoId,String title,AddonClient.Stream stream,int streamIndex) throws IOException {
        if(stream.kind!=AddonClient.StreamKind.HTTP || db.movieAddons().get(addon)==null || !java.util.Set.of("movie","series").contains(type) || videoId.isEmpty() || videoId.length()>500) throw new IOException("Video unavailable");
        MovieVideo video=new MovieVideo(); video.id=identity(addon,type,videoId); video.addonId=addon; video.title=title;
        video.source=AccountSecrets.encrypt("movie-stream-v1:"+video.id,HttpDocument.url(stream.url)); video.providerType=type; video.providerVideoId=videoId;
        video.streamFingerprint=fingerprint(stream); video.streamIndex=streamIndex; db.movieVideos().save(video); return video;
    }
    public static String resolve(MovieVideo video) throws IOException { return HttpDocument.url(AccountSecrets.decrypt("movie-stream-v1:"+video.id,video.source)); }
    private static String fingerprint(AddonClient.Stream stream) throws IOException {
        try { byte[] digest=MessageDigest.getInstance("SHA-256").digest((stream.name.length()+":"+stream.name+stream.description.length()+":"+stream.description).getBytes(StandardCharsets.UTF_8));
            char[] hex="0123456789abcdef".toCharArray(); StringBuilder value=new StringBuilder(64); for(byte b:digest)value.append(hex[(b>>4)&15]).append(hex[b&15]); return value.toString();
        } catch(Exception error) { throw new IOException("Stream unavailable"); }
    }
    /** Re-discover only the same named source. Ambiguous changes require explicit user selection. */
    public static AddonClient.Stream renew(MovieVideo video, MovieAddon addon, HttpDocument client) throws IOException {
        if(video.providerType.isEmpty() || video.providerVideoId.isEmpty() || video.streamFingerprint.isEmpty() || !video.addonId.equals(addon.id)) throw new IOException("Source cannot renew");
        var manifest=AddonClient.manifest(client,AddonRegistration.endpoint(addon));
        if(!manifest.id.equals(addon.providerId)) throw new IOException("Provider changed");
        var streams=AddonClient.streams(client,manifest,video.providerType,video.providerVideoId);
        AddonClient.Stream chosen=null; int count=0;
        for(int i=0;i<streams.size();i++) { var candidate=streams.get(i); if(candidate.kind!=AddonClient.StreamKind.HTTP || !fingerprint(candidate).equals(video.streamFingerprint)) continue;
            count++; if(video.streamIndex==i) chosen=candidate; else if(chosen==null) chosen=candidate;
        }
        if(count==0 || (count>1 && (video.streamIndex<0 || video.streamIndex>=streams.size() || streams.get(video.streamIndex).kind!=AddonClient.StreamKind.HTTP || !fingerprint(streams.get(video.streamIndex)).equals(video.streamFingerprint)))) throw new IOException("Source changed; select it again");
        if(chosen==null || resolve(video).equals(chosen.url)) throw new IOException("No new source URL");
        return chosen;
    }
}
