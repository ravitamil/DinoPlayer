package com.brouken.player;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** One focused editor for text, individual timings, global offset and document export. */
public final class SubtitleEditorActivity extends AppCompatActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private List<SubtitleTimeline.Cue> cues=new ArrayList<>();
    private Uri pendingExport;
    private Uri media;private CaptionPack pack;private String path;private File draft;
    private boolean dirty,busy=true,exportVtt;
    private TextView status;private RecyclerView list;private Rows rows;
    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
                Uri uri = result.getData().getData();
                if (busy || cues.isEmpty()) { pendingExport = uri; return; }
                export(uri);
            });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
        media=getIntent().getData();path=getIntent().getStringExtra("path");
        pack=CaptionPack.find(state==null?CaptionPackages.get(this).selected().id:state.getString("pack"));
        draft=state==null?new File(getCacheDir(),"subtitle-draft-"+UUID.randomUUID()+".srt"):new File(state.getString("draft"));
        dirty=state!=null && state.getBoolean("dirty");
        if(state!=null) { exportVtt=state.getBoolean("vtt");pendingExport=state.getParcelable("pendingExport"); }
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);setContentView(page);
        Toolbar toolbar=new Toolbar(this);toolbar.setTitle("Edit subtitles");toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setNavigationOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());page.addView(toolbar,new LinearLayout.LayoutParams(-1,Utils.dpToPx(56)));
        status=new TextView(this);status.setTextSize(14);status.setPadding(16,8,16,8);status.setText("Loading subtitles…");page.addView(status);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));rows=new Rows();list.setAdapter(rows);
        page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);page.addView(actions);
        button(actions,"Shift timing",()->shift());button(actions,"Export",()->chooseExport());button(actions,"Save",()->save());
        io.execute(()-> {
            try {
                Uri source=getIntent().getParcelableExtra("subtitle");
                File file=draft.isFile()?draft:CaptionLibrary.find(this,media,path);
                List<SubtitleTimeline.Cue> loaded;
                if(file!=null && (draft.isFile() || source==null)) {
                    try(Reader reader=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)) { loaded=SubtitleDocument.read(reader); }
                } else if(source!=null) {
                    try(InputStream stream=getContentResolver().openInputStream(source)) {
                        if(stream==null)throw new IOException("Cannot open subtitle file");
                        try(Reader reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) { loaded=SubtitleDocument.read(reader); }
                    }
                } else throw new IOException("Generate subtitles or open an SRT/VTT file in the player first");
                main.post(()-> { if(isDestroyed())return;cues=loaded;busy=false;rows.notifyDataSetChanged();updateStatus();
                    if(pendingExport!=null) { Uri destination=pendingExport;pendingExport=null;export(destination); } });
            }catch(Exception e) { main.post(()-> { if(isDestroyed())return;status.setText(e.getMessage());busy=false; }); }
        });
    }
    private void button(LinearLayout parent,String label,Runnable action) {
        Button button=new Button(this);button.setText(label);button.setTextSize(14);button.setAllCaps(false);button.setMinHeight(Utils.dpToPx(48));
        parent.addView(button,new LinearLayout.LayoutParams(0,-2,1));button.setOnClickListener(v->{if(!busy && !cues.isEmpty())action.run();});
    }
    private void updateStatus() { status.setText(cues.size()+" captions · Tap a caption to edit"+(dirty?" · Unsaved changes":"")); }
    private EditText field(LinearLayout form,String hint,String text,boolean multiline) {
        EditText edit=new EditText(this);edit.setTextSize(16);edit.setHint(hint);edit.setText(text);edit.setSelectAllOnFocus(true);
        edit.setInputType(multiline?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_CLASS_TEXT);
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(multiline?16384:20)});
        if(multiline) { edit.setMinLines(2);edit.setMaxLines(6); }form.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;
    }
    private void edit(int index) {
        if(busy || index<0 || index>=cues.size())return;
        SubtitleTimeline.Cue original=cues.get(index);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,0,24,0);
        EditText start=field(form,"Start · hh:mm:ss.mmm",time(original.startMs),false);
        EditText end=field(form,"End · hh:mm:ss.mmm",time(original.endMs),false);
        EditText text=field(form,"Subtitle text",original.text,true);
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Caption "+(index+1)).setView(form)
                .setPositiveButton("Apply",null).setNegativeButton(android.R.string.cancel,null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v-> {
            try {
                String content=text.getText().toString().trim();
                if(content.isEmpty() || content.contains("\n\n"))throw new IOException("Use non-empty subtitle text without blank lines");
                SubtitleTimeline.Cue changed=new SubtitleTimeline.Cue(SubtitleDocument.parseTime(start.getText().toString()),SubtitleDocument.parseTime(end.getText().toString()),content);
                List<SubtitleTimeline.Cue> updated=new ArrayList<>(cues);updated.set(index,changed);SubtitleDocument.validate(updated);
                cues=updated;rows.notifyItemChanged(index);changed();dialog.dismiss();
            }catch(Exception e) { text.setError(e.getMessage()); }
        }));dialog.show();
    }
    private void shift() {
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,0,24,0);
        EditText value=field(form,"Milliseconds: +500 later, -500 earlier","0",false);
        value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Shift all subtitle times").setMessage("Negative offsets discard captions that end before the video starts.").setView(form)
                .setPositiveButton("Apply",null).setNegativeButton(android.R.string.cancel,null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v-> {
            try { cues=SubtitleDocument.shifted(cues,Long.parseLong(value.getText().toString()));changed();rows.notifyDataSetChanged();dialog.dismiss(); }
            catch(Exception e) { value.setError(e.getMessage()==null?"Enter milliseconds":e.getMessage()); }
        }));dialog.show();
    }
    private void changed() {
        dirty=true;updateStatus();List<SubtitleTimeline.Cue> copy=new ArrayList<>(cues);
        io.execute(()-> { try { writeFile(draft,copy,false); }catch(IOException ignored) { } });
    }
    private static void writeFile(File target,List<SubtitleTimeline.Cue> data,boolean vtt) throws IOException {
        File temp=new File(target.getPath()+".tmp");
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(temp),StandardCharsets.UTF_8)) { SubtitleDocument.write(writer,data,vtt); }
        if(!temp.renameTo(target))throw new IOException("Could not save subtitle draft");
    }
    private void save() {
        busy=true;status.setText("Saving…");List<SubtitleTimeline.Cue> copy=new ArrayList<>(cues);
        io.execute(()-> {
            try {
                CaptionLibrary.save(this,media,pack,copy);String token="edited-"+UUID.randomUUID();CaptionWorkStore store=new CaptionWorkStore(this);
                store.begin(media,pack,token);store.update(media,pack,token,CaptionWorkStore.Status.READY,100,"");draft.delete();
                main.post(()-> { if(isDestroyed())return;dirty=false;setResult(RESULT_OK,new Intent().setData(Uri.fromFile(CaptionLibrary.saved(this,media,pack))));finish(); });
            }catch(Exception e) { main.post(()->error(e)); }
        });
    }
    private void chooseExport() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Export subtitles").setItems(new String[]{"SRT","VTT"},(d,w)-> {
            exportVtt=w==1;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(exportVtt?"text/vtt":"application/x-subrip").putExtra(Intent.EXTRA_TITLE,"subtitles."+(exportVtt?"vtt":"srt"));
            try { exportLauncher.launch(intent); }catch(ActivityNotFoundException e) { error(e); }
        }).show();
    }
    private void export(Uri destination) {
        boolean vtt=exportVtt;List<SubtitleTimeline.Cue> copy=new ArrayList<>(cues);busy=true;
        status.setText("Exporting…");io.execute(()-> {
            try(OutputStream stream=getContentResolver().openOutputStream(destination,"wt")) {
                if(stream==null)throw new IOException("Cannot write to the selected destination");
                try(Writer writer=new OutputStreamWriter(stream,StandardCharsets.UTF_8)) { SubtitleDocument.write(writer,copy,vtt); }
                main.post(()-> { if(isDestroyed())return;busy=false;updateStatus();Toast.makeText(this,"Subtitles exported",Toast.LENGTH_SHORT).show(); });
            }catch(Exception e) { main.post(()->error(e)); }
        });
    }
    private void error(Exception e) { if(isDestroyed())return;busy=false;updateStatus();Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
    private void handleBackPressed() {
        if(busy)return;
        if(!dirty) { finish();return; }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setMessage("Save subtitle changes?").setPositiveButton("Save",(d,w)->save())
                .setNegativeButton("Discard",(d,w)-> { io.execute(()->draft.delete());finish(); }).setNeutralButton("Keep editing",null).show();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);out.putString("draft",draft.getPath());out.putBoolean("dirty",dirty);out.putBoolean("vtt",exportVtt);out.putString("pack",pack.id);out.putParcelable("pendingExport",pendingExport);
    }
    @Override protected void onDestroy() { io.shutdown();super.onDestroy(); }
    private static String time(long ms) { return SubtitleTimeline.timestamp(ms).replace(',','.'); }
    private final class Rows extends RecyclerView.Adapter<Row> {
        @Override public Row onCreateViewHolder(android.view.ViewGroup parent,int type) {
            TextView text=new TextView(SubtitleEditorActivity.this);text.setTextSize(16);text.setPadding(24,16,24,16);text.setMinHeight(Utils.dpToPx(72));
            text.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Row(text);
        }
        @Override public void onBindViewHolder(Row holder,int position) {
            SubtitleTimeline.Cue cue=cues.get(position);holder.text.setText(time(cue.startMs)+" → "+time(cue.endMs)+"\n"+cue.text);
            holder.text.setOnClickListener(v->edit(holder.getAdapterPosition()));
        }
        @Override public int getItemCount() { return cues.size(); }
    }
    private static final class Row extends RecyclerView.ViewHolder { final TextView text;Row(TextView text) { super(text);this.text=text; } }
}
