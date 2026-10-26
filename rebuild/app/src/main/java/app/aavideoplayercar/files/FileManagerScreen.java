package app.aavideoplayercar.files;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.text.*;
import android.view.View;
import android.widget.*;
import app.aavideoplayercar.R;
import java.util.*;
import java.util.concurrent.*;

/** User-granted document tree browser; provider operations never run on the main thread. */
public final class FileManagerScreen {
    private static final int PAGE_SIZE=50;
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1));
    private final Context context;
    private final Runnable chooseFolder;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout view;
    private final TextView status;
    private final EditText search;
    private final ListView list;
    private final Button choose,up,refresh,create,paste,cancelPaste,previous,next;
    private final LinearLayout transferActions;
    private final ArrayList<String> ids=new ArrayList<>(), names=new ArrayList<>();
    private List<TreeDocuments.Entry> rows=List.of(), visible=List.of();
    private Uri tree;
    private int page,generation;
    private boolean closed,truncated,loading,mutating;
    private CancellationSignal signal;
    private Future<?> task;
    private AlertDialog actionDialog, editDialog;
    private TreeDocuments.Entry pendingEntry;
    private String pendingParent;
    private boolean pendingMove;

    public FileManagerScreen(Context context, Bundle saved, Runnable chooseFolder) {
        this.context=context; this.chooseFolder=chooseFolder;
        String savedTree=context.getSharedPreferences("file_manager",0).getString("tree",null);
        if(savedTree!=null)try { tree=Uri.parse(savedTree); } catch(Exception ignored) { tree=null; }
        if(tree!=null && hasGrant(false)) {
            String root=DocumentsContract.getTreeDocumentId(tree); ids.add(root); names.add(context.getString(R.string.files_root));
            if(saved!=null) {
                ArrayList<String> oldIds=saved.getStringArrayList("ids"),oldNames=saved.getStringArrayList("names");
                if(oldIds!=null && oldNames!=null && oldIds.size()==oldNames.size() && !oldIds.isEmpty() && oldIds.size()<=100 && root.equals(oldIds.get(0))) {
                    boolean valid=true; for(String id:oldIds)if(id==null || id.isEmpty() || id.length()>2048)valid=false;
                    if(valid) { ids.clear(); ids.addAll(oldIds); names.clear(); names.addAll(oldNames); }
                }
                page=Math.max(0,saved.getInt("page"));
                if(hasGrant(true) && tree.toString().equals(saved.getString("transferTree"))) {
                    String id=saved.getString("transferId"), parent=saved.getString("transferParent");
                    String name=saved.getString("transferName"), mime=saved.getString("transferMime");
                    if(id!=null && !id.isEmpty() && id.length()<=2048 && parent!=null && !parent.isEmpty() && parent.length()<=2048
                        && name!=null && !name.isEmpty() && name.length()<=500 && mime!=null && !mime.isEmpty() && mime.length()<=255) {
                        pendingEntry=new TreeDocuments.Entry(id,name,mime,saved.getLong("transferSize",-1),saved.getInt("transferFlags"),DocumentsContract.buildDocumentUriUsingTree(tree,id));
                        pendingParent=parent;pendingMove=saved.getBoolean("transferMove");
                    }
                }
            }
        } else tree=null;
        view=new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); view.setBackgroundColor(Color.rgb(13,19,32));
        choose=button(R.string.files_choose,chooseFolder); view.addView(choose);
        LinearLayout actions=new LinearLayout(context); view.addView(actions);
        up=button(R.string.files_up,()->{ if(ids.size()>1){ids.remove(ids.size()-1);names.remove(names.size()-1);page=0;load();} }); actions.addView(up,new LinearLayout.LayoutParams(0,-2,1));
        refresh=button(R.string.files_refresh,this::load);actions.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));
        create=button(R.string.files_create_folder,this::createFolder); actions.addView(create,new LinearLayout.LayoutParams(0,-2,1));
        transferActions=new LinearLayout(context);view.addView(transferActions);
        paste=button(R.string.files_paste_here,this::paste);transferActions.addView(paste,new LinearLayout.LayoutParams(0,-2,1));
        cancelPaste=button(android.R.string.cancel,this::clearTransfer);transferActions.addView(cancelPaste,new LinearLayout.LayoutParams(0,-2,1));
        search=new EditText(context); search.setSingleLine(); search.setHint(R.string.files_search); search.setTag("files.search"); search.setTextColor(Color.WHITE); search.setHintTextColor(Color.LTGRAY); view.addView(search);
        if(saved!=null)search.setText(saved.getString("search",""));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){page=0;show();} public void afterTextChanged(Editable e){} });
        status=new TextView(context); status.setTag("files.status"); status.setTextColor(Color.WHITE); view.addView(status);
        list=new ListView(context); list.setTag("files.list"); view.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,position,id)->select(visible.get(page*PAGE_SIZE+position)));
        LinearLayout pages=new LinearLayout(context); view.addView(pages);
        previous=button(R.string.radio_previous,()->{page--;show();}); pages.addView(previous,new LinearLayout.LayoutParams(0,-2,1));
        next=button(R.string.radio_next,()->{page++;show();}); pages.addView(next,new LinearLayout.LayoutParams(0,-2,1));
        show(); if(tree!=null)load();
    }
    private Button button(int label,Runnable action){Button b=new Button(context);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private boolean hasGrant(boolean write){
        if(tree==null)return false;
        for(var grant:context.getContentResolver().getPersistedUriPermissions())
            if(grant.getUri().equals(tree) && grant.isReadPermission() && (!write || grant.isWritePermission()))return true;
        return false;
    }
    public void setTree(Uri selected){
        if(closed)return;
        Uri previous=tree; tree=selected;
        if(!hasGrant(false)){tree=previous; show(); status.setText(R.string.files_permission); return;}
        cancel();
        context.getSharedPreferences("file_manager",0).edit().putString("tree",tree.toString()).apply();
        if(previous!=null && !previous.equals(tree))releaseOldGrant(previous);
        clearTransfer();
        ids.clear();names.clear();ids.add(DocumentsContract.getTreeDocumentId(tree));names.add(context.getString(R.string.files_root)); page=0;load();
    }
    @android.annotation.SuppressLint("WrongConstant")
    private void releaseOldGrant(Uri old){
        for(var grant:context.getContentResolver().getPersistedUriPermissions())if(grant.getUri().equals(old)){
            int flags=(grant.isReadPermission()?Intent.FLAG_GRANT_READ_URI_PERMISSION:0)|(grant.isWritePermission()?Intent.FLAG_GRANT_WRITE_URI_PERMISSION:0);
            if(flags!=0)try{context.getContentResolver().releasePersistableUriPermission(old,flags);}catch(SecurityException ignored){}
            break;
        }
    }
    private void cancel(){if(signal!=null)signal.cancel();if(task!=null)task.cancel(true);}
    private void load(){
        if(closed || tree==null)return;
        if(!hasGrant(false)){tree=null;clearTransfer();context.getSharedPreferences("file_manager",0).edit().remove("tree").apply();rows=List.of();show();status.setText(R.string.files_permission);return;}
        cancel();int token=++generation; Uri selected=tree;String folder=ids.get(ids.size()-1);
        signal=new CancellationSignal();CancellationSignal currentSignal=signal;loading=true;rows=List.of();show();WORKER.purge();
        try{task=WORKER.submit(()->{try{var result=TreeDocuments.list(context.getContentResolver(),selected,folder,currentSignal);
            main.post(()->{if(!closed && token==generation){loading=false;rows=result.entries;truncated=result.truncated;show();}});
        }catch(Exception error){main.post(()->{if(!closed && token==generation){loading=false;rows=List.of();truncated=false;show();status.setText(R.string.files_unavailable);}});}});}
        catch(RejectedExecutionException error){loading=false;show();status.setText(R.string.files_unavailable);}
    }
    private void show(){
        if(status==null || list==null)return;
        choose.setEnabled(!mutating);up.setEnabled(!mutating && tree!=null && ids.size()>1);refresh.setEnabled(!mutating && tree!=null);
        create.setEnabled(tree!=null && !loading && hasGrant(true));
        transferActions.setVisibility(pendingEntry==null?View.GONE:View.VISIBLE);
        paste.setEnabled(pendingEntry!=null && !loading && hasGrant(true) && (!pendingMove || !ids.get(ids.size()-1).equals(pendingParent)));
        cancelPaste.setEnabled(!mutating);
        search.setEnabled(!mutating);
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<TreeDocuments.Entry> found=new ArrayList<>();for(var row:rows)if(query.isEmpty() || row.name.toLowerCase(Locale.ROOT).contains(query))found.add(row);visible=found;
        page=Math.min(page,Math.max(0,(found.size()-1)/PAGE_SIZE));
        ArrayList<String> labels=new ArrayList<>();for(int i=page*PAGE_SIZE;i<Math.min(found.size(),(page+1)*PAGE_SIZE);i++){var row=found.get(i);labels.add((row.directory()?"📁 ":"📄 ")+row.name+(row.size>=0 && !row.directory()?" · "+row.size+" B":""));}
        list.setAdapter(new ArrayAdapter<String>(context,android.R.layout.simple_list_item_1,labels){@Override public View getView(int position,View recycled,android.view.ViewGroup parent){View row=super.getView(position,recycled,parent);if(row instanceof TextView)((TextView)row).setTextColor(Color.WHITE);return row;}});list.setEnabled(tree!=null && !loading);
        previous.setEnabled(!mutating && page>0);next.setEnabled(!mutating && (page+1)*PAGE_SIZE<found.size());
        status.setText(loading?context.getString(R.string.movies_loading):tree==null?context.getString(R.string.files_no_folder):pendingEntry!=null?context.getString(pendingMove?R.string.files_move_pending:R.string.files_copy_pending,pendingEntry.name,String.join(" / ",names)):context.getResources().getQuantityString(truncated?R.plurals.files_count_limited:R.plurals.files_count,found.size(),String.join(" / ",names),found.size()));
    }
    private void select(TreeDocuments.Entry entry){
        if(entry.directory()){if(ids.size()>=100){status.setText(R.string.files_depth);return;}ids.add(entry.id);names.add(entry.name);page=0;load();return;}
        ArrayList<String> options=new ArrayList<>();options.add(context.getString(R.string.files_open));
        boolean writable=hasGrant(true);if(writable && (entry.flags&DocumentsContract.Document.FLAG_SUPPORTS_RENAME)!=0)options.add(context.getString(R.string.files_rename));
        if(writable && (entry.flags&DocumentsContract.Document.FLAG_PARTIAL)==0 && ((entry.flags&DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT)==0 || (entry.flags&DocumentsContract.Document.FLAG_SUPPORTS_COPY)!=0))options.add(context.getString(R.string.files_copy));
        if(writable && (entry.flags&DocumentsContract.Document.FLAG_SUPPORTS_MOVE)!=0)options.add(context.getString(R.string.files_move));
        if(writable && (entry.flags&DocumentsContract.Document.FLAG_SUPPORTS_DELETE)!=0)options.add(context.getString(R.string.files_delete));
        actionDialog=new AlertDialog.Builder(context).setTitle(entry.name).setItems(options.toArray(new String[0]),(d,which)->{
            String choice=options.get(which);
            if(choice.equals(context.getString(R.string.files_open)))open(entry);
            else if(choice.equals(context.getString(R.string.files_rename)))rename(entry);
            else if(choice.equals(context.getString(R.string.files_copy)))startTransfer(entry,false);
            else if(choice.equals(context.getString(R.string.files_move)))startTransfer(entry,true);
            else delete(entry);
        }).show();
    }
    private void startTransfer(TreeDocuments.Entry entry,boolean move){
        pendingEntry=entry;pendingParent=ids.get(ids.size()-1);pendingMove=move;show();
    }
    private void clearTransfer(){pendingEntry=null;pendingParent=null;pendingMove=false;show();}
    private void paste(){
        if(pendingEntry==null || tree==null || !hasGrant(true))return;
        TreeDocuments.Entry source=pendingEntry;String sourceParent=pendingParent;boolean move=pendingMove;
        String destination=ids.get(ids.size()-1);if(move && destination.equals(sourceParent))return;
        Uri from=DocumentsContract.buildDocumentUriUsingTree(tree,sourceParent);
        Uri to=DocumentsContract.buildDocumentUriUsingTree(tree,destination);
        mutate(()->move?DocumentsContract.moveDocument(context.getContentResolver(),source.uri,from,to)!=null:TreeDocuments.copyFile(context.getContentResolver(),source,to),this::clearTransfer);
    }
    private void open(TreeDocuments.Entry entry){
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(entry.uri,entry.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{context.startActivity(intent);}catch(Exception error){status.setText(R.string.files_no_viewer);}
    }
    private void rename(TreeDocuments.Entry entry){
        EditText name=new EditText(context);name.setSingleLine();name.setText(entry.name);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(255)});
        editDialog=new AlertDialog.Builder(context).setTitle(R.string.files_rename).setView(name).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.files_rename,null).create();
        editDialog.show();editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=name.getText().toString().trim();if(!validName(value)){name.setError(context.getString(R.string.files_invalid_name));return;}
            editDialog.dismiss();mutate(()->DocumentsContract.renameDocument(context.getContentResolver(),entry.uri,value)!=null);
        });
    }
    private void createFolder(){
        if(tree==null || !hasGrant(true)){status.setText(R.string.files_permission);return;}
        EditText name=new EditText(context);name.setSingleLine();name.setHint(R.string.files_folder_name);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(255)});
        editDialog=new AlertDialog.Builder(context).setTitle(R.string.files_create_folder).setView(name).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.files_create_folder,null).create();
        editDialog.show();editDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=name.getText().toString().trim();if(!validName(value)){name.setError(context.getString(R.string.files_invalid_name));return;}
            Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,ids.get(ids.size()-1));
            editDialog.dismiss();mutate(()->DocumentsContract.createDocument(context.getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,value)!=null);
        });
    }
    private static boolean validName(String value){return !value.isEmpty() && !value.contains("/") && !value.contains("\\") && value.chars().noneMatch(c->c<32);}
    private void delete(TreeDocuments.Entry entry){
        editDialog=new AlertDialog.Builder(context).setTitle(R.string.files_delete).setMessage(context.getString(R.string.files_confirm_delete,entry.name))
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.files_delete,(d,w)->mutate(()->DocumentsContract.deleteDocument(context.getContentResolver(),entry.uri))).show();
    }
    private interface Change { boolean apply() throws Exception; }
    private void mutate(Change change){mutate(change,()->{});}
    private void mutate(Change change,Runnable onSuccess){
        int token=++generation;loading=true;mutating=true;show();
        try{WORKER.execute(()->{boolean success=false;try{success=change.apply();}catch(Exception ignored){}
            boolean result=success;main.post(()->{if(!closed && token==generation){loading=false;mutating=false;if(result){onSuccess.run();load();}else {show();status.setText(R.string.files_change_failed);}}});
        });}catch(RejectedExecutionException error){loading=false;mutating=false;show();status.setText(R.string.files_change_failed);}
    }
    public boolean onBack(){if(mutating)return true;if(ids.size()<=1)return false;ids.remove(ids.size()-1);names.remove(names.size()-1);page=0;load();return true;}
    public LinearLayout getView(){return view;}
    public Bundle saveState(){Bundle state=new Bundle();state.putStringArrayList("ids",new ArrayList<>(ids));state.putStringArrayList("names",new ArrayList<>(names));state.putString("search",search.getText().toString());state.putInt("page",page);
        if(pendingEntry!=null && !mutating && tree!=null){state.putString("transferTree",tree.toString());state.putString("transferId",pendingEntry.id);state.putString("transferParent",pendingParent);state.putString("transferName",pendingEntry.name);state.putString("transferMime",pendingEntry.mime);state.putLong("transferSize",pendingEntry.size);state.putInt("transferFlags",pendingEntry.flags);state.putBoolean("transferMove",pendingMove);}return state;}
    public void close(){closed=true;cancel();if(actionDialog!=null)actionDialog.dismiss();if(editDialog!=null)editDialog.dismiss();main.removeCallbacksAndMessages(null);}
}
