package com.aynurkacak.bullseye;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.googlecode.mp4parser.BasicContainer;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends MasterActivity implements View.OnClickListener, View.OnTouchListener {

    CameraPreview mPreview;
    ImageView ivFlash, ivFrontCam;
    Button btLibrary, btPhoto, btVideo, btNext;
    TextView tvName;
    ProgressBar pbVideo;
    ImageView ivRec;
    FrameLayout previewLayout;

    /* max video size is 15 seconds */
    ProgressTimer progressTimer; // For video time
    File mFile; // photo file
    Camera mCamera;
    MediaRecorder mMediaRecorder;
    ProgressDialog pdMerge;

    public static int LIBRARY_REQUEST = 1;
    int type = 1; // photo = 1, video = 2
    boolean isFlashOn = false;
    boolean isFrontCam = false;
    boolean isRecording = false;
    boolean isVideoRecorded = false;
    int camId = 0;
    int timerProgress = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        ivRec = (ImageView) findViewById(R.id.iv_rec);
        previewLayout = (FrameLayout) findViewById(R.id.camera_preview);
        btLibrary = (Button) findViewById(R.id.bt_library);
        btNext = (Button) findViewById(R.id.bt_next);
        tvName = (TextView) findViewById(R.id.tv_post_type);
        btPhoto = (Button) findViewById(R.id.bt_photo);
        btVideo = (Button) findViewById(R.id.bt_video);
        pbVideo = (ProgressBar) findViewById(R.id.pb_video);
        ivFlash = (ImageView) findViewById(R.id.iv_flash);
        ivFrontCam = (ImageView) findViewById(R.id.iv_front);

        ivRec.setOnClickListener(this);
        btPhoto.setOnClickListener(this);
        btVideo.setOnClickListener(this);
        btLibrary.setOnClickListener(this);
        btNext.setOnClickListener(this);
        ivFrontCam.setOnClickListener(this);
        ivFlash.setOnClickListener(this);

        ivRec.setOnTouchListener(this);

        pbVideo.setVisibility(View.GONE);
        mCamera = getCameraInstance(camId);
        mPreview = new CameraPreview(this, mCamera);
        previewLayout.addView(mPreview);

        pbVideo.setProgress(0);
        pbVideo.setMax(150);
        progressTimer = new ProgressTimer(timerProgress * 100, 100);

        pdMerge = new ProgressDialog(this);
        pdMerge.setTitle("Please Wait");
        pdMerge.setMessage("Your video is preparing...");
        pdMerge.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pdMerge.setCanceledOnTouchOutside(false);

        boolean hasFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        if (hasFlash) {
            ivFlash.setVisibility(View.VISIBLE);
        }
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            ivFrontCam.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_rec:
                if (type == 1) {
                    mCamera.takePicture(null, null, mPicture);
                }
                break;
            case R.id.bt_photo:
                if (type == 2)
                    photoClick();
                break;
            case R.id.bt_video:
                if (type == 1)
                    videoClick();
                break;
            case R.id.bt_next:
                if (type == 2) {
                    if (isVideoRecorded) {
                        if (timerProgress <= 110) {
                            new PrepareVideos(this).execute();
                        }
                        else {
                            showToast(getString(R.string.min_video));
                        }
                    }
                    else {
                        showToast(getString(R.string.content_error_str));
                    }
                }
                else {
                    new PrepareVideos(this).execute();
                }
                break;
            case R.id.bt_library:
                Intent selectIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(selectIntent, LIBRARY_REQUEST);
                break;
            case R.id.iv_front:
                if (isFrontCam) {
                    isFrontCam = false;
                    releaseCamera();
                    camId = 0;
                    mCamera = getCameraInstance(camId);
                    mPreview.refreshCamera(mCamera);
                }
                else {
                    isFrontCam = true;
                    releaseCamera();
                    camId = findFrontFacingCamera();
                    mCamera = getCameraInstance(camId);
                    mPreview.refreshCamera(mCamera);
                }
                break;
            case R.id.iv_flash:
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                    return;
                }
                if (isFlashOn) {
                    isFlashOn = false;
                    ivFlash.setSelected(false);
                    Camera.Parameters p = mCamera.getParameters();
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    mCamera.setParameters(p);
                }
                else {
                    isFlashOn = true;
                    ivFlash.setSelected(true);
                    Camera.Parameters p = mCamera.getParameters();
                    p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    mCamera.setParameters(p);
                }
                break;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (type == 2) {
                    if (!isRecording) {
                        if (prepareVideoRecorder()) {
                            mMediaRecorder.start();
                            isRecording = true;
                            progressTimer = new ProgressTimer(timerProgress * 100, 100);
                            progressTimer.start();
                            isVideoRecorded = true;
                            ivRec.setImageResource(R.drawable.rec_video_tap);
                        }
                        else {
                            releaseMediaRecorder();
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (type == 2) {
                    if (isRecording) {
                        mMediaRecorder.stop();  // stop the recording
                        releaseMediaRecorder(); // release the MediaRecorder object
                        mCamera.lock();         // take camera access back from MediaRecorder
                        isRecording = false;
                        progressTimer.cancel();
                        ivRec.setImageResource(R.drawable.rec_video);
                    }
                }
                break;
        }
        return false;
    }

    //region PHOTO VIDEO
    private void photoClick() {
        if (getFragmentManager().getBackStackEntryCount() != 0) {
            getFragmentManager().popBackStack();
            boolean hasFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
            if (hasFlash) {
                ivFlash.setVisibility(View.VISIBLE);
            }
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                ivFrontCam.setVisibility(View.VISIBLE);
            }
        }
        type = 1;
        btPhoto.setBackgroundColor(ContextCompat.getColor(this, R.color.content_bar_color));
        btVideo.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        tvName.setText(getResources().getString(R.string.photo_str));
        pbVideo.setVisibility(View.GONE);
        btNext.setVisibility(View.GONE);
        ivRec.setImageResource(R.drawable.rec_photo);
        isRecording = false;
    }

    private void videoClick() {
        if (getFragmentManager().getBackStackEntryCount() != 0) {
            getFragmentManager().popBackStack();
            boolean hasFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
            if (hasFlash) {
                ivFlash.setVisibility(View.VISIBLE);
            }
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                ivFrontCam.setVisibility(View.VISIBLE);
            }
        }
        type = 2;
        btPhoto.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        btVideo.setBackgroundColor(ContextCompat.getColor(this, R.color.content_bar_color));
        tvName.setText(getResources().getString(R.string.video_str));
        pbVideo.setVisibility(View.VISIBLE);
        timerProgress = 150;
        pbVideo.setProgress(0);
        btNext.setVisibility(View.VISIBLE);
        deleteFilesDir(getExternalFilesDir(null).getAbsolutePath());
        ivRec.setImageResource(R.drawable.rec_video);
        isRecording = false;
    }

    //endregion

    //region MEDIA RECORDER
    private boolean prepareVideoRecorder() {
        if (mCamera == null) {
            releaseCamera();
            mCamera = getCameraInstance(camId);
            Camera.Parameters params = mCamera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                Log.i("video", Build.MODEL);
                if (((Build.MODEL.startsWith("GT-I950")) || (Build.MODEL.endsWith("SCH-I959"))
                        || (Build.MODEL.endsWith("MEIZU MX3"))) && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                }
                else if ((Build.MODEL.startsWith("GT"))) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
                else
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }
            mCamera.setParameters(params);
            mCamera.setDisplayOrientation(90);
        }

        mMediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);
        if (isFrontCam) {
            mMediaRecorder.setOrientationHint(270);
        }
        else
            mMediaRecorder.setOrientationHint(90);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(CamcorderProfile.get(camId, CamcorderProfile.QUALITY_HIGH));
        mMediaRecorder.setOutputFile(getVideoFile(this).toString());
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d("", "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d("", "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    //endregion

    private int findFrontFacingCamera() {
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                Log.d("", "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    //region PICTURE
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = null;
            try {
                pictureFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mFile = pictureFile;
            if (pictureFile == null) {
                return;
            }
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d("", "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d("", "Error accessing file: " + e.getMessage());
            }

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            Bitmap bm = BitmapFactory.decodeFile(pictureFile.getPath(), bmOptions);
            Matrix matrix = new Matrix();
            if (isFrontCam) {
                float[] mirrorY = {-1, 0, 0, 0, 1, 0, 0, 0, 1};
                Matrix matrixMirrorY = new Matrix();
                matrixMirrorY.setValues(mirrorY);
                matrix.postConcat(matrixMirrorY);
                matrix.preRotate(270);
            }
            else {
                matrix.setRotate(90);
            }
            try {
                Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                byte[] byteArray = stream.toByteArray();
                FileOutputStream fos = null;
                fos = new FileOutputStream(pictureFile);
                fos.write(byteArray);
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
            intent.putExtra("video_path", "");
            intent.putExtra("image_path", pictureFile.getPath());
            startActivity(intent);
            mPreview.refreshCamera(mCamera);
        }
    };

    public static Camera getCameraInstance(int cameraId) {
        Camera c = null;
        try {
            c = Camera.open(cameraId);
        } catch (Exception e) {
            Log.e("camera instance", e.getMessage());
        }
        return c;
    }

    //endregion

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LIBRARY_REQUEST) {
            if (resultCode == RESULT_OK) {
                //region GALERY
                Uri selectedImageUri = data.getData();
                try {
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};
                    Cursor cursor = getContentResolver().query(selectedImageUri, filePathColumn, null, null, null);
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String filePath = cursor.getString(columnIndex);
                    cursor.close();

                    Bitmap yourSelectedImage = BitmapFactory.decodeFile(filePath);
                    Bitmap scaled = scaleDown(yourSelectedImage, 480, true);
                    File imageFile = createImageFile();
                    if (imageFile == null) {
                        Log.d("", "Error creating media file, check storage permissions: ");
                        return;
                    }
                    try {
                        FileOutputStream fos = new FileOutputStream(imageFile);
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, fos);
                        fos.close();

                        Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
                        intent.putExtra("video_path", "");
                        intent.putExtra("image_path", imageFile.getPath());
                        startActivity(intent);
                        finish();

                    } catch (FileNotFoundException e) {
                        Log.d("", "File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.d("", "Error accessing file: " + e.getMessage());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //endregion
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCamera == null) {
            mCamera = getCameraInstance(camId);
            mPreview.refreshCamera(mCamera);
            timerProgress = 150;
            pbVideo.setProgress(0);
        }
    }

    private void deleteFilesDir(String path) {
        File dir = new File(path);
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                new File(dir, children[i]).delete();
            }
        }
    }

    public class ProgressTimer extends CountDownTimer {

        public ProgressTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long l) {
            int progress = (int) (l / 100);
            pbVideo.setProgress(pbVideo.getMax() - progress);
            timerProgress = progress;
        }

        @Override
        public void onFinish() {
            pbVideo.setProgress(pbVideo.getMax());
            if (isRecording) {
                mMediaRecorder.stop();  // stop the recording
                releaseMediaRecorder(); // release the MediaRecorder object
                mCamera.lock();         // take camera access back from MediaRecorder
                isRecording = false;
            }
            new PrepareVideos(MainActivity.this).execute();
        }
    }

    class PrepareVideos extends AsyncTask {
        Activity activity;

        public PrepareVideos(Activity activity) {
            this.activity = activity;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pdMerge.show();
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            mergeVideos();
            pdMerge.dismiss();
            return null;
        }

        public void mergeVideos() {
            try {
                File parentDir = new File(getExternalFilesDir(null).getAbsolutePath());
                List<String> videosPathList = new ArrayList<>();
                File[] files = parentDir.listFiles();
                for (File file : files) {
                    videosPathList.add(file.getAbsolutePath());
                }

                List<Movie> inMovies = new ArrayList<>();
                for (int i = 0; i < videosPathList.size(); i++) {
                    String filePath = videosPathList.get(i);
                    try {
                        Movie movie = MovieCreator.build(filePath);
                        if (movie != null)
                            inMovies.add(movie);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                List<Track> videoTracks = new LinkedList<Track>();
                List<Track> audioTracks = new LinkedList<Track>();
                for (Movie m : inMovies) {
                    for (Track t : m.getTracks()) {
                        try {
                            if (t.getHandler().equals("soun")) {
                                audioTracks.add(t);
                            }
                            if (t.getHandler().equals("vide")) {
                                videoTracks.add(t);
                            }
                        } catch (Exception e) {

                        }
                    }
                }
                Movie result = new Movie();
                if (audioTracks.size() > 0) {
                    result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
                }
                if (videoTracks.size() > 0) {
                    result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
                }
                BasicContainer out = (BasicContainer) new DefaultMp4Builder().build(result);
                File f = null;
                String finalVideoPath;
                try {
                    f = setUpVideoFile(Environment.getExternalStorageDirectory() + "/Bullseye/videos/");
                    finalVideoPath = f.getAbsolutePath();

                } catch (IOException e) {
                    e.printStackTrace();
                    f = null;
                    finalVideoPath = null;
                }
                WritableByteChannel fc = new RandomAccessFile(finalVideoPath, "rw").getChannel();
                out.writeContainer(fc);
                fc.close();
                deleteFilesDir(getExternalFilesDir(null).getAbsolutePath());

                Bitmap thumb = ThumbnailUtils.createVideoThumbnail(finalVideoPath, MediaStore.Images.Thumbnails.MINI_KIND);
                String filename = createImageFile().getAbsolutePath();
                FileOutputStream bitmapOut = null;
                try {
                    bitmapOut = new FileOutputStream(filename);
                    thumb.compress(Bitmap.CompressFormat.PNG, 100, bitmapOut);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (bitmapOut != null) {
                            bitmapOut.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                Intent intent = new Intent(activity, PreviewActivity.class);
                intent.putExtra("video_path", finalVideoPath);
                intent.putExtra("image_path", filename);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();

                finish();
            }
        }

        File setUpVideoFile(String directory) throws IOException {
            File videoFile = null;
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                File storageDir = new File(directory);
                if (storageDir != null) {
                    if (!storageDir.mkdirs()) {
                        if (!storageDir.exists()) {
                            Log.d("CameraSample", "failed to create directory");
                            return null;
                        }
                    }
                }
                videoFile = File.createTempFile("video_" + System.currentTimeMillis() + "_", ".mp4", storageDir);
            }
            return videoFile;
        }

        private void deleteFilesDir(String path) {
            File dir = new File(path);
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (int i = 0; i < children.length; i++) {
                    new File(dir, children[i]).delete();
                }
            }
        }
    }

}
