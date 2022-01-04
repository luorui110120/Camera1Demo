package com.example.cameraonedemo.utils;

import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Mp4ToNV21 implements VideoToFrames.Callback {
    public boolean mTransformationEnd = false;
    public int mWidth;
    public int mHight;
    public long mFrameRate;
    public long mCount;
    public int mRotation;
    public long mDuration;
    public float mRate;
    public List<String> mNV21PathList;
    public Mp4ToNV21(String inpath, String outdir){
        mNV21PathList = new ArrayList<String>();
        getMp4Info(inpath);
        VideoToFrames videoToFrames = new VideoToFrames();
        videoToFrames.setCallback(this);
        try {
            videoToFrames.setSaveFrames(outdir, VideoToFrames.COLOR_FormatNV21);
            videoToFrames.decode(inpath);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void getMp4Info(String inpath){
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(inpath);
        //获取视频时长，单位：毫秒(ms)
        String duration_s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        mDuration = Long.valueOf(duration_s);
        Log.e("mlog","mDuration:"+duration_s);

////获取视频帧数
//        String count_s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
//        mCount = Long.valueOf(count_s);
//
////计算帧率
//        long dt = mDuration/mCount; // 平均每帧的时间间隔，35ms
//        mFrameRate = (long) (1000/dt); // 帧率

//获取帧率（有时返回值为 null）
//        String rate_s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE); //获取视频帧率
//        mRate = Float.valueOf(rate_s);

//获取 MIME
        String mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);

//获取视频方向（0、90、180、270）
        String rotation_s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        mRotation = Integer.parseInt(rotation_s);

//获取视频宽度（单位：px）
        String width_s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        mWidth = Integer.valueOf(width_s);

//获取视频高度（单位：px）
        String hight_s = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        mHight = Integer.valueOf(hight_s);
    }
    @Override
    public void onFinishDecode() {
        Log.e("mlog", "decodeSuccess!!");
        mTransformationEnd = true;
        mFrameRate = 1000 / (mDuration / mCount);
    }

    @Override
    public void onDecodeFrame(int index, String filepath) {
        Log.e("mlog", "decodeIndex:" + index);
        mCount = index;
        ////这里使用落地文件的方式, 如果直接使用 byte数组会造成大量空间并且程序会oom;
        mNV21PathList.add(filepath);
    }

    public boolean isTransformation(){
        return mTransformationEnd;
    }
    public String toString(){
        return String.format("mFrameRate:%d, width:%d, higth:%d,cunt:%d, duration:%d", mFrameRate,
                mWidth,mHight,mCount,mDuration);
    }
}
