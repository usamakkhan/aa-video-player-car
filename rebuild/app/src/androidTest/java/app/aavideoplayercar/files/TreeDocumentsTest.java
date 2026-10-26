package app.aavideoplayercar.files;

import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TreeDocumentsTest {
    private static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS};
    @Test public void sortsDirectoriesValidatesRowsAndKeepsDocumentTreeUris() throws Exception {
        MatrixCursor cursor=new MatrixCursor(COLUMNS);
        cursor.addRow(new Object[]{"file","zeta.mp4","video/mp4",120,DocumentsContract.Document.FLAG_SUPPORTS_RENAME});
        cursor.addRow(new Object[]{"folder","Albums",DocumentsContract.Document.MIME_TYPE_DIR,null,0});
        cursor.addRow(new Object[]{"bad","",null,0,0});
        Uri tree=Uri.parse("content://fixture/tree/root");
        var page=TreeDocuments.read(cursor,tree);
        assertFalse(page.truncated);assertEquals(2,page.entries.size());
        assertTrue(page.entries.get(0).directory());assertEquals("Albums",page.entries.get(0).name);
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(tree,"file"),page.entries.get(1).uri);
        assertEquals(120,page.entries.get(1).size);
    }
    @Test public void capsLargeProviderListings() throws Exception {
        MatrixCursor cursor=new MatrixCursor(COLUMNS);
        for(int i=0;i<TreeDocuments.MAX_ENTRIES+1;i++)cursor.addRow(new Object[]{"id"+i,"File "+i,"text/plain",i,0});
        var page=TreeDocuments.read(cursor,Uri.parse("content://fixture/tree/root"));
        assertEquals(TreeDocuments.MAX_ENTRIES,page.entries.size());assertTrue(page.truncated);
    }
    @Test public void rejectsMissingProviderColumns() {
        MatrixCursor cursor=new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});
        assertThrows(java.io.IOException.class,()->TreeDocuments.read(cursor,Uri.parse("content://fixture/tree/root")));
    }
}
