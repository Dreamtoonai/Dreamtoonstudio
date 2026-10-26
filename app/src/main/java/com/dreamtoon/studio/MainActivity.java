package com.dreamtoon.studio;
import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.content.ContentValues;
import android.provider.MediaStore;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import android.widget.*;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;

public class MainActivity extends Activity {
 private VideoView video;
 private final Handler progressHandler=new Handler(Looper.getMainLooper());
 private final Runnable progressTask=new Runnable(){public void run(){if(video!=null&&seek!=null&&selected!=null&&!seekDragging){seek.setProgress(video.getCurrentPosition());}progressHandler.postDelayed(this,250);}};
 private boolean seekDragging=false;
 private TextView info;
 private Uri selected;
 private SeekBar seek;
 private TextView captionPreview;
 private EditText captionInput;
 private Uri lastExport;
 private Uri musicUri;
 private MediaPlayer musicPlayer;
 private TextToSpeech tts;
 private boolean ttsReady=false;
 private EditText voiceScript;
 private MediaRecorder recorder;
 private File recordedVoice;
 private long startMs=0,endMs=0;
 private void button(LinearLayout parent,String title,Runnable action){
  Button b=new Button(this);b.setText(title);parent.addView(b);
  b.setOnClickListener(v->action.run());
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,30,20,20);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);
  TextView title=new TextView(this);title.setText("Dreamtoon Studio");title.setTextSize(26);root.addView(title);
  info=new TextView(this);info.setText("Choose an MP4 video to begin");root.addView(info);
  FrameLayout stage=new FrameLayout(this);
  root.addView(stage,new LinearLayout.LayoutParams(-1,600));
  video=new VideoView(this);stage.addView(video,new FrameLayout.LayoutParams(-1,-1));
  captionPreview=new TextView(this);
  captionPreview.setTextColor(Color.WHITE);
  captionPreview.setTextSize(24);
  captionPreview.setShadowLayer(4,2,2,Color.BLACK);
  captionPreview.setGravity(Gravity.CENTER);
  FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
  overlay.bottomMargin=32;
  stage.addView(captionPreview,overlay);
  captionInput=new EditText(this);
  captionInput.setSingleLine(false);
  captionInput.setHint("বাংলা / English caption (preview only)");
  root.addView(captionInput);
  button(root,"Update Caption Preview",()->captionPreview.setText(captionInput.getText().toString()));
  seek=new SeekBar(this);root.addView(seek);
  button(root,"Import Video",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);});
  button(root,"Play / Pause",()->{if(selected==null)return;if(video.isPlaying())video.pause();else video.start();});
  button(root,"Set Trim Start at Current Frame",()->{if(selected==null)return;startMs=video.getCurrentPosition();showTrim();});
  button(root,"Set Trim End at Current Frame",()->{if(selected==null)return;endMs=video.getCurrentPosition();showTrim();});
  button(root,"Export Trimmed MP4",this::export);
  button(root,"Export 9:16 Reel + Permanent Caption",this::exportReel);
  button(root,"Share Last Export",this::shareLastExport);
  button(root,"Save Caption as SRT",this::saveSubtitle);
  button(root,"Choose Background Music",()->{
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(i,2);
  });
  button(root,"Play / Stop Music Preview",this::toggleMusicPreview);
  voiceScript=new EditText(this);
  voiceScript.setHint("AI Voice: বাংলা বা English লিখুন");
  voiceScript.setMinLines(2);
  root.addView(voiceScript);
  button(root,"Speak AI Voice Preview",this::speakVoice);
  button(root,"Save AI Voice WAV",this::saveAiVoice);
  button(root,"Start Voice Recording",this::startVoiceRecording);
  button(root,"Stop Voice Recording",this::stopVoiceRecording);
  TextView note=new TextView(this);
  note.setText("Use Export 9:16 Reel for permanent caption. Music/voice mixing into the video is not yet available.");
  root.addView(note);
  setContentView(scroll);
  tts=new TextToSpeech(this,code->runOnUiThread(()->{
   ttsReady=code==TextToSpeech.SUCCESS;
   if(!ttsReady)info.setText("Text-to-Speech initialization failed");
  }));
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&selected!=null)video.seekTo(p);}
   public void onStartTrackingTouch(SeekBar s){seekDragging=true;}public void onStopTrackingTouch(SeekBar s){seekDragging=false;}
  });
  video.setOnPreparedListener(mp->{seek.setMax(video.getDuration());startMs=0;endMs=video.getDuration();showTrim();});
  progressHandler.post(progressTask);
 }
 private void showTrim(){info.setText("Trim "+(startMs/1000.0)+"s to "+(endMs/1000.0)+"s");}
 private void exportReel(){
  if(selected==null){info.setText("Import a video first");return;}
  if(endMs<=startMs){info.setText("Choose a valid trim range");return;}
  File folder=getExternalFilesDir(Environment.DIRECTORY_MOVIES);
  if(folder==null){info.setText("Movies directory unavailable");return;}
  if(!folder.exists()&&!folder.mkdirs()){info.setText("Cannot create output folder");return;}
  File out=new File(folder,"Dreamtoon_Reel_"+System.currentTimeMillis()+".mp4");
  info.setText("Rendering vertical Reel... please wait");
  try{
   ReelsExporter.export(this,selected,out,startMs,endMs,
      captionInput.getText().toString(),new ReelsExporter.Callback(){
       @Override public void onSuccess(File output){
        new Thread(()->{
         try{
          ContentValues values=new ContentValues();
          values.put(MediaStore.Video.Media.DISPLAY_NAME,output.getName());
          values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
          values.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/Dreamtoon Studio");
          values.put(MediaStore.Video.Media.IS_PENDING,1);
          Uri gallery=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
          if(gallery==null)throw new IllegalStateException("Gallery unavailable");
          try{
           try(FileInputStream inputStream=new FileInputStream(output);
               OutputStream stream=getContentResolver().openOutputStream(gallery)){
            if(stream==null)throw new IllegalStateException("Cannot write Gallery");
            byte[] data=new byte[65536];int n;
            while((n=inputStream.read(data))!=-1)stream.write(data,0,n);
           }
           ContentValues done=new ContentValues();
           done.put(MediaStore.Video.Media.IS_PENDING,0);
           getContentResolver().update(gallery,done,null,null);
          }catch(Exception e){getContentResolver().delete(gallery,null,null);throw e;}
          output.delete();
          runOnUiThread(()->{lastExport=gallery;info.setText("9:16 Reel saved to Gallery");});
         }catch(Exception e){runOnUiThread(()->info.setText("Gallery save failed: "+e.getMessage()));}
        }).start();
       }
       @Override public void onFailure(Exception error){
        out.delete();
        runOnUiThread(()->info.setText("Reel render failed: "+error.getMessage()));
       }
      });
  }catch(Exception e){out.delete();info.setText("Cannot start Reel export: "+e.getMessage());}
 }
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
    runOnUiThread(()->{lastExport=gallery;info.setText("Video saved to Gallery: "+gallery);});
   }catch(Exception ex){runOnUiThread(()->info.setText("Export failed: "+ex.getMessage()));}
  }).start();
 }
 private String srtTime(long ms){
  long t=Math.max(0,ms);
  return String.format(Locale.US,"%02d:%02d:%02d,%03d",t/3600000,(t/60000)%60,(t/1000)%60,t%1000);
 }
 private void saveSubtitle(){
  if(selected==null){info.setText("Import a video first");return;}
  if(endMs<=startMs){info.setText("Set a valid trim range");return;}
  String words=captionInput.getText().toString().trim();
  if(words.isEmpty()){info.setText("Enter a caption first");return;}
  ContentValues values=new ContentValues();
  values.put(MediaStore.Downloads.DISPLAY_NAME,"Dreamtoon_Caption_"+System.currentTimeMillis()+".srt");
  values.put(MediaStore.Downloads.MIME_TYPE,"application/x-subrip");
  values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Dreamtoon Studio");
  values.put(MediaStore.Downloads.IS_PENDING,1);
  Uri uri=null;
  try{
   uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
   if(uri==null)throw new IllegalStateException("Cannot create subtitle file");
   String srt="1\n"+srtTime(0)+" --> "+srtTime(endMs-startMs)+"\n"+words+"\n";
   try(OutputStream stream=getContentResolver().openOutputStream(uri)){
    if(stream==null)throw new IllegalStateException("Cannot open subtitle file");
    stream.write(srt.getBytes(StandardCharsets.UTF_8));
   }
   ContentValues done=new ContentValues();
   done.put(MediaStore.Downloads.IS_PENDING,0);
   getContentResolver().update(uri,done,null,null);
   info.setText("SRT saved to Downloads/Dreamtoon Studio");
  }catch(Exception e){
   if(uri!=null)getContentResolver().delete(uri,null,null);
   info.setText("Subtitle save failed: "+e.getMessage());
  }
 }
 private boolean configureVoice(){
  if(!ttsReady||tts==null){info.setText("Text-to-Speech is not ready");return false;}
  String words=voiceScript.getText().toString().trim();
  if(words.isEmpty()){info.setText("Write AI voice text first");return false;}
  java.util.Locale locale=words.matches("(?s).*[\u0980-\u09FF].*")?new java.util.Locale("bn","BD"):java.util.Locale.ENGLISH;
  int result=tts.setLanguage(locale);
  if(result==TextToSpeech.LANG_MISSING_DATA||result==TextToSpeech.LANG_NOT_SUPPORTED){
   info.setText("This phone has no voice installed for "+locale.getDisplayLanguage());return false;
  }
  return true;
 }
 private void speakVoice(){
  if(!configureVoice())return;
  tts.speak(voiceScript.getText().toString(),TextToSpeech.QUEUE_FLUSH,null,"dreamtoon-preview");
 }
 private void saveAiVoice(){
  if(!configureVoice())return;
  File folder=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
  if(folder==null){info.setText("Audio folder unavailable");return;}
  if(!folder.exists()&&!folder.mkdirs()){info.setText("Cannot create audio folder");return;}
  File out=new File(folder,"Dreamtoon_AI_"+System.currentTimeMillis()+".wav");
  tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
   @Override public void onStart(String id){}
   @Override public void onDone(String id){runOnUiThread(()->info.setText("AI voice saved: "+out.getAbsolutePath()));}
   @Override public void onError(String id){runOnUiThread(()->info.setText("AI voice generation failed"));}
  });
  int result=tts.synthesizeToFile(voiceScript.getText().toString(),null,out,"dreamtoon-save");
  if(result!=TextToSpeech.SUCCESS)info.setText("Unable to start AI voice generation");
  else info.setText("Generating AI voice...");
 }
 private void toggleMusicPreview(){
  if(musicPlayer!=null){musicPlayer.release();musicPlayer=null;info.setText("Music preview stopped");return;}
  if(musicUri==null){info.setText("Choose an audio file first");return;}
  try{
   musicPlayer=new MediaPlayer();
   musicPlayer.setDataSource(this,musicUri);
   musicPlayer.setOnCompletionListener(mp->{mp.release();musicPlayer=null;});
   musicPlayer.prepare();
   musicPlayer.start();
   info.setText("Playing selected music (preview only)");
  }catch(Exception e){
   if(musicPlayer!=null){musicPlayer.release();musicPlayer=null;}
   info.setText("Cannot play music: "+e.getMessage());
  }
 }
 private void startVoiceRecording(){
  if(recorder!=null){info.setText("Recording already active");return;}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42);
   info.setText("Microphone permission needed. Tap Start again after allowing.");
   return;
  }
  try{
   File folder=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
   if(folder==null)throw new IllegalStateException("Music folder unavailable");
   if(!folder.exists()&&!folder.mkdirs())throw new IllegalStateException("Cannot create voice folder");
   recordedVoice=new File(folder,"Dreamtoon_Voice_"+System.currentTimeMillis()+".m4a");
   MediaRecorder next=new MediaRecorder();
   next.setAudioSource(MediaRecorder.AudioSource.MIC);
   next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
   next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
   next.setAudioEncodingBitRate(128000);
   next.setAudioSamplingRate(44100);
   next.setOutputFile(recordedVoice.getAbsolutePath());
   next.prepare();next.start();recorder=next;
   info.setText("Recording voice... Tap Stop when finished.");
  }catch(Exception ex){
   if(recorder!=null){recorder.release();recorder=null;}
   if(recordedVoice!=null)recordedVoice.delete();
   info.setText("Recording failed: "+ex.getMessage());
  }
 }
 private void stopVoiceRecording(){
  if(recorder==null){info.setText("Start recording first");return;}
  boolean success=false;
  try{recorder.stop();success=true;}catch(Exception ex){info.setText("Recording too short or failed");}
  finally{recorder.release();recorder=null;}
  if(success)info.setText("Voice saved: "+recordedVoice.getAbsolutePath()+" (not mixed into video yet)");
  else if(recordedVoice!=null)recordedVoice.delete();
 }
 @Override protected void onDestroy(){
  if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}
  progressHandler.removeCallbacks(progressTask);
  if(musicPlayer!=null){musicPlayer.release();musicPlayer=null;}
  if(tts!=null){tts.stop();tts.shutdown();tts=null;}
  super.onDestroy();
 }
 private void shareLastExport(){
  if(lastExport==null){info.setText("Export a video before sharing");return;}
  Intent share=new Intent(Intent.ACTION_SEND);
  share.setType("video/mp4");
  share.putExtra(Intent.EXTRA_STREAM,lastExport);
  share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  startActivity(Intent.createChooser(share,"Share Dreamtoon video"));
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==2&&result==RESULT_OK&&data!=null){
   if(musicPlayer!=null){musicPlayer.release();musicPlayer=null;}
   musicUri=data.getData();
   info.setText("Music selected. Tap Play / Stop Music Preview.");
  }
  if(req==1&&result==RESULT_OK&&data!=null){
   selected=data.getData();
   lastExport=null;
   startMs=0;endMs=0;seek.setProgress(0);
   video.setVideoURI(selected);video.start();
  }
 }
}