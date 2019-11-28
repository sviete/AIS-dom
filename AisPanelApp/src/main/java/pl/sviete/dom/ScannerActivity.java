package pl.sviete.dom;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import me.dm7.barcodescanner.zbar.Result;
import me.dm7.barcodescanner.zbar.ZBarScannerView;

public class ScannerActivity extends Activity
        implements ZBarScannerView.ResultHandler {
    private ZBarScannerView mScannerView;
    static final String TAG = ScannerActivity.class.getName();
    private static final int PERMISSION_REQUEST_CODE = 200;
    public static String BACK_TO_WIZARD = "BACK_TO_WIZARD";
    private boolean backToWizard = false;
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (checkPermission()) {
            mScannerView = new ZBarScannerView(this);
            setContentView(mScannerView);

        } else {
            requestPermission();
        }


    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            return false;
        }
        return true;
    }

    private void requestPermission() {

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // main logic
                    mScannerView = new ZBarScannerView(this);    // Programmatically initialize the scanner view
                    setContentView(mScannerView);                // Set the scanner view as the content view
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.cammera_permissions_denied_title), Toast.LENGTH_SHORT).show();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                != PackageManager.PERMISSION_GRANTED) {
                            showMessageOKCancel(getString(R.string.cammera_permissions_dialog_info),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermission();
                                            }
                                        }
                                    });
                        }
                    }
                }
                break;
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(ScannerActivity.this)
                .setMessage(message)
                .setPositiveButton(getString(R.string.cammera_button_ok), okListener)
                .setNegativeButton(getString(R.string.cammera_button_cancel), null)
                .create()
                .show();
    }


    @Override
    public void onResume() {
        super.onResume();
        Intent callingintent = getIntent();
        backToWizard = callingintent.getBooleanExtra(BACK_TO_WIZARD, false);
        if (checkPermission()) {
            mScannerView.setResultHandler(this);
            mScannerView.startCamera();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (checkPermission()) {
            mScannerView.stopCamera();
        }
    }

    @Override
    public void handleResult(Result rawResult) {
        String scan = rawResult.getContents();
        Log.i(TAG, scan);
        scan = scan.replace("https://", "");
        scan = scan.replace(".paczka.pro", "");
        // gate id
        if (scan.startsWith("dom-")) {
            Toast.makeText(getApplicationContext(), scan, Toast.LENGTH_SHORT).show();
            // save code and go to browser or wizard
            final Config config = new Config(this.getApplicationContext());
            config.setAppLaunchUrl(scan);
            if (backToWizard){
                this.finish();
            } else {
                startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
            }
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.cammera_permissions_denied_title), Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                showMessageOKCancel(getString(R.string.cammera_wrong_code_dialog_info, scan),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermission();
                                }
                            }
                        });
            }
        }
    }
}