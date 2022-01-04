package com.example.cameraonedemo.activity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.annotation.SuppressLint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.example.cameraonedemo.camera.api1.CameraContext;
import com.example.cameraonedemo.camera.api1.CameraInfo;
import com.example.cameraonedemo.R;
import com.example.cameraonedemo.camera.common.BaseCameraContext;
import com.example.cameraonedemo.encoder.VideoEncoder;
import com.example.cameraonedemo.utils.AutoFitSurfaceView;
import com.example.cameraonedemo.utils.CameraUtils;
import com.example.cameraonedemo.utils.Mp4ToNV21;
import com.example.cameraonedemo.view.FaceView;
import com.example.cameraonedemo.view.FocusMeteringView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera1Activity extends BaseActivity
        implements SurfaceHolder.Callback, View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int MSG_CANCEL_AUTO_FOCUS = 1000;
    private static final int MSG_UPDATE_RECORDING_STATUS = 1001;
    private static final int MSG_UPDATE_CODEC_STATUS = 1002;
    private static final int MSG_TOUCH_AF_LOCK_TIME_OUT = 5000;
    ///////
    private int mNV21Index = 0;
    private List<String> mRelNV21List;
    public static Mp4ToNV21 mMp4Obj;
    //////

    private AutoFitSurfaceView mSurfaceView;
    private ImageView mPictureImageView;
    private Button mRecordBtn;
    private Button mCodecBtn;
    private Button mShowBtn;
    private Button flashOptionalBtn;
    private FocusMeteringView mFocusMeteringView;
    private FaceView mFaceView;
    private VideoEncoder mVideoEncoder;

    private CameraContext mCameraContext;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private int mCurrentCameraIdType = Camera.CameraInfo.CAMERA_FACING_BACK;
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private MainHandler mMainHandler = new MainHandler(Looper.getMainLooper());

    private OnTouchEventListener mOnTouchEventListener = new OnTouchEventListener() {
        @Override
        public void onScale(float scaleFactor) {

        }

        @Override
        public void onSingleTapUp(final float x, final float y) {
            int focusW = mFocusMeteringView.getWidth();
            int focusH = mFocusMeteringView.getHeight();
            Log.d(TAG, "onSingleTapUp: x = " + x + ", y = " + y
                    + ", focusW = " + focusW + ", focusH = " + focusH);
            mFocusMeteringView.show();
            mFocusMeteringView.setCenter(x, y);
            mFocusMeteringView.setColor(Color.WHITE);

            mMainHandler.removeMessages(MSG_CANCEL_AUTO_FOCUS);
            mMainHandler.sendEmptyMessageDelayed(MSG_CANCEL_AUTO_FOCUS, MSG_TOUCH_AF_LOCK_TIME_OUT);
            final boolean isMirror = mCurrentCameraIdType == Camera.CameraInfo.CAMERA_FACING_FRONT;
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        mCameraContext.onTouchAF(x, y, 200, 200, mPreviewWidth, mPreviewHeight, isMirror);
                    }
                }
            });
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(this);

        mPictureImageView = findViewById(R.id.picture_image_view);
        mPictureImageView.setOnClickListener(this);

        findViewById(R.id.switch_btn).setOnClickListener(this);
        findViewById(R.id.capture_btn).setOnClickListener(this);

        flashOptionalBtn = findViewById(R.id.flash_optional_btn);
        flashOptionalBtn.setOnClickListener(this);

        mCodecBtn = findViewById(R.id.codec_btn);
        mCodecBtn.setOnClickListener(this);

        mRecordBtn = findViewById(R.id.record_btn);
        mRecordBtn.setOnClickListener(this);

        mShowBtn = findViewById(R.id.show_btn);
        mShowBtn.setOnClickListener(this);

        mFocusMeteringView = findViewById(R.id.focus_metering_view);
        mFaceView = findViewById(R.id.face_view);

        mCameraContext = new CameraContext(this);
        mMp4Obj = new Mp4ToNV21(Environment.getExternalStorageDirectory().getAbsolutePath() + "/rel.mp4", getCacheDir().getAbsolutePath());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRelNV21List = CameraUtils.getEnumDirFileName(Environment.getExternalStorageDirectory().getAbsolutePath() + "/rel/", null);

        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mCameraContext.resume();
                mCameraContext.setFocusStatusCallback(new BaseCameraContext.FocusStatusCallback() {
                    @Override
                    public void onAutoFocus(boolean success) {
                        mFocusMeteringView.setColor(success ? Color.GREEN : Color.RED);
                    }

                    @Override
                    public void onAutoFocusMoving(boolean start) {
                        Log.d(TAG, "onAutoFocusMoving: " + start);
//                        mFocusMeteringView.reset();
//                        mFocusMeteringView.setColor(start ? Color.WHITE : Color.GREEN);
                    }
                });
            }
        });
        mCameraContext.setFaceDetectionListener(new BaseCameraContext.FaceDetectionListener() {
            @Override
            public void onFaceDetection(Rect[] faces) {
                mFaceView.setFaces(faces, mCameraContext.isFront(), mCameraContext.getDisplayOrientation());
            }
        });
        setOnTouchEventListener(mOnTouchEventListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mCameraContext.closeCamera();
                mCameraContext.pause();
                mCameraContext.setFocusStatusCallback(null);
            }
        });
        mMainHandler.removeCallbacksAndMessages(null);
        mCameraContext.setFaceDetectionListener(null);
        setOnTouchEventListener(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        final SurfaceHolder surfaceHolder = holder;
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                mCameraContext.configSurfaceHolder(surfaceHolder);
                mCameraContext.openCamera(mCurrentCameraIdType);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mPreviewWidth = width;
        mPreviewHeight = height;
        Log.d(TAG, "surfaceChanged: w = " + width + ", h = " + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed: ");
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.switch_btn) {
            if (mCurrentCameraIdType == CameraInfo.CAMERA_FACING_BACK) {
                mCurrentCameraIdType = CameraInfo.CAMERA_FACING_FRONT;
            } else {
                mCurrentCameraIdType = CameraInfo.CAMERA_FACING_BACK;
            }

            mFocusMeteringView.reset();
            mFocusMeteringView.hide();
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        mCameraContext.switchCamera(mCurrentCameraIdType);
                    }
                }
            });
            mFaceView.clear();
        } else if (v.getId() == R.id.capture_btn) {
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        mCameraContext.capture(new CameraContext.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] data) {
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (bitmap == null) {
                                    return;
                                }
                                mPictureImageView.setImageBitmap(bitmap);
                                mPictureImageView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            });
        } else if (v.getId() == R.id.picture_image_view) {
            mPictureImageView.setVisibility(View.INVISIBLE);
        } else if (v.getId() == R.id.record_btn) {
            Log.e(TAG, "onClick: record_btn");
            mExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (mCameraContext != null) {
                        if (mCameraContext.isRecording()) {
                            mCameraContext.stopRecord();
                        } else {
                            mCameraContext.startRecord();
                        }
                        final boolean isRecording = mCameraContext.isRecording();
                        mMainHandler.sendMessage(mMainHandler.obtainMessage(MSG_UPDATE_RECORDING_STATUS, isRecording));
                    }
                }
            });
        } else if (v.getId() == R.id.codec_btn) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                if (mVideoEncoder == null) {
                    mVideoEncoder = new VideoEncoder(mCameraContext.getPreviewWidth(), mCameraContext.getPreviewHeight());
                }

                if (!mVideoEncoder.isStart()) {
                    mCameraContext.setPreviewCallback(new CameraContext.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data) {
                            mVideoEncoder.addVideoData(data);

                            //////自己添加的数据用于替换,读取目录的
//                            int beishu = 3;  //放慢几倍
//                            mNV21Index += 1;
//                            if(mNV21Index / beishu > (mRelNV21List.size() -1 )){
//                                mNV21Index = 0;
//                            }
//                            else{
//                                byte[] newdata  = CameraUtils.readFileToByteArray(mRelNV21List.get(mNV21Index / beishu));
//                                Log.e("mlog", "addVideoData, onPreviewFrame" + ",newdata:" +mRelNV21List.get(mNV21Index / beishu));
//                                Bitmap bitmap = CameraUtils.getPriviewPic(newdata, 1920, 1080);
//                                mPictureImageView.setImageBitmap(bitmap);
//                                mPictureImageView.setVisibility(View.VISIBLE);
//                            }


                        }
                    });
                    mVideoEncoder.setVideoEncodeListener(new VideoEncoder.VideoEncodeListener() {
                        @Override
                        public void onVideoEncodeStart() {
                            Log.d(TAG, "onVideoEncodeStart: ");
                            mMainHandler.sendMessage(mMainHandler.obtainMessage(MSG_UPDATE_CODEC_STATUS, true));
                        }

                        @Override
                        public void onVideoEncodeEnd() {
                            mMainHandler.sendMessage(mMainHandler.obtainMessage(MSG_UPDATE_CODEC_STATUS, false));
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mVideoEncoder.release();
                                    mVideoEncoder = null;
                                }
                            });
                            Log.d(TAG, "onVideoEncodeEnd: ");
                        }
                    });
                    mVideoEncoder.start();
                } else {
                    mCameraContext.setPreviewCallback(null);
                    mVideoEncoder.stop();
                }
            }
        }else if (v.getId() == R.id.show_btn) {
            mCameraContext.setPreviewCallback(new CameraContext.PreviewCallback(){

                @Override
                public void onPreviewFrame(byte[] data) {

                    ////分析 MP4录像并将data数据替换掉,用于一些后面视频处理hook 用
                    if(mMp4Obj.isTransformation()){
                        int  rate = (int) (30 / mMp4Obj.mFrameRate);  //放慢几倍
                        Log.e("mlog", "addVideoData, onPreviewFrame" + ",mMp4Obj:" + mMp4Obj.toString() + ",rate:" + rate);
                        mNV21Index += 1;
                        if(mNV21Index / rate > (mMp4Obj.mNV21PathList.size() -1 )){
                            mNV21Index = 0;
                        }
                        else{
                            byte[] newdata  =CameraUtils.readFileToByteArray(mMp4Obj.mNV21PathList.get(mNV21Index / rate));
                            Log.e("mlog", "addVideoData, onPreviewFrame" + ",newdata:" + (mNV21Index / rate));
                            Bitmap bitmap = CameraUtils.getPriviewPic(newdata, mMp4Obj.mWidth, mMp4Obj.mHight);
                            mPictureImageView.setImageBitmap(bitmap);
                            mPictureImageView.setVisibility(View.VISIBLE);
                        }
                    }
                    else {
                        Bitmap bmp = CameraUtils.getPriviewPic(data, mCameraContext.getPreviewWidth(), mCameraContext.getPreviewHeight());
                        mPictureImageView.setImageBitmap(bmp);
                        mPictureImageView.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
        else if (v.getId() == R.id.flash_optional_btn) {
            if (mCameraContext != null) {
                String text = flashOptionalBtn.getText().toString();
                int index = Arrays.asList(FLASH_OPTIONAL_SET).indexOf(text);
                index = (index + 1) % FLASH_OPTIONAL_SET.length;
                text = FLASH_OPTIONAL_SET[index];
                flashOptionalBtn.setText(text);
                mCameraContext.switchFlashMode(text);
            }
        }
    }

    private class MainHandler extends Handler {

        MainHandler(Looper looper) {
            super((looper));
        }

        @Override
        public void dispatchMessage(@NonNull Message msg) {
            super.dispatchMessage(msg);
            switch (msg.what) {
                case MSG_CANCEL_AUTO_FOCUS:
                    if (mCameraContext != null) {
                        mCameraContext.cancelAutoFocus();
                        mCameraContext.enableCaf();
                        mFocusMeteringView.hide();
                    }
                    break;
                case MSG_UPDATE_RECORDING_STATUS:
                    boolean isRecording = (Boolean) msg.obj;
                    Log.e(TAG, "dispatchMessage: MSG_UPDATE_RECORDING_STATUS isRecording = " + isRecording);
                    mRecordBtn.setText(isRecording ? "结束" : "录像");
                    break;
                case MSG_UPDATE_CODEC_STATUS:
                    boolean isCodec = (Boolean) msg.obj;
                    Log.e(TAG, "dispatchMessage: MSG_UPDATE_CODEC_STATUS isCodec = " + isCodec);
                    mCodecBtn.setText(isCodec ? "结束" : "硬编");
                    break;
            }
        }
    }


}