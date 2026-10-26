package com.dreamtoon.studio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import java.nio.ByteBuffer;
import java.io.File;

/** Keyframe-aligned, no-reencode MP4 trimming. Does not mix audio or burn captions. */
public final class VideoEngine {
  private VideoEngine() {}
  public static void trim(Context context, Uri input, File output, long startMs, long endMs) throws Exception {
    if(startMs<0||endMs<=startMs) throw new IllegalArgumentException("Choose a valid trim range");
    MediaExtractor extractor=new MediaExtractor();
    MediaMuxer muxer=null;
    try {
      extractor.setDataSource(context,input,null);
      int video=-1;
      for(int i=0;i<extractor.getTrackCount();i++){
        String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
        if(mime!=null&&mime.startsWith("video/")){video=i;break;}
      }
      if(video<0)throw new IllegalArgumentException("No video track");
      extractor.selectTrack(video);
      extractor.seekTo(startMs*1000,MediaExtractor.SEEK_TO_NEXT_SYNC);
      long origin=extractor.getSampleTime();
      extractor.unselectTrack(video);
      if(origin<0||origin>=endMs*1000)throw new IllegalArgumentException("No keyframe within selected range");
      muxer=new MediaMuxer(output.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      MediaFormat vf=extractor.getTrackFormat(video);
      int rotation=vf.containsKey(MediaFormat.KEY_ROTATION_DEGREES)?vf.getInteger(MediaFormat.KEY_ROTATION_DEGREES):0;
      if(rotation==90||rotation==180||rotation==270)muxer.setOrientationHint(rotation);
      int[] outputTracks=new int[extractor.getTrackCount()];
      for(int i=0;i<outputTracks.length;i++){
        MediaFormat f=extractor.getTrackFormat(i);
        String mime=f.getString(MediaFormat.KEY_MIME);
        if(mime!=null&&(mime.startsWith("video/")||mime.startsWith("audio/"))){
          outputTracks[i]=muxer.addTrack(f);
        }else outputTracks[i]=-1;
      }
      muxer.start();
      MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      ByteBuffer buffer=ByteBuffer.allocateDirect(16*1024*1024);
      for(int track=0;track<outputTracks.length;track++){
        if(outputTracks[track]<0)continue;
        extractor.selectTrack(track);
        extractor.seekTo(origin,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        long previous=-1;
        while(true){
          buffer.clear();
          int size=extractor.readSampleData(buffer,0);
          long pts=extractor.getSampleTime();
          if(size<0||pts<0||pts>=endMs*1000)break;
          if(pts<origin){extractor.advance();continue;}
          if(size>=buffer.capacity())throw new IllegalArgumentException("Sample too large");
          long outPts=pts-origin;
          if(outPts<=previous){extractor.advance();continue;}
          info.set(0,size,outPts,extractor.getSampleFlags());
          muxer.writeSampleData(outputTracks[track],buffer,info);
          previous=outPts;
          extractor.advance();
        }
        extractor.unselectTrack(track);
      }
      muxer.stop();
      muxer.release();muxer=null;
    } finally {
      extractor.release();
      if(muxer!=null){try{muxer.stop();}catch(Exception ignored){}muxer.release();}
    }
  }
}
