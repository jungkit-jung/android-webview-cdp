package app.so7o.webview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * AGY Agentic Browser Shell
 *
 * Runs full Android WebView with Chrome DevTools Protocol (CDP) enabled,
 * relayed to 127.0.0.1:9334 so local agents (like Antigravity in Termux)
 * can inspect, navigate, click, fill forms, and analyze authenticated web sessions.
 */
public class MainActivity extends Activity {

    private static final String TAG = "So7oWebView";
    private WebView web;
    private EditText urlBar;
    private ProgressBar progressBar;
    private TextView statusDot;

    @Override
    protected void onCreate(Bundle state) {
        // Must run before any WebView is constructed. Global to the process.
        WebView.setWebContentsDebuggingEnabled(true);
        final int pid = android.os.Process.myPid();
        Log.i(TAG, "devtools socket = webview_devtools_remote_" + pid);

        super.onCreate(state);

        // Keep-alive service to prevent Android from freezing background process
        try {
            startForegroundService(new Intent(this, KeepAliveService.class));
        } catch (Throwable t) {
            Log.w(TAG, "KeepAliveService start failed: " + t.getMessage());
        }

        // Build Root Layout
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.BLACK);

        // Top Navigation Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#1E1E2E"));
        int barPadding = dp(8);
        topBar.setPadding(barPadding, barPadding, barPadding, barPadding);

        // Back Button
        TextView backBtn = new TextView(this);
        backBtn.setText("◀");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        backBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web != null && web.canGoBack()) {
                    web.goBack();
                }
            }
        });
        topBar.addView(backBtn);

        // URL / Search Input Bar
        urlBar = new EditText(this);
        urlBar.setHint("Enter URL or search...");
        urlBar.setHintTextColor(Color.parseColor("#888899"));
        urlBar.setTextColor(Color.WHITE);
        urlBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        urlBar.setSingleLine(true);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setBackgroundColor(Color.parseColor("#313244"));
        urlBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        urlParams.setMargins(dp(6), 0, dp(6), 0);
        urlBar.setLayoutParams(urlParams);

        urlBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    loadFromUrlBar();
                    return true;
                }
                return false;
            }
        });
        topBar.addView(urlBar);

        // Go / Reload Button
        TextView goBtn = new TextView(this);
        goBtn.setText("➔");
        goBtn.setTextColor(Color.WHITE);
        goBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        goBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        goBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadFromUrlBar();
            }
        });
        topBar.addView(goBtn);

        // Agent Status Indicator (⚡)
        statusDot = new TextView(this);
        statusDot.setText("⚡");
        statusDot.setTextColor(Color.parseColor("#A6E3A1")); // Green
        statusDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusDot.setPadding(dp(6), dp(4), dp(8), dp(4));
        topBar.addView(statusDot);

        rootLayout.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Loading Progress Bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        rootLayout.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        // WebView
        web = new WebView(this);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        rootLayout.addView(web, webParams);

        setContentView(rootLayout);

        // Configure WebSettings
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        // Enable Cookies (including 3rd-party for auth/login workflows)
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(web, true);
        }

        // WebChromeClient for Progress
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // WebViewClient for navigation
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // Load inside this WebView
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (urlBar != null && !urlBar.hasFocus()) {
                    urlBar.setText(url);
                }
            }
        });

        web.addJavascriptInterface(new Bridge(), "so7o");
        web.loadUrl("file:///android_asset/index.html");

        // Publish DevTools socket on 127.0.0.1:9334
        new RelayServer().start();
    }

    private void loadFromUrlBar() {
        if (urlBar == null || web == null) return;
        String query = urlBar.getText().toString().trim();
        if (query.isEmpty()) return;

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
        }
        urlBar.clearFocus();

        if (query.startsWith("http://") || query.startsWith("https://") || query.startsWith("file://")) {
            web.loadUrl(query);
        } else if (query.contains(".") && !query.contains(" ")) {
            web.loadUrl("https://" + query);
        } else {
            // Search Google
            web.loadUrl("https://www.google.com/search?q=" + android.net.Uri.encode(query));
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    /** In-app channel (page -> Java), independent of CDP. */
    public class Bridge {
        @JavascriptInterface
        public String ping() {
            return "pong from app pid " + android.os.Process.myPid()
                    + " at " + System.currentTimeMillis();
        }

        /** Introspection: which socket should an outside client aim at? */
        @JavascriptInterface
        public String info() {
            int pid = android.os.Process.myPid();
            return "{\"pid\":" + pid
                    + ",\"socket\":\"webview_devtools_remote_" + pid
                    + "\",\"relay\":\"127.0.0.1:" + RelayServer.PORT
                    + "\",\"uptime_ms\":" + android.os.SystemClock.elapsedRealtime() + "}";
        }

        /** Prove that a CDP call really crossed into the Android layer. */
        @JavascriptInterface
        public String toast(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
                }
            });
            return "toasted: " + text;
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
