package app.aavideoplayercar.movies;
import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import androidx.media3.ui.PlayerView;
import app.aavideoplayercar.R;
import app.aavideoplayercar.playback.PlaybackService;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class MoviePlayerDialog {
    private final AlertDialog dialog;
    private final String mediaId;
    private final TextView status;
    private final Button select;
    private final PlayerView view;
    private MediaController controller;
    private boolean closed, desiredPlay;
    public MoviePlayerDialog(Context context,String mediaId,Runnable dismissed) { this(context,mediaId,null,dismissed); }
    public MoviePlayerDialog(Context context,String mediaId,Bundle restore,Runnable dismissed) {
        this.mediaId=mediaId; desiredPlay=restore==null || restore.getBoolean("playing",true);
        LinearLayout content=new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(context); status.setTag("movies.player.status"); status.setText(R.string.movies_connecting); content.addView(status);
        select=new Button(context); select.setText(R.string.movies_play_selected); select.setAllCaps(false); select.setVisibility(View.GONE); content.addView(select);
        select.setOnClickListener(v->startSelected(true));
        view=new PlayerView(context); view.setTag("movies.player"); view.setControllerShowTimeoutMs(0); content.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        Context app=context.getApplicationContext();
        var future=new MediaController.Builder(app,new SessionToken(app,new ComponentName(app,PlaybackService.class))).buildAsync();
        dialog=new AlertDialog.Builder(context).setTitle(R.string.movies_playback).setView(content).setPositiveButton(R.string.news_close,null).create();
        dialog.setOnDismissListener(d->{ closed=true; view.setPlayer(null); MediaController.releaseFuture(future); controller=null; dismissed.run(); });
        future.addListener(()->{ if(closed)return; try {
            controller=future.get();
            controller.addListener(new Player.Listener(){ @Override public void onEvents(Player p,Player.Events events) { if(!closed) sync(); } });
            MediaItem current=controller.getCurrentMediaItem();
            if(restore==null || current==null) startSelected(desiredPlay); else sync();
        } catch(Exception error){status.setText(R.string.movies_play_error);} },new android.os.Handler(android.os.Looper.getMainLooper())::post);
        dialog.show(); dialog.getWindow().setLayout(-1,-1);
    }
    private void startSelected(boolean play) {
        if(closed || controller==null)return;
        try { controller.setMediaItem(new MediaItem.Builder().setMediaId(mediaId).build()); controller.prepare(); if(play) controller.play(); sync(); }
        catch(Exception error){status.setText(R.string.movies_play_error);}
    }
    private void sync() {
        if(controller==null)return;
        MediaItem current=controller.getCurrentMediaItem();
        if(current==null || !mediaId.equals(current.mediaId)) {
            view.setPlayer(null); select.setVisibility(View.VISIBLE); status.setText(R.string.movies_other_playback); return;
        }
        if(view.getPlayer()!=controller)view.setPlayer(controller);
        select.setVisibility(View.GONE);
        status.setText(controller.getPlayerError()!=null ? R.string.movies_play_error : controller.isPlaying() ? R.string.movies_playing : controller.getPlaybackState()==Player.STATE_BUFFERING ? R.string.movies_connecting : R.string.movies_paused);
    }
    public Bundle saveState() {
        Bundle state=new Bundle(); state.putString("mediaId",mediaId);
        MediaItem current=controller==null ? null : controller.getCurrentMediaItem();
        state.putBoolean("playing",current!=null && mediaId.equals(current.mediaId) ? controller.getPlayWhenReady() : desiredPlay);
        return state;
    }
    public void close(){dialog.dismiss();}
}
