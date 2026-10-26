package app.aavideoplayercar.podcast;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.*;
import app.aavideoplayercar.MainActivity;
import app.aavideoplayercar.R;
import java.util.List;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PodcastDownloadService extends DownloadService {
    private static final String CHANNEL = "podcast-downloads";
    public PodcastDownloadService() { super(2001, 1000, CHANNEL, R.string.podcast_downloads, 0); }
    @Override protected DownloadManager getDownloadManager() { return PodcastDownloads.get(this).manager(); }
    @Override protected Scheduler getScheduler() { return new PlatformScheduler(this, 2002); }
    @Override protected Notification getForegroundNotification(List<Download> downloads, int requirements) {
        PendingIntent open = PendingIntent.getActivity(this, 2001, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new DownloadNotificationHelper(this, CHANNEL).buildProgressNotification(this,
            android.R.drawable.stat_sys_download, open, getString(R.string.podcast_downloads), downloads, requirements);
    }
}
