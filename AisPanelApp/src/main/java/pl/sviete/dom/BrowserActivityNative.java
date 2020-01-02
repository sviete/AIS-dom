package pl.sviete.dom;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.os.Bundle;
import android.webkit.WebViewClient;

import com.github.zagum.switchicon.SwitchIconView;
import com.redbooth.wizard.MainWizardActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class BrowserActivityNative extends BrowserActivity {

    final String TAG = BrowserActivityNative.class.getName();
    boolean doubleBackToExitPressedOnce = false;
    public static final int INPUT_FILE_REQUEST_CODE = 1;
    private static final int FILECHOOSER_RESULTCODE = 1;
    public static final String EXTRA_FROM_NOTIFICATION = "EXTRA_FROM_NOTIFICATION";
    private final int REQUEST_FILES_PERMISSION = 220;
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;
    private ValueCallback<Uri> mUploadMessage;
    private Uri mCapturedImageURI = null;



    /**
     * More info this method can be found at
     * http://developer.android.com/training/camera/photobasics.html
     *
     * @return
     * @throws IOException
     */
    private File createImageFile() {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File imageFile = null;
        try {
            imageFile = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpg",         /* suffix */
                    storageDir      /* directory */
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageFile;
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (AisCoreUtils.mWebView != null){
            AisCoreUtils.mWebView.clearHistory();
            AisCoreUtils.mWebView.clearCache(false);
            AisCoreUtils.mWebView.destroy();
            AisCoreUtils.mWebView = null;
        }

        setContentView(R.layout.activity_browser);
        AisCoreUtils.mWebView = (WebView) findViewById(R.id.activity_browser_webview_native);
        AisCoreUtils.mWebView.setVisibility(View.VISIBLE);
        AisCoreUtils.mWebView.setHorizontalScrollBarEnabled(true);
        AisCoreUtils.mWebView.setVerticalScrollBarEnabled(true);
        AisCoreUtils.mWebView.setScrollContainer(true);


        //
        Locale current_locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            current_locale =  this.getApplicationContext().getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            current_locale = this.getApplicationContext().getResources().getConfiguration().locale;
        }
        Locale.setDefault(Locale.forLanguageTag(current_locale.getLanguage()));

        // File choicer
        AisCoreUtils.mWebView.setWebChromeClient(new WebChromeClient() {
            public boolean onShowFileChooser(
                    WebView webView, ValueCallback<Uri[]> filePathCallback,
                    WebChromeClient.FileChooserParams fileChooserParams) {

                // check if we have access to files
                int permissionFiles = ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (permissionFiles != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(BrowserActivityNative.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_FILES_PERMISSION);
                    return false;
                }

                // Double check that we don't have any existing callbacks
                if(mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;

                // Set up the take picture intent
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    // Create the File where the photo should go
                    File photoFile = null;
                    photoFile = createImageFile();
                    takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);

                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                                Uri.fromFile(photoFile));
                    } else {
                        takePictureIntent = null;
                    }
                }

                // Set up the intent to get an existing image
                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("image/*");

                // Set up the intents for the Intent chooser
                Intent[] intentArray;
                if(takePictureIntent != null) {
                    intentArray = new Intent[]{takePictureIntent};
                } else {
                    intentArray = new Intent[0];
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

                startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);

                return true;
            }
            // openFileChooser for Android 3.0+
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;
                // Create AndroidExampleFolder at sdcard
                // Create AndroidExampleFolder at sdcard
                File imageStorageDir = new File(
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES)
                        , "AndroidExampleFolder");
                if (!imageStorageDir.exists()) {
                    // Create AndroidExampleFolder at sdcard
                    imageStorageDir.mkdirs();
                }
                // Create camera captured image file path and name
                File file = new File(
                        imageStorageDir + File.separator + "IMG_"
                                + String.valueOf(System.currentTimeMillis())
                                + ".jpg");
                mCapturedImageURI = Uri.fromFile(file);
                // Camera capture image intent
                final Intent captureIntent = new Intent(
                        android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI);
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                // Create file chooser intent
                Intent chooserIntent = Intent.createChooser(i, "Image Chooser");
                // Set camera intent to file chooser
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS
                        , new Parcelable[] { captureIntent });
                // On select image call onActivityResult method of activity
                startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
            }
            // openFileChooser for Android < 3.0
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
                openFileChooser(uploadMsg, "");
            }
            //openFileChooser for other Android versions
            public void openFileChooser(ValueCallback<Uri> uploadMsg,
                                        String acceptType,
                                        String capture) {
                openFileChooser(uploadMsg, acceptType);
            }
        });



        AisCoreUtils.mWebView.setWebViewClient(new WebViewClient(){
            //If you will not use this method url links are open in new browser not in webview
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                // then it is not handled by default action
                return false;
            }
            //
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                AisCoreUtils.mWebView.loadUrl("file:///android_asset/web/ais_loading.html");
            }

            //
            @Override
            public void onReceivedSslError(final WebView view, final SslErrorHandler handler, SslError error) {
                Log.d(TAG, "onReceivedSslError");
                Log.d(TAG, "error.getUrl(): " + error.getUrl());

                // we are using self sign certificate on localhost / 127.0.0.1 / ais-dom:8123 (ais-dom is a local hostname)
                if (error.getUrl().startsWith("https://localhost:8123/") || error.getUrl().startsWith("https://127.0.0.1:8123/") || error.getUrl().startsWith("https://ais-dom:8123/")){
                    handler.proceed();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(BrowserActivityNative.this);
                    String message = "Certificate error.";
                    switch (error.getPrimaryError()) {
                        case SslError.SSL_UNTRUSTED:
                            message = "The certificate authority is not trusted.";
                            break;
                        case SslError.SSL_EXPIRED:
                            message = "The certificate has expired.";
                            break;
                        case SslError.SSL_IDMISMATCH:
                            message = "The certificate Hostname mismatch.";
                            break;
                        case SslError.SSL_NOTYETVALID:
                            message = "The certificate is not yet valid.";
                            break;
                    }
                    message += " Do you want to continue anyway?";
                    builder.setTitle("SSL Certificate Error");
                    builder.setMessage(message);
                    builder.setPositiveButton("continue", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            handler.proceed();
                        }
                    });
                    builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            handler.cancel();
                        }
                    });
                    final AlertDialog dialog = builder.create();
                    dialog.show();
                }
            }
        });

        /**
         * JavaScript Handler which allow you to communicate with Framer project.
         */

        class JavascriptHandler {
            /**
             * This method handles call from javascript in WebView
             */

            @JavascriptInterface
            public String showTheUrl() {
                return appLaunchUrl;
            }

            @JavascriptInterface
            public void sayInFrame(String text) {
                speakOutFromBrowser(text);
            }
        }

        AisCoreUtils.mWebView.addJavascriptInterface(new JavascriptHandler(), "JavascriptHandler");

        WebSettings webSettings = AisCoreUtils.mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAppCacheEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            AisCoreUtils.mWebView.setWebContentsDebuggingEnabled(true);
        }

        Log.i(TAG, webSettings.getUserAgentString());

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_FILES_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // TODO
                    Log.i(TAG, "REQUEST_FILES_PERMISSION OK");
                } else {
                    Log.i(TAG, "REQUEST_FILES_PERMISSION NOK");
                }
        }
    }



    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        if(requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        Uri[] results = null;

        // Check that the response is a good one
        if(resultCode == Activity.RESULT_OK) {
            if(data == null) {
                // If there is not data, then we may have taken a photo
                if(mCameraPhotoPath != null) {
                    results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                }
            } else {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }

        mFilePathCallback.onReceiveValue(results);
        mFilePathCallback = null;
        return;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState ) {
        super.onSaveInstanceState(outState);
        AisCoreUtils.mWebView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        AisCoreUtils.mWebView.restoreState(savedInstanceState);
    }


    @Override
    protected void loadUrl(final String url, Boolean syncIcon) {
        if (zoomLevel != 1.0) { AisCoreUtils.mWebView.setInitialScale((int)(zoomLevel * 100)); }
        if (url.equals("")) {
            // go to settings
            Intent intent = new Intent(BrowserActivityNative.this, WelcomeActivity.class);
            intent.putExtra(WelcomeActivity.BROADCAST_STAY_ON_SETTNGS_ACTIVITY_VALUE, true);
            startActivity(intent);
            return;
        }
        if (url.startsWith("dom-")) {
            AisCoreUtils.mWebView.loadUrl("file:///android_asset/web/ais_loading.html");
        } else {
            AisCoreUtils.mWebView.loadUrl(url);
        }
        SwitchIconView mSwitchIconModeConnection =  findViewById(R.id.switchControlModeConnection);
        View mButtonModeConnection = findViewById(R.id.btnControlModeConnection);
        // display connection icon
        if (syncIcon == true){
            mButtonModeConnection.setBackgroundResource(R.drawable.ic_connection_sync_icon);
        } else {
            mButtonModeConnection.setBackgroundResource(R.drawable.ic_empty_icon);
        }
        if (url.contains("paczka.pro")) {
            mSwitchIconModeConnection.setBackgroundResource(R.drawable.ic_cloud_connection_control_bg);
        } else {
            mSwitchIconModeConnection.setBackgroundResource(R.drawable.ic_local_connection_control_bg);
        }


    }


    @Override
    protected void evaluateJavascript(final String js) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            AisCoreUtils.mWebView.evaluateJavascript(js, null);
        }
    }

    @Override
    protected void clearCache() {
        AisCoreUtils.mWebView.clearCache(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
        }
    }

    @Override
    protected void reload() {
        AisCoreUtils.mWebView.reload();
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Back in browser
            if (event.getAction() == KeyEvent.ACTION_UP){
                Log.i(TAG, "Back button pressed in on display mode");
                String historyUrl="";
                WebBackForwardList mWebBackForwardList = AisCoreUtils.mWebView.copyBackForwardList();
                if (mWebBackForwardList.getCurrentIndex() > 0) {
                    historyUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getCurrentIndex() - 1).getUrl();
                }

                if (AisCoreUtils.mWebView.canGoBack() && !historyUrl.equals("file:///android_asset/web/ais_loading.html")) {
                    AisCoreUtils.mWebView.evaluateJavascript("window.history.back()", null);
                    return true;
                } else {
                    // exit on back in not on box
                    Log.i(TAG, "Back button pressed we should exit the app");
                    Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startMain);
//                            if (doubleBackToExitPressedOnce) {
//                                // going to home screen programmatically
//                                Intent startMain = new Intent(Intent.ACTION_MAIN);
//                                startMain.addCategory(Intent.CATEGORY_HOME);
//                                startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                                startActivity(startMain);
//                            } else {
//                                this.doubleBackToExitPressedOnce = true;
//                                Toast.makeText(this, R.string.back_to_exit, Toast.LENGTH_SHORT).show();
//
//                                new Handler().postDelayed(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        doubleBackToExitPressedOnce = false;
//                                    }
//                                }, 2000);
//                            }
                }
            }
        }

        else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP){
            if (AisCoreUtils.getRemoteControllerMode().equals(AisCoreUtils.mOnDisplay)) {
                Log.d(TAG, "dispatchKeyEvent mWebView " + event.getAction() + " " + event.getKeyCode());

                if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                    AisCoreUtils.mWebView.dispatchKeyEvent(new KeyEvent(event.getAction(), KeyEvent.KEYCODE_PAGE_DOWN));
                } else {
                    AisCoreUtils.mWebView.dispatchKeyEvent(new KeyEvent(event.getAction(), KeyEvent.KEYCODE_PAGE_UP));
                }
            }
        }
        // return to parent
        return super.dispatchKeyEvent(event);
    }


}


