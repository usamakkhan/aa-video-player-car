package app.aavideoplayercar.files;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.DocumentsContract;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/** Bounded Storage Access Framework directory reads. Providers perform I/O on a worker. */
public final class TreeDocuments {
    public static final int MAX_ENTRIES = 10000;
    private static final String[] COLUMNS = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_FLAGS};
    private TreeDocuments() {}
    public static final class Entry {
        public final String id, name, mime;
        public final long size;
        public final int flags;
        public final Uri uri;
        Entry(String id, String name, String mime, long size, int flags, Uri uri) {
            this.id=id; this.name=name; this.mime=mime; this.size=size; this.flags=flags; this.uri=uri;
        }
        public boolean directory() { return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime); }
    }
    public static final class Page {
        public final List<Entry> entries;
        public final boolean truncated;
        Page(List<Entry> entries, boolean truncated) { this.entries=Collections.unmodifiableList(entries); this.truncated=truncated; }
    }
    public static Page list(ContentResolver resolver, Uri tree, String folderId, CancellationSignal signal) throws IOException {
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId);
        try (Cursor cursor=resolver.query(children,COLUMNS,null,null,null,signal)) {
            if(cursor==null)throw new IOException("Folder unavailable");
            return read(cursor,tree);
        }
    }
    /** Prefer a provider-side copy; stream ordinary files when the provider does not offer one. */
    public static boolean copyFile(ContentResolver resolver, Entry source, Uri targetParent) throws Exception {
        if(source.directory() || (source.flags&DocumentsContract.Document.FLAG_PARTIAL)!=0)return false;
        if((source.flags&DocumentsContract.Document.FLAG_SUPPORTS_COPY)!=0)
            return DocumentsContract.copyDocument(resolver,source.uri,targetParent)!=null;
        if((source.flags&DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT)!=0)return false;
        String mime=source.mime.isEmpty()?"application/octet-stream":source.mime;
        Uri created=DocumentsContract.createDocument(resolver,targetParent,mime,source.name);
        if(created==null)return false;
        boolean complete=false;
        try {
            try(InputStream input=resolver.openInputStream(source.uri);OutputStream output=resolver.openOutputStream(created,"w")) {
                if(input==null || output==null)throw new IOException("Document stream unavailable");
                byte[] buffer=new byte[65536];int read;
                while((read=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Copy interrupted");output.write(buffer,0,read);}
                output.flush();
            }
            complete=true;
        } finally {if(!complete)try{DocumentsContract.deleteDocument(resolver,created);}catch(Exception ignored){}}
        return true;
    }
    static Page read(Cursor cursor, Uri tree) throws IOException {
        int idColumn=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
        int nameColumn=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        int mimeColumn=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
        if(idColumn<0 || nameColumn<0 || mimeColumn<0)throw new IOException("Incomplete folder listing");
        int sizeColumn=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE), flagsColumn=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);
        List<Entry> entries=new ArrayList<>(); boolean truncated=false;
        while(cursor.moveToNext()) {
            if(entries.size()==MAX_ENTRIES) { truncated=true; break; }
            String id=cursor.getString(idColumn), name=cursor.getString(nameColumn), mime=cursor.getString(mimeColumn);
            if(id==null || id.isEmpty() || id.length()>2048 || mime==null || mime.length()>255)continue;
            if(name==null || name.trim().isEmpty())name="Unnamed file";
            if(name.length()>500)name=name.substring(0,500);
            long size=sizeColumn<0 || cursor.isNull(sizeColumn) ? -1 : Math.max(-1,cursor.getLong(sizeColumn));
            int flags=flagsColumn<0 || cursor.isNull(flagsColumn) ? 0 : cursor.getInt(flagsColumn);
            entries.add(new Entry(id,name,mime,size,flags,DocumentsContract.buildDocumentUriUsingTree(tree,id)));
        }
        entries.sort(Comparator.comparing((Entry e)->!e.directory()).thenComparing(e->e.name,String.CASE_INSENSITIVE_ORDER).thenComparing(e->e.id));
        return new Page(entries,truncated);
    }
}
