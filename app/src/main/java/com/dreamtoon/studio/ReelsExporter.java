package com.dreamtoon.studio;

import android.content.Context;
import android.net.Uri;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Effect;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.TextOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Transcodes an MP4 into a vertical canvas and optionally burns in a static caption. */
public final class ReelsExporter {
 private ReelsExporter(){}
 public interface Callback {
  void onSuccess(File output);
  void onFailure(Exception error);
 }
 public static Transformer export(Context context,Uri input,File output,long startMs,long endMs,
     String caption,Callback callback){
  MediaItem.ClippingConfiguration clip=new MediaItem.ClippingConfiguration.Builder()
      .setStartPositionMs(startMs).setEndPositionMs(endMs).build();
  MediaItem media=new MediaItem.Builder().setUri(input).setClippingConfiguration(clip).build();
  List<Effect> videoEffects=new ArrayList<>();
  videoEffects.add(Presentation.createForAspectRatio(9f/16f,Presentation.LAYOUT_SCALE_TO_FIT));
  if(caption!=null&&!caption.trim().isEmpty()){
   SpannableString text=new SpannableString(caption.trim());
   text.setSpan(new ForegroundColorSpan(Color.WHITE),0,text.length(),0);
   videoEffects.add(new OverlayEffect(Collections.singletonList(
      TextOverlay.createStaticTextOverlay(text))));
  }
  EditedMediaItem item=new EditedMediaItem.Builder(media)
      .setEffects(new Effects(Collections.emptyList(),videoEffects)).build();
  Transformer transformer=new Transformer.Builder(context)
      .addListener(new Transformer.Listener(){
       @Override public void onCompleted(Composition composition,ExportResult result){
        callback.onSuccess(output);
       }
       @Override public void onError(Composition composition,ExportResult result,ExportException error){
        callback.onFailure(error);
       }
      }).build();
  transformer.start(item,output.getAbsolutePath());
  return transformer;
 }
}
