package pl.sviete.dom;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.os.Bundle;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.util.Locale;


import static android.widget.ListPopupWindow.MATCH_PARENT;
import static android.widget.ListPopupWindow.WRAP_CONTENT;


public class BrowserActivityNative extends BrowserActivity {

    final String TAG = BrowserActivityNative.class.getName();
    boolean doubleBackToExitPressedOnce = false;

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

        // http://www.qc4blog.com/?p=1186
        final Config config = new Config(this.getApplicationContext());
        final String appLaunchUrl = config.getAppLaunchUrl();
        // to save/up the connection in history
        config.setAppLaunchUrl(appLaunchUrl, null);

        //
        AisCoreUtils.mWebView.clearCache(true);
        //
        Locale current_locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            current_locale =  this.getApplicationContext().getResources().getConfiguration().getLocales().get(0);
        } else {
            //noinspection deprecation
            current_locale = this.getApplicationContext().getResources().getConfiguration().locale;
        }
        Locale.setDefault(Locale.forLanguageTag(current_locale.getLanguage()));

        // Force links and redirects to open in the WebView instead of in a browser
        AisCoreUtils.mWebView.setWebChromeClient(new WebChromeClient(){

            Snackbar snackbar;

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if(newProgress == 100 && snackbar != null){
                    snackbar.dismiss();
                    pageLoadComplete(view.getUrl());
                    return;
                }
                String text = getString(R.string.webview_loading) + newProgress+ "% " + view.getUrl();
                if(snackbar == null){
                    snackbar = Snackbar.make(view, text, Snackbar.LENGTH_INDEFINITE);
                } else {
                    snackbar.setText(text);
                }

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params.setMargins(0, 0, 0, 0);
                snackbar.getView().setLayoutParams(params);
                snackbar.show();
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
    protected void loadUrl(final String url) {
        if (zoomLevel != 1.0) { AisCoreUtils.mWebView.setInitialScale((int)(zoomLevel * 100)); }
        // mWebView.loadUrl(url);
        AisCoreUtils.mWebView.loadUrl(url);
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

        // checking if user is trying to change the mode but don't have access to AccessibilityService
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_1 || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE) {
                // change the mode
                if (!AisCoreUtils.isAccessibilityEnabled(getApplicationContext())){
                    // no access to AccessibilityService - try to add
                    AisCoreUtils.enableAccessibility();
                    Intent txtIntent = new Intent(AisPanelService.BROADCAST_READ_THIS_TXT_NOW);
                    txtIntent.putExtra(AisPanelService.READ_THIS_TXT_MESSAGE_VALUE, "Brak dostępu do klawiatury, sprawdzam uprawnienia aplikacji.");
                    LocalBroadcastManager bm = LocalBroadcastManager.getInstance(getApplicationContext());
                    bm.sendBroadcast(txtIntent);
                }
            }
        }

        if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Back in browser
            if (AisCoreUtils.getRemoteControllerMode().equals(AisCoreUtils.mOnDisplay)) {
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


