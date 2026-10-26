package com.dreamtoon.studio;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.FileInputStream;
import java.io.OutputStream;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
 private VideoView video;
 private TextView info;
 private Uri selected;
 private SeekBar seek;
 private long startMs=0,endMs=0;
 private void button(LinearLayout parent,String title,Runnable action){
  Button b=new Button(this);b.setText(title);parent.addView(b);
  b.setOnClickListener(v->action.run());
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,30,20,20);
  TextView title=new TextView(this);title.setText("Dreamtoon Studio");title.setTextSize(26);root.addView(title);
  info=new TextView(this);info.setText("Choose an MP4 video to begin");root.addView(info);
  video=new VideoView(this);root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
  seek=new SeekBar(this);root.addView(seek);
  button(root,"Import Video",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);});
  button(root,"Play / Pause",()->{if(selected==null)return;if(video.isPlaying())video.pause();else video.start();});
  button(root,"Set Trim Start at Current Frame",()->{if(selected==null)return;startMs=video.getCurrentPosition();showTrim();});
  button(root,"Set Trim End at Current Frame",()->{if(selected==null)return;endMs=video.getCurrentPosition();showTrim();});
  button(root,"Export Trimmed MP4",this::export);
  TextView note=new TextView(this);
  note.setText("Preview and keyframe-aligned trim export. Text burn-in, audio mixing and 9:16 conversion are not available in this build.");
  root.addView(note);
  setContentView(root);
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&selected!=null)video.seekTo(p);}
   public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
  });
  video.setOnPreparedListener(mp->{seek.setMax(video.getDuration());endMs=video.getDuration();showTrim();});
 }
 private void showTrim(){info.setText("Trim "+(startMs/1000.0)+"s to "+(endMs/1000.0)+"s");}
 private void export(){
  if(selected==null){info.setText("Import a video first");return;}
  if(endMs<=startMs){info.setText("Trim end must be after start");return;}
  info.setText("Exporting...");
  Uri input=selected;long from=startMs,to=endMs;
  new Thread(()->{
   try{
    File folder=getExternalFilesDir(Environment.DIRECTORY_MOVIES);
    if(folder==null)throw new IllegalStateException("Movies directory unavailable");
    if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("Cannot create output directory");
    File output=new File(folder,"Dreamtoon_"+System.currentTimeMillis()+".mp4");
    try{VideoEngine.trim(this,input,output,from,to);}
    catch(Exception e){output.delete();throw e;}
    ContentValues values=new ContentValues();
    values.put(MediaStore.Video.Media.DISPLAY_NAME,output.getName());
    values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
    values.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/Dreamtoon Studio");
    values.put(MediaStore.Video.Media.IS_PENDING,1);
    Uri gallery=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
    if(gallery==null)throw new IllegalStateException("Gallery save unavailable");
    try{
     try(FileInputStream inputStream=new FileInputStream(output);
         OutputStream outputStream=getContentResolver().openOutputStream(gallery)){
      if(outputStream==null)throw new IllegalStateException("Cannot open Gallery file");
      byte[] chunk=new byte[65536];int count;
      while((count=inputStream.read(chunk))!=-1)outputStream.write(chunk,0,count);
     }
     ContentValues done=new ContentValues();
     done.put(MediaStore.Video.Media.IS_PENDING,0);
     getContentResolver().update(gallery,done,null,null);
    }catch(Exception error){getContentResolver().delete(gallery,null,null);throw error;}
    output.delete();
    runOnUiThread(()->info.setText("Video saved to Gallery: "+gallery));
   }catch(Exception ex){runOnUiThread(()->info.setText("Export failed: "+ex.getMessage()));}
  }).start();
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==1&&result==RESULT_OK&&data!=null){
   selected=data.getData();
   video.setVideoURI(selected);video.start();
  }
 }
}