package com.dreamtoon.studio;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
public class MainActivity extends Activity {
 private VideoView video; private TextView info; private Uri selected;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this); root.setOrientation(1);root.setPadding(20,30,20,20);
  TextView title=new TextView(this);title.setText("Dreamtoon Studio");title.setTextSize(26);root.addView(title);
  Button importButton=new Button(this);importButton.setText("Import Video");root.addView(importButton);
  info=new TextView(this);info.setText("Choose a video to preview");root.addView(info);
  video=new VideoView(this);root.addView(video,new LinearLayout.LayoutParams(-1,0,1));
  Button play=new Button(this);play.setText("Play / Pause");root.addView(play);
  SeekBar seek=new SeekBar(this);root.addView(seek);
  TextView note=new TextView(this);note.setText("Prototype: import and preview only. Editing/export coming later.");root.addView(note);
  setContentView(root);
  importButton.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,1);});
  play.setOnClickListener(v->{if(selected==null)return;if(video.isPlaying())video.pause();else video.start();});
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&selected!=null)video.seekTo(p);}
   public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
  });
  video.setOnPreparedListener(mp->{seek.setMax(video.getDuration());info.setText("Duration: "+video.getDuration()/1000+" seconds");});
 }
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==1&&result==RESULT_OK&&data!=null){selected=data.getData();video.setVideoURI(selected);video.start();}}
}