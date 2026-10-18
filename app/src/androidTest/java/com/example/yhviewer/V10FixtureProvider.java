package com.example.yhviewer;

import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Test APK only. Java avoids depending on target-APK Kotlin runtime in this provider's process. */
public class V10FixtureProvider extends DocumentsProvider {
    private static final String AUTHORITY = "com.example.yhviewer.v10.fixtures";
    private File root(String name) {
        if ("external".equals(name)) return new File(getContext().getFilesDir(), "v10-pack");
        if (!"generic".equals(name)) throw new IllegalArgumentException("Unknown fixture root");
        File f = new File(getContext().getFilesDir(), "v10-fixtures"); f.mkdirs(); return f;
    }
    @Override public boolean onCreate() {
        for (String name : new String[]{"generic", "external"}) getContext().grantUriPermission(
            "io.github.zer0rin.yhviewer", DocumentsContract.buildTreeDocumentUri(AUTHORITY, name + ":"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return true;
    }
    private File file(String id) {
        int colon = id.indexOf(':'); if (colon < 0) throw new IllegalArgumentException(id);
        try {
            File r = root(id.substring(0, colon)).getCanonicalFile();
            File f = new File(r, id.substring(colon + 1)).getCanonicalFile();
            if (!f.equals(r) && !f.getPath().startsWith(r.getPath() + File.separator)) throw new IllegalArgumentException(id);
            return f;
        } catch (IOException e) { throw new IllegalArgumentException(e); }
    }
    private void erase(File f) {
        File[] children = f.listFiles(); if (children != null) for (File child : children) erase(child);
        f.delete();
    }
    private void put(String path, String text) {
        File f = file("generic:" + path); f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    private String repeated(String text) { StringBuilder out = new StringBuilder(); for (int i=0;i<60;i++) out.append(text); return out.toString(); }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("fixtureReset".equals(method)) {
            erase(root("generic")); root("generic").mkdirs();
            put("page.html", "<html><head><link rel=stylesheet href='assets/layout.css'></head><body><h1>Fixture page</h1><img src='assets/card.svg'><a href='other.html#second'>Next document</a><button onclick=\"alert('fixture alert')\">Alert</button><p>needle one needle two END-HTML</p></body></html>");
            put("other.html", "<h1>Other document</h1><h2 id=second>Second heading</h2><p>SECOND-END</p>");
            put("notes.md", "# Notes\n\nA footnote[^note].\n\n[^note]: Footnote payload.\n\nEND-MD");
            put("assets/layout.css", "body { background: rgb(230,248,230); border: 4px solid rgb(26,108,65); }");
            put("assets/card.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='60'><rect width='120' height='60' fill='green'/></svg>");
            put("novel.txt", "第一章 起点\n" + repeated("起点文字 needle\n") + "第二章 终点\n" + repeated("终点文字 needle\n") + "T-END");
            put("sample.log", "Log record LOG-END");
            put("preview.svg", "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='60'><text x='5' y='30'>SVG image</text></svg>");
            put("unsupported.zip", "not a supported document");
            Bundle b = new Bundle(); b.putBoolean("ready", true); return b;
        }
        if ("externalReady".equals(method)) { Bundle b = new Bundle(); b.putBoolean("ready", new File(root("external"), "测试清单.csv").isFile()); return b; }
        if ("removeGeneric".equals(method)) { File f=file("generic:"+arg); if (!f.isFile()) throw new IllegalArgumentException(arg); f.delete(); return Bundle.EMPTY; }
        return super.call(method, arg, extras);
    }
    private String mime(File f) { String n=f.getName(); int i=n.lastIndexOf('.'); String ext=i<0?"":n.substring(i+1).toLowerCase(java.util.Locale.ROOT); String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); return mime==null?"application/octet-stream":mime; }
    private String[] columns(String[] projection) { return projection==null?new String[]{Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_FLAGS}:projection; }
    private Object[] row(String[] columns, File f, String id) {
        Object[] out=new Object[columns.length];
        for(int i=0;i<columns.length;i++) switch(columns[i]) {
            case Document.COLUMN_DOCUMENT_ID: out[i]=id; break;
            case Document.COLUMN_DISPLAY_NAME: out[i]=id.endsWith(":")?"YHViewer fixture folder":f.getName(); break;
            case Document.COLUMN_MIME_TYPE: out[i]=f.isDirectory()?Document.MIME_TYPE_DIR:mime(f); break;
            case Document.COLUMN_SIZE: out[i]=f.isFile()?f.length():0L; break;
            case Document.COLUMN_FLAGS: out[i]=0; break;
            default: out[i]=null;
        }
        return out;
    }
    @Override public Cursor queryDocument(String id, String[] projection) { String[] cols=columns(projection); MatrixCursor c=new MatrixCursor(cols); c.addRow(row(cols,file(id),id)); return c; }
    @Override public Cursor queryChildDocuments(String id, String[] projection, String sortOrder) {
        String name=id.substring(0,id.indexOf(':')); String[] cols=columns(projection); MatrixCursor c=new MatrixCursor(cols);
        File[] files=file(id).listFiles(); if(files==null)return c; Arrays.sort(files,Comparator.comparing(File::getName));
        for(File f:files) { String rel=file(name+":").toURI().relativize(f.toURI()).getPath(); if(rel.endsWith("/"))rel=rel.substring(0,rel.length()-1); c.addRow(row(cols,f,name+":"+rel)); }
        return c;
    }
    @Override public Cursor queryRoots(String[] projection) {
        String[] cols=projection==null?new String[]{Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_TITLE,Root.COLUMN_FLAGS}:projection;
        Map<String,Object> values=new HashMap<>(); values.put(Root.COLUMN_ROOT_ID,"generic");values.put(Root.COLUMN_DOCUMENT_ID,"generic:");values.put(Root.COLUMN_TITLE,"YHViewer fixtures");values.put(Root.COLUMN_FLAGS,Root.FLAG_SUPPORTS_IS_CHILD);
        MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=values.get(cols[i]);c.addRow(row);return c;
    }
    @Override public boolean isChildDocument(String parent, String id) { return file(id).getPath().startsWith(file(parent).getPath()+File.separator); }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
