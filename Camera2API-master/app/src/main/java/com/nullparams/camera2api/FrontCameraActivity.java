package com.nullparams.camera2api;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static android.os.Environment.DIRECTORY_PICTURES;

public class FrontCameraActivity extends AppCompatActivity{

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private Integer mSensorOrientation;
    private ColorFragment colorFragment;
    private Map<String, Integer> colorMap;
    private int iterations;
    private Random random;

    private Runnable colorChangeRunnable;
    private Handler handler;
    private final int delay = 1000; //milliseconds
    private String filename;

    private File videoFile;
    private File textFile;
    private BufferedWriter writer;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private TextureView textureView;
    private Button button;

    private int cameraType=1;

    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;

    public FrontCameraActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_front_camera);

        textureView = findViewById(R.id.tvRecordVideo);
        button = findViewById(R.id.btnRecord);

        handler = new Handler();
        colorFragment = new ColorFragment();
        random = new Random();
        textureView.setSurfaceTextureListener(textureListener);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsRecordingVideo) {
                    stopRecordingVideo();
                } else {
                    startRecordingVideo();
                }
            }
        });
    }
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(cameraType);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void openCamera(int cameraType) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e("tag", "is camera open");
        try {
            String cameraId = manager.getCameraIdList()[cameraType];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED  &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED  ) {
                ActivityCompat.requestPermissions(FrontCameraActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e("tag", "openCamera X");
    }
    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {

            Log.e("tag", "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(FrontCameraActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e("tag", "updatePreview error, return");
        }
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecordingVideo() {
        if (null == cameraDevice || !textureView.isAvailable() || null == imageDimension) {
            return;
        }
        try {
            colorMap = createRandomColorMap();
            iterations = 4;
            closePreviewSession();

            //A new file must be created before setting up the output file on setUpMediaRecorder
            filename = String.valueOf(System.currentTimeMillis());
            videoFile = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES) + "/" + filename + ".MP4");
            textFile = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES) + "/" + filename + ".txt");
            writer = new BufferedWriter(new FileWriter(textFile));

            setUpMediaRecorder();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            captureRequestBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface mRecorderSurface = mMediaRecorder.getSurface();
            surfaces.add(mRecorderSurface);
            captureRequestBuilder.addTarget(mRecorderSurface);

            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCapture) {
                    cameraCaptureSessions = cameraCapture;
                    updatePreview();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            button.setText("Stop");
                            button.setBackgroundColor(Color.TRANSPARENT);
                            mIsRecordingVideo = true;

                            FragmentManager fragmentManager = getSupportFragmentManager();
                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                            fragmentTransaction.add(R.id.activity_main, colorFragment);
                            fragmentTransaction.commit();

                            colorChangeRunnable = new Runnable(){
                                public void run(){
                                    try{
                                        if(colorFragment.isAdded() && iterations >= 0){
                                            if(iterations == 0){
                                                writer.write("WHITE");
                                                colorFragment.getActivity().findViewById(R.id.full_screen_layout).setBackgroundColor(Color.WHITE);
                                            }else if(iterations == 1){
                                                writer.write("BLACK" + "_");
                                                colorFragment.getActivity().findViewById(R.id.full_screen_layout).setBackgroundColor(Color.BLACK);
                                            }else{
                                                String key = getRandomColorKey(colorMap);
                                                assert key != null;
                                                Integer newColor = colorMap.get(key);
                                                assert newColor != null;
                                                colorMap.remove(key);
                                                writer.write(key + "_");
                                                colorFragment.getActivity().findViewById(R.id.full_screen_layout).setBackgroundColor(newColor);
                                            }
                                            handler.postDelayed(this, delay);
                                            iterations--;
                                        }else{
                                            handler.removeCallbacksAndMessages(null);
                                            if(mIsRecordingVideo){
                                                stopRecordingVideo();
                                            }
                                        }
                                    }catch (IOException e){
                                        Toast.makeText(getBaseContext(), "Problem in writting text file", Toast.LENGTH_LONG).show();
                                    }
                                }
                            };
                            handler.postDelayed(colorChangeRunnable, delay);
                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                    Toast.makeText(FrontCameraActivity.this, "Failed", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    private String getRandomColorKey(Map<String, Integer> colors){
        if(colors.size() > 0){
            List<String> colorKeys = new ArrayList<>(colors.keySet());
            return colorKeys.get(random.nextInt(colorKeys.size()));
        }
        return null;
    }

    private Map<String, Integer> createRandomColorMap(){
        Map<String, Integer> colors = new HashMap<>();
        colors.put("RED", Color.RED);
        colors.put("GREEN", Color.GREEN);
        colors.put("YELLOW", Color.YELLOW);
        colors.put("BLUE", Color.BLUE);
        colors.put("ORANGE", 0xFFFF7F00);
        colors.put("INDIGO", 0xFF4B0082);
        colors.put("VIOLET", 0xFF8F00FF);
        return colors;
    }

    private void setUpMediaRecorder() throws IOException {
        WindowManager wm = (WindowManager) getBaseContext().getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(videoFile.toString());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoSize(size.y, size.x);
        int rotation =getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }
    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        button.setText("Start");
        button.setBackgroundColor(Color.GREEN);
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        if(colorFragment.isAdded()){
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.remove(colorFragment);
            fragmentTransaction.commit();
        }

        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Toast.makeText(FrontCameraActivity.this, "Video saved: " + videoFile.toString(),
                Toast.LENGTH_SHORT).show();
        createCameraPreview();
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void closePreviewSession() {
        if (cameraCaptureSessions != null) {
            cameraCaptureSessions.close();
            cameraCaptureSessions = null;
        }
    }
    private void closeCamera() {
        if (null != cameraCaptureSessions) {
            cameraCaptureSessions.close();
            cameraCaptureSessions = null;
        }
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.e("tag", "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(cameraType);
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.e("tag", "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(FrontCameraActivity.this, "You can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
