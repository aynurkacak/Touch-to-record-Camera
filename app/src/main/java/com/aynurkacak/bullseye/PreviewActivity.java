package com.aynurkacak.bullseye;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.VideoView;

import java.io.File;

/**
 * Created by aynurkacak on 09/08/16.
 */
public class PreviewActivity extends Activity implements View.OnClickListener {
    String videoPath, imagePath;
    ImageView ivPlay, ivImage;
    Button btDelete;
    VideoView vvContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        videoPath = getIntent().getStringExtra("video_path");
        imagePath = getIntent().getStringExtra("image_path");
        init();
    }

    private void init() {
        ivPlay = (ImageView) findViewById(R.id.previre_play);
        btDelete = (Button) findViewById(R.id.bt_cancel);
        ivImage = (ImageView) findViewById(R.id.iv_image);
        vvContent = (VideoView) findViewById(R.id.vv_content);

        ivPlay.setOnClickListener(this);
        btDelete.setOnClickListener(this);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
        ivImage.setImageBitmap(bitmap);

        if (videoPath.equals("")) {
            ivPlay.setVisibility(View.GONE);
            vvContent.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_cancel:
                if (vvContent.isPlaying()) {
                    vvContent.stopPlayback();
                }
                File file = new File(videoPath);
                file.delete();
                file = new File(imagePath);
                file.delete();
                this.finish();
                break;
            case R.id.previre_play:
                ivPlay.setVisibility(View.GONE);
                ivImage.setVisibility(View.GONE);
                vvContent.setVideoPath(videoPath);
                vvContent.start();
                break;
        }
    }
}
