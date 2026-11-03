package com.liskovsoft.smartyoutubetv2.common.misc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

public class BackupReceiverActivity extends ComponentActivity {
    private BackupAndRestoreHelper mRestoreHelper;
    private final ActivityResultLauncher<String> mStoragePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    restoreData();
                }
                finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRestoreHelper = new BackupAndRestoreHelper(this);

        Intent intent = getIntent();
        Uri uri = Intent.ACTION_SEND.equals(intent.getAction())
                ? IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class)
                : intent.getData();
        // Content URIs carry their own read grant; storage permission is only for legacy file URIs.
        if ((uri != null && "content".equals(uri.getScheme())) || Build.VERSION.SDK_INT >= 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            restoreData();
            finish();
        } else if (savedInstanceState == null) {
            mStoragePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        restoreData();
        finish();
    }

    private void restoreData() {
        mRestoreHelper.handleIncomingZip(getIntent());
    }
}
