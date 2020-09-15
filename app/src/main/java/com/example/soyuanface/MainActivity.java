package com.example.soyuanface;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.soyuanface.view.OpencvTest;


import java.util.ArrayList;
import java.util.List;

/**
 * @author SoYuan
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };
    private static final int REQUEST_CODE = 1;
    private List<String> mMissPermissions = new ArrayList<>();
    private int currTab = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button faceDetect = (Button) findViewById(R.id.face_detect);
        faceDetect.setOnClickListener(this);

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void checkAndRequestPermissions() {
        mMissPermissions.clear();
        for (String permission : REQUIRED_PERMISSION_LIST) {
            int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                mMissPermissions.add(permission);
            }
        }
        // check permissions has granted
        if (mMissPermissions.isEmpty()) {
            startMainActivity();
        } else {
            ActivityCompat.requestPermissions(this,
                    mMissPermissions.toArray(new String[mMissPermissions.size()]),
                    REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    mMissPermissions.remove(permissions[i]);
                }
            }
        }
        // Get permissions success or not
        if (mMissPermissions.isEmpty()) {
            startMainActivity();
        } else {
            Toast.makeText(MainActivity.this, "get permissions failed,exiting...", Toast.LENGTH_SHORT).show();
            MainActivity.this.finish();
        }
    }

    private void startMainActivity() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    switch (currTab) {
                        case R.id.face_detect:
                            startActivity(new Intent(MainActivity.this, OpencvTest.class));
                            break;
                        default:
                    }

                    MainActivity.this.finish();
                }
            }, 300);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.face_detect:
                currTab = R.id.face_detect;
                break;
            default:
        }

        checkAndRequestPermissions();
    }
}