package io.github.lucaswotta.webmini;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.os.Environment;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.ScrollView;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static {
        System.loadLibrary("webmini_adblock");
    }

    private static final String HOME = "https://www.google.com/";
    private static final String ABOUT = "https://github.com/lucaswotta/webmini-androidtv";
    private static final String BLANK = "about:blank";
    private static final String FAVORITES_KEY = "favorites";
    private static final String EASYLIST_URL = "https://easylist.to/easylist/easylist.txt";
    private static final String EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt";
    private static final String[] FILTER_LIST_FILES = {"easylist.txt", "easyprivacy.txt"};
    private static final long FILTER_LIST_MAX_AGE = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_FILTER_LIST_BYTES = 5 * 1024 * 1024;
    private static final byte[] EMPTY_BODY = new byte[0];
    private static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "amazon-adsystem.com", "taboola.com", "outbrain.com"
    };

    private interface Choice {
        void select(int index);
    }

    private WebView webView;
    private EditText address;
    private View loadingLine;
    private FrameLayout root;
    private FrameLayout menuPanel;
    private FrameLayout content;
    private LinearLayout page;
    private View cursorView;
    private Button favoriteButton;
    private EditText homeSearch;
    private final ArrayList<String> favorites = new ArrayList<>();
    private boolean homePage;
    private boolean tvMode;
    private boolean cursorShown = true;
    private long edgeScrollStarted;
    private int edgeScrollDirection;
    private final Runnable hideCursor = () -> {
        if (tvMode && cursorShown) {
            cursorView.setVisibility(View.INVISIBLE);
            cursorShown = false;
        }
    };
    private static final long CURSOR_IDLE_MS = 2500;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private volatile boolean adBlockEnabled;
    private boolean popupsBlocked;
    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadReferer;
    private String pendingDownloadName;
    private String pendingDownloadMime;
    private String approvedHttpUrl;
    private float cursorX = -1;
    private float cursorY = -1;
    private float analogX;
    private float analogY;
    private int digitalX;
    private int digitalY;
    private int digitalSpeed = 6;
    private boolean cursorTickScheduled;
    private volatile String currentUrl = HOME;
    private volatile boolean nativeFiltersReady;
    private final ExecutorService adBlockExecutor = Executors.newSingleThreadExecutor();
    private boolean adBlockInitializationRunning;
    private boolean adBlockReloadRequested;
    private final Runnable hideLoading = () -> loadingLine.setVisibility(View.INVISIBLE);
    private final Runnable cursorTick = new Runnable() {
        @Override
        public void run() {
            cursorTickScheduled = false;
            if (!tvMode || (analogX == 0 && analogY == 0
                    && digitalX == 0 && digitalY == 0)) return;
            revealCursor();
            moveCursorPixels(dp(6) * analogX + dp(digitalSpeed) * digitalX,
                    dp(6) * analogY + dp(digitalSpeed) * digitalY);
            scheduleCursorTick();
        }
    };

    private static native void nativeBeginFilterLists();
    private static native boolean nativeAddFilterList(String listText);
    private static native boolean nativeFinishFilterLists();
    private static native void nativeClearFilters();
    private static native boolean nativeShouldBlock(String url, String pageUrl,
            String resourceType, String method);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tvMode = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        if (tvMode) setTheme(R.style.TvTheme);
        super.onCreate(savedInstanceState);
        adBlockEnabled = getPreferences(MODE_PRIVATE).getBoolean("adBlockEnabled", true);
        popupsBlocked = getPreferences(MODE_PRIVATE).getBoolean("popupsBlocked", true);
        loadFavorites();

        root = new FrameLayout(this);
        if (Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return filterNetworkRequest(request);
                }
            });
        }
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        if (tvMode) {
            root.setBackgroundColor(Color.BLACK);
            page.setBackgroundColor(Color.BLACK);
        }
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                page.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                page.setPadding(insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER);
        if (tvMode) {
            toolbar.setBackgroundColor(Color.BLACK);
            toolbar.setPadding(0, 0, 0, 0);
        }
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        Button home = button("⌂", toolbar);
        home.setContentDescription(getString(R.string.nav_home));
        home.setOnClickListener(view -> {
            showHomePage();
        });

        address = new EditText(this);
        address.setHint(R.string.address_hint);
        address.setSingleLine(true);
        address.setGravity(Gravity.CENTER_VERTICAL);
        address.setSelectAllOnFocus(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        if (tvMode) {
            address.setBackgroundResource(R.drawable.tv_address);
            address.setTextColor(Color.rgb(245, 244, 239));
            address.setHintTextColor(Color.rgb(170, 170, 170));
            address.setTextSize(18);
            address.setPadding(dp(18), 0, dp(18), 0);
        }
        address.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                openAddress();
                return true;
            }
            return false;
        });
        Button back = button("←", toolbar);
        back.setContentDescription(getString(R.string.nav_back));
        back.setOnClickListener(view -> navigateBack());
        FrameLayout addressBox = new FrameLayout(this);
        toolbar.addView(addressBox, new LinearLayout.LayoutParams(
                0, tvMode ? dp(52) : -2, 1));
        addressBox.addView(address, new FrameLayout.LayoutParams(-1, tvMode ? -1 : -2));
        loadingLine = new View(this);
        loadingLine.setBackgroundColor(Color.rgb(56, 189, 248));
        loadingLine.setVisibility(View.INVISIBLE);
        loadingLine.setPivotX(0);
        loadingLine.setScaleX(0);
        addressBox.addView(loadingLine, new FrameLayout.LayoutParams(-1, dp(2), Gravity.BOTTOM));
        Button reload = button("⟳", toolbar);
        reload.setContentDescription(getString(R.string.nav_reload));
        reload.setOnClickListener(view -> reloadPage());
        favoriteButton = button("☆", toolbar);
        favoriteButton.setContentDescription(getString(R.string.nav_favorite_add));
        favoriteButton.setOnClickListener(view -> toggleFavorite());
        Button menu = button("⋮", toolbar);
        menu.setContentDescription(getString(R.string.nav_options));
        menu.setOnClickListener(view -> showMenu());

        if (tvMode) {
            View line = new View(this);
            line.setBackgroundColor(Color.rgb(245, 244, 239));
            page.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        if (tvMode) createCursor();

        String launchUrl = savedInstanceState == null ? incomingUrl(getIntent())
                : savedInstanceState.getString("url");
        currentUrl = launchUrl == null ? HOME : launchUrl;
        if (launchUrl == null || (savedInstanceState != null
                && savedInstanceState.getBoolean("homePage"))) {
            showHomePage();
        } else if (needsHttpWarning(launchUrl)) {
            showHomePage();
            navigateTo(launchUrl);
        } else {
            createWebView(currentUrl);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::navigateBack);
        }
        if (adBlockEnabled) initializeAdblock();
    }

    private Button button(String label, LinearLayout parent) {
        Button button = new Button(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(
                parent.getOrientation() == LinearLayout.VERTICAL ? -1 : tvMode ? dp(52) : -2,
                tvMode ? dp(52) : -2);
        if (tvMode) {
            button.setAllCaps(false);
            button.setTextSize(parent.getOrientation() == LinearLayout.VERTICAL ? 18 : 24);
            button.setTextColor(getResources().getColorStateList(R.color.tv_button_text));
            button.setBackgroundResource(R.drawable.tv_button);
            button.setPadding(parent.getOrientation() == LinearLayout.VERTICAL ? dp(18) : 0,
                    0, parent.getOrientation() == LinearLayout.VERTICAL ? dp(18) : 0, 0);
            if (parent.getOrientation() == LinearLayout.VERTICAL) {
                size.setMargins(0, dp(4), 0, dp(4));
            }
        }
        parent.addView(button, size);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showLoading() {
        if (loadingLine == null) return;
        loadingLine.removeCallbacks(hideLoading);
        loadingLine.animate().cancel();
        loadingLine.setScaleX(0.02f);
        loadingLine.setVisibility(View.VISIBLE);
        loadingLine.animate().scaleX(0.2f).setDuration(600).start();
    }

    private void reloadPage() {
        if (webView == null) return;
        showLoading();
        webView.reload();
    }

    private void showHomePage() {
        if (webView != null) {
            webView.stopLoading();
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        homePage = true;
        currentUrl = HOME;
        address.setText("");
        favoriteButton.setText("☆");
        favoriteButton.setEnabled(false);
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(tvMode ? Color.BLACK : Color.rgb(20, 20, 20));
        LinearLayout landing = new LinearLayout(this);
        landing.setOrientation(LinearLayout.VERTICAL);
        landing.setGravity(Gravity.CENTER_HORIZONTAL);
        landing.setPadding(dp(20), dp(28), dp(20), dp(24));
        scroll.addView(landing, new ScrollView.LayoutParams(-1, -2));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_browser);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoSize = new LinearLayout.LayoutParams(dp(92), dp(92));
        logoSize.bottomMargin = dp(8);
        landing.addView(logo, logoSize);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        landing.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackgroundResource(tvMode ? R.drawable.tv_address : R.drawable.tv_address);
        LinearLayout.LayoutParams searchRowSize = new LinearLayout.LayoutParams(-1, dp(52));
        searchRowSize.setMargins(0, dp(22), 0, dp(24));
        landing.addView(searchRow, searchRowSize);
        homeSearch = new EditText(this);
        homeSearch.setSingleLine(true);
        homeSearch.setHint(R.string.home_search_hint);
        homeSearch.setTextColor(Color.WHITE);
        homeSearch.setHintTextColor(Color.LTGRAY);
        homeSearch.setTextSize(16);
        homeSearch.setPadding(dp(14), 0, dp(8), 0);
        homeSearch.setBackgroundColor(Color.TRANSPARENT);
        homeSearch.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        homeSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        homeSearch.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchGoogle(homeSearch.getText().toString());
                return true;
            }
            return false;
        });
        searchRow.addView(homeSearch, new LinearLayout.LayoutParams(0, -1, 1));
        Button searchButton = new Button(this);
        searchButton.setText("⌕");
        searchButton.setTextColor(Color.rgb(56, 189, 248));
        searchButton.setTextSize(23);
        searchButton.setMinWidth(dp(52));
        searchButton.setBackgroundColor(Color.TRANSPARENT);
        searchButton.setOnClickListener(view -> searchGoogle(homeSearch.getText().toString()));
        searchRow.addView(searchButton, new LinearLayout.LayoutParams(dp(52), -1));

        TextView favoritesTitle = new TextView(this);
        favoritesTitle.setText(R.string.favorites_title);
        favoritesTitle.setTextColor(Color.WHITE);
        favoritesTitle.setTextSize(18);
        favoritesTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1, -2);
        headingSize.setMargins(0, 0, 0, dp(10));
        landing.addView(favoritesTitle, headingSize);
        if (favorites.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.favorites_empty);
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(14);
            landing.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (int i = 0; i < favorites.size(); i += 2) {
                LinearLayout row = new LinearLayout(this);
                LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, dp(72));
                rowSize.setMargins(-dp(4), 0, -dp(4), dp(4));
                landing.addView(row, rowSize);
                for (int slot = i; slot < Math.min(i + 2, favorites.size()); slot++) {
                    String url = favorites.get(slot);
                    Button item = new Button(this);
                    String host = Uri.parse(url).getHost();
                    item.setText(host == null ? url : host);
                    item.setGravity(Gravity.CENTER);
                    item.setTextColor(Color.WHITE);
                    item.setAllCaps(false);
                    item.setTextSize(14);
                    item.setBackgroundResource(R.drawable.tv_button);
                    LinearLayout.LayoutParams itemSize = new LinearLayout.LayoutParams(0, -1, 1);
                    itemSize.setMargins(dp(4), dp(4), dp(4), dp(4));
                    row.addView(item, itemSize);
                    item.setOnClickListener(view -> navigateTo(url));
                }
            }
        }
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        updateFavoriteButton();
    }

    private void searchGoogle(String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return;
        try {
            navigateTo("https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException error) {
            throw new AssertionError(error);
        }
    }

    private void navigateTo(String url) {
        // shouldOverrideUrlLoading does not run for loadUrl, so typed addresses,
        // favorites and links from other apps are checked here.
        if (needsHttpWarning(url)) {
            confirmHttp(url);
            return;
        }
        if (webView == null) createWebView(url);
        else {
            showLoading();
            webView.loadUrl(url);
        }
    }

    private boolean needsHttpWarning(String url) {
        return "http".equalsIgnoreCase(Uri.parse(url).getScheme()) && !url.equals(approvedHttpUrl);
    }

    private void confirmHttp(String url) {
        showConfirmation(getString(R.string.http_warning_title),
                getString(R.string.http_warning_message, Uri.parse(url).getHost()),
                getString(R.string.action_continue), getString(R.string.action_cancel),
                () -> {
                    approvedHttpUrl = url;
                    navigateTo(url);
                });
    }

    private void loadFavorites() {
        favorites.clear();
        String saved = getPreferences(MODE_PRIVATE).getString(FAVORITES_KEY, "[]");
        try {
            JSONArray urls = new JSONArray(saved);
            for (int i = 0; i < urls.length(); i++) {
                String url = urls.optString(i);
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    favorites.add(url);
                }
            }
        } catch (org.json.JSONException ignored) {
            getPreferences(MODE_PRIVATE).edit().remove(FAVORITES_KEY).apply();
        }
    }

    private void persistFavorites() {
        JSONArray urls = new JSONArray();
        for (String url : favorites) urls.put(url);
        getPreferences(MODE_PRIVATE).edit().putString(FAVORITES_KEY, urls.toString()).apply();
    }

    private void toggleFavorite() {
        if (webView == null || currentUrl == null || !currentUrl.startsWith("http")) return;
        if (favorites.contains(currentUrl)) favorites.remove(currentUrl);
        else favorites.add(0, currentUrl);
        persistFavorites();
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        if (favoriteButton == null) return;
        boolean saved = !homePage && favorites.contains(currentUrl);
        favoriteButton.setText(saved ? "★" : "☆");
        favoriteButton.setEnabled(!homePage);
        favoriteButton.setContentDescription(getString(saved
                ? R.string.nav_favorite_remove : R.string.nav_favorite_add));
    }

    private String incomingUrl(Intent intent) {
        Uri uri = intent.getData();
        if (uri == null) return null;
        String scheme = uri.getScheme();
        return "http".equals(scheme) || "https".equals(scheme) ? uri.toString() : null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = incomingUrl(intent);
        if (url != null) {
            navigateTo(url);
        }
    }

    private void createWebView(String url) {
        content.removeAllViews();
        webView = new WebView(this);
        if (tvMode) webView.setBackgroundColor(Color.BLACK);
        WebSettings settings = webView.getSettings();
        CookieManager.getInstance().setAcceptCookie(true);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        applyPopupPolicy(settings);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                               WebResourceRequest request) {
                return filterNetworkRequest(request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri destination = request.getUrl();
                String scheme = destination.getScheme();
                if ("http".equalsIgnoreCase(scheme)) {
                    if (destination.toString().equals(approvedHttpUrl)) {
                        approvedHttpUrl = null;
                        return false;
                    }
                    confirmHttp(destination.toString());
                    return true;
                }
                if (!"https".equalsIgnoreCase(scheme)) {
                    Toast.makeText(MainActivity.this, R.string.navigation_blocked, Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl,
                                      android.graphics.Bitmap favicon) {
                if (view == webView) {
                    currentUrl = pageUrl;
                    if (loadingLine.getVisibility() != View.VISIBLE) showLoading();
                }
                if (view == webView && tvMode) view.requestFocus();
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (view == webView && !pageUrl.equals(BLANK)) {
                    currentUrl = pageUrl;
                    address.setText(pageUrl);
                    updateFavoriteButton();
                }
                if (view == webView) {
                    loadingLine.removeCallbacks(hideLoading);
                    loadingLine.animate().scaleX(1f).setDuration(120).start();
                    loadingLine.postDelayed(hideLoading, 250);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view != webView) return true;
                hideFullscreen();
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                view.destroy();
                webView = null;
                createWebView(BLANK);
                address.setText(currentUrl);
                Toast.makeText(MainActivity.this, R.string.page_stopped, Toast.LENGTH_LONG).show();
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (view != webView || loadingLine.getVisibility() != View.VISIBLE) return;
                loadingLine.animate().scaleX(Math.max(0.2f, progress / 100f))
                        .setDuration(150).start();
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                String link = isUserGesture && !isDialog
                        ? view.getHitTestResult().getExtra() : null;
                if (link != null && (link.startsWith("https://") || link.startsWith("http://"))) {
                    view.post(() -> {
                        Runnable openLink = () -> {
                            if (webView != null) navigateTo(link);
                        };
                        if (popupsBlocked) {
                            showConfirmation(getString(R.string.popup_open_title),
                                    getString(R.string.popup_open_message, Uri.parse(link).getHost()),
                                    getString(R.string.action_open), getString(R.string.action_block), openLink);
                        } else {
                            openLink.run();
                        }
                    });
                } else {
                    view.post(() -> Toast.makeText(MainActivity.this,
                            R.string.popup_unknown_blocked, Toast.LENGTH_LONG).show());
                }
                return false;
            }

            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.deny();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }

            @Override
            public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<Uri[]> callback,
                    android.webkit.WebChromeClient.FileChooserParams params) {
                callback.onReceiveValue(null);
                Toast.makeText(MainActivity.this, R.string.file_access_blocked, Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                if (cursorView != null) cursorView.bringToFront();
                page.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }
        });
        webView.setDownloadListener((downloadUrl, userAgent, contentDisposition, mimeType, contentLength) ->
                confirmDownload(downloadUrl, userAgent, contentDisposition, mimeType));
        content.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        homePage = false;
        address.setText(url.equals(BLANK) ? currentUrl : url);
        updateFavoriteButton();
        webView.loadUrl(url);
    }

    private void createCursor() {
        cursorView = new View(this);
        cursorView.setBackgroundResource(R.drawable.tv_cursor);
        root.addView(cursorView, new FrameLayout.LayoutParams(dp(28), dp(32)));
        root.post(() -> {
            cursorX = root.getWidth() / 2f;
            cursorY = root.getHeight() / 2f;
            updateCursor();
            revealCursor();
        });
    }

    private void updateCursor() {
        cursorView.setX(cursorX);
        cursorView.setY(cursorY);
    }

    private void revealCursor() {
        if (!tvMode || cursorView == null) return;
        cursorView.setVisibility(View.VISIBLE);
        cursorShown = true;
        cursorView.removeCallbacks(hideCursor);
        cursorView.postDelayed(hideCursor, CURSOR_IDLE_MS);
    }

    private void scheduleCursorTick() {
        if (cursorTickScheduled) return;
        cursorTickScheduled = true;
        cursorView.postOnAnimation(cursorTick);
    }

    private void moveCursorPixels(float horizontal, float vertical) {
        float nextY = cursorY + vertical;
        int direction = nextY > root.getHeight() - dp(32) ? 1 : nextY < 0 ? -1 : 0;
        if (direction != 0 && webView != null) {
            long now = SystemClock.uptimeMillis();
            if (edgeScrollDirection != direction) {
                edgeScrollDirection = direction;
                edgeScrollStarted = now;
            }
            int scrollStep = dp(3 + Math.min(17, (int) ((now - edgeScrollStarted) / 180)));
            webView.scrollBy(0, direction * scrollStep);
        } else {
            edgeScrollDirection = 0;
            edgeScrollStarted = 0;
        }
        cursorX = Math.max(0, Math.min(root.getWidth() - dp(28), cursorX + horizontal));
        cursorY = Math.max(0, Math.min(root.getHeight() - dp(32), nextY));
        updateCursor();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (tvMode && event.getAction() == MotionEvent.ACTION_MOVE
                && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            if (focusedEditor() != null || keyboardAcceptingText()) {
                analogX = 0;
                analogY = 0;
                return true;
            }
            float x = event.getAxisValue(MotionEvent.AXIS_X);
            float y = event.getAxisValue(MotionEvent.AXIS_Y);
            analogX = Math.abs(x) > 0.2f ? x : 0;
            analogY = Math.abs(y) > 0.2f ? y : 0;
            if (analogX != 0 || analogY != 0) {
                revealCursor();
                scheduleCursorTick();
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void clickCursor() {
        revealCursor();
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                cursorX, cursorY, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP,
                cursorX, cursorY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        View target = menuPanel != null ? menuPanel
                : fullscreenView == null ? page : fullscreenView;
        target.dispatchTouchEvent(down);
        target.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (tvMode && event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP) navigateBack();
            return true;
        }
        EditText editor = tvMode ? focusedEditor() : null;
        if (tvMode && editor == null && keyboardAcceptingText()) {
            return super.dispatchKeyEvent(event);
        }
        if (editor != null) {
            digitalX = 0;
            digitalY = 0;
            if (cursorView != null) cursorView.removeCallbacks(cursorTick);
            cursorTickScheduled = false;
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) moveTextCaret(editor,
                        key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (tvMode) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                    || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                    || key == KeyEvent.KEYCODE_BUTTON_A) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    revealCursor();
                    if (key == KeyEvent.KEYCODE_DPAD_UP) digitalY = -1;
                    if (key == KeyEvent.KEYCODE_DPAD_DOWN) digitalY = 1;
                    if (key == KeyEvent.KEYCODE_DPAD_LEFT) digitalX = -1;
                    if (key == KeyEvent.KEYCODE_DPAD_RIGHT) digitalX = 1;
                    if (digitalX != 0 || digitalY != 0) {
                        digitalSpeed = event.getRepeatCount() > 5 ? 10 : 6;
                        if (event.getRepeatCount() == 0) {
                            moveCursorPixels(dp(6) * digitalX, dp(6) * digitalY);
                        }
                        scheduleCursorTick();
                    }
                    if ((key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                            || key == KeyEvent.KEYCODE_BUTTON_A)
                            && event.getRepeatCount() == 0) clickCursor();
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (key == KeyEvent.KEYCODE_DPAD_UP && digitalY == -1) digitalY = 0;
                    if (key == KeyEvent.KEYCODE_DPAD_DOWN && digitalY == 1) digitalY = 0;
                    if (key == KeyEvent.KEYCODE_DPAD_LEFT && digitalX == -1) digitalX = 0;
                    if (key == KeyEvent.KEYCODE_DPAD_RIGHT && digitalX == 1) digitalX = 0;
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private EditText focusedEditor() {
        if (address != null && address.isFocused()) return address;
        if (homeSearch != null && homeSearch.isFocused()) return homeSearch;
        return null;
    }

    private boolean keyboardAcceptingText() {
        InputMethodManager inputMethod = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        return inputMethod != null && inputMethod.isAcceptingText();
    }

    private void moveTextCaret(EditText editor, int direction) {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end < 0) return;
        if (start != end) {
            editor.setSelection(direction < 0 ? Math.min(start, end) : Math.max(start, end));
            return;
        }
        if ((direction < 0 && start == 0) || (direction > 0 && start == editor.length())) return;
        int next = Character.offsetByCodePoints(editor.getText(), start, direction);
        editor.setSelection(next);
    }

    private void showMenu() {
        String popupLabel = getString(popupsBlocked
                ? R.string.popup_block_enabled : R.string.popup_block_disabled);
        String adBlockLabel = getString(adBlockEnabled
                ? R.string.tracker_block_enabled : R.string.tracker_block_disabled);
        String[] entries = {getString(R.string.menu_forward), popupLabel,
                adBlockLabel, getString(R.string.menu_about)};
        showChoices(entries, which -> {
            if (which == 0 && webView != null && webView.canGoForward()) webView.goForward();
            if (which == 1) {
                popupsBlocked = !popupsBlocked;
                getPreferences(MODE_PRIVATE).edit()
                        .putBoolean("popupsBlocked", popupsBlocked).apply();
                if (webView != null) applyPopupPolicy(webView.getSettings());
            }
            if (which == 2) {
                adBlockEnabled = !adBlockEnabled;
                getPreferences(MODE_PRIVATE).edit()
                        .putBoolean("adBlockEnabled", adBlockEnabled).apply();
                if (adBlockEnabled) {
                    initializeAdblock();
                } else {
                    nativeFiltersReady = false;
                    adBlockExecutor.execute(MainActivity::nativeClearFilters);
                }
                if (webView != null) reloadPage();
            }
            if (which == 3) {
                navigateTo(ABOUT);
            }
        });
    }

    private void applyPopupPolicy(WebSettings settings) {
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
    }

    private String requestType(WebResourceRequest request) {
        if (request.isForMainFrame()) return "document";
        java.util.Map<String, String> headers = request.getRequestHeaders();
        String destination = headers.get("Sec-Fetch-Dest");
        if (destination == null) destination = headers.get("sec-fetch-dest");
        if (destination != null) {
            switch (destination.toLowerCase(Locale.ROOT)) {
                case "script": return "script";
                case "style": return "stylesheet";
                case "image": return "image";
                case "font": return "font";
                case "audio":
                case "video": return "media";
                case "iframe":
                case "frame": return "subdocument";
                case "document": return "document";
                default: break;
            }
        }
        String accept = headers.get("Accept");
        if (accept == null) accept = headers.get("accept");
        String value = accept == null ? "" : accept.toLowerCase(Locale.ROOT);
        if (value.contains("text/css")) return "stylesheet";
        if (value.contains("javascript")) return "script";
        if (value.contains("image/")) return "image";
        if (value.contains("font/")) return "font";
        if (value.contains("audio/") || value.contains("video/")) return "media";
        if (value.contains("text/html")) return "subdocument";
        if (value.contains("application/json")) return "xmlhttprequest";
        String requestPath = request.getUrl().getPath();
        String path = requestPath == null ? "" : requestPath.toLowerCase(Locale.ROOT);
        if (path.endsWith(".js") || path.endsWith(".mjs")) return "script";
        if (path.endsWith(".css")) return "stylesheet";
        if (path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf")
                || path.endsWith(".otf") || path.endsWith(".eot")) return "font";
        if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")
                || path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg")
                || path.endsWith(".avif") || path.endsWith(".ico")) return "image";
        if (path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".mpd")
                || path.endsWith(".webm") || path.endsWith(".mp3") || path.endsWith(".m4a")
                || path.endsWith(".aac") || path.endsWith(".ogg")) return "media";
        return "other";
    }

    private WebResourceResponse filterNetworkRequest(WebResourceRequest request) {
        if (!adBlockEnabled || request.isForMainFrame()) return null;
        String resourceUrl = request.getUrl().toString();
        boolean blocked = nativeFiltersReady && nativeShouldBlock(resourceUrl, currentUrl,
                requestType(request), request.getMethod());
        if (!blocked && (!nativeFiltersReady || !adBlockEnabled)) {
            blocked = isAdHost(request.getUrl().getHost());
        }
        if (!blocked) return null;
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(EMPTY_BODY));
    }

    private void initializeAdblock() {
        synchronized (this) {
            if (!adBlockEnabled) return;
            if (adBlockInitializationRunning) {
                adBlockReloadRequested = true;
                return;
            }
            adBlockInitializationRunning = true;
        }
        adBlockExecutor.execute(() -> {
            try {
                String[] lists = readCachedFilterLists();
                boolean hasCachedLists = lists[0] != null || lists[1] != null;
                if (hasCachedLists && adBlockEnabled) installFilterLists(lists);
                long lastUpdate = getPreferences(MODE_PRIVATE)
                        .getLong("filterListsUpdatedAt", 0);
                if (lists[0] == null || lists[1] == null || !hasCachedLists
                        || System.currentTimeMillis() - lastUpdate >= FILTER_LIST_MAX_AGE) {
                    updateFilterLists(lists);
                }
            } finally {
                boolean retry;
                synchronized (this) {
                    adBlockInitializationRunning = false;
                    retry = adBlockReloadRequested && adBlockEnabled;
                    adBlockReloadRequested = false;
                }
                if (retry) initializeAdblock();
            }
        });
    }

    private String[] readCachedFilterLists() {
        String[] lists = new String[FILTER_LIST_FILES.length];
        for (int i = 0; i < FILTER_LIST_FILES.length; i++) {
            File file = new File(getFilesDir(), FILTER_LIST_FILES[i]);
            if (!file.isFile() || file.length() <= 0 || file.length() > MAX_FILTER_LIST_BYTES) continue;
            try (InputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copyLimited(input, output);
                lists[i] = output.toString(StandardCharsets.UTF_8.name());
                if (!isValidFilterList(lists[i])) lists[i] = null;
            } catch (IOException ignored) {
                lists[i] = null;
            }
        }
        return lists;
    }

    private void updateFilterLists(String[] cachedLists) {
        String[] urls = {EASYLIST_URL, EASYPRIVACY_URL};
        boolean updated = false;
        for (int i = 0; i < urls.length; i++) {
            if (!adBlockEnabled) return;
            String downloaded = downloadFilterList(urls[i]);
            if (downloaded == null) continue;
            try {
                saveFilterList(i, downloaded);
                cachedLists[i] = downloaded;
                updated = true;
            } catch (IOException ignored) {
                // Keep using the in-memory copy if storage is unavailable.
            }
        }
        if (updated && adBlockEnabled) {
            installFilterLists(cachedLists);
            getPreferences(MODE_PRIVATE).edit()
                    .putLong("filterListsUpdatedAt", System.currentTimeMillis()).apply();
        }
    }

    private String downloadFilterList(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "WebMini/1.0 filter-list updater");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK
                    || !"https".equalsIgnoreCase(connection.getURL().getProtocol())) return null;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copyLimited(input, output);
                String contents = output.toString(StandardCharsets.UTF_8.name());
                return isValidFilterList(contents) ? contents : null;
            }
        } catch (IOException ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void copyLimited(InputStream input, ByteArrayOutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_FILTER_LIST_BYTES) throw new IOException("Filter list is too large");
            output.write(buffer, 0, count);
        }
    }

    private void saveFilterList(int index, String contents) throws IOException {
        File destination = new File(getFilesDir(), FILTER_LIST_FILES[index]);
        File temporary = new File(getFilesDir(), FILTER_LIST_FILES[index] + ".tmp");
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILTER_LIST_BYTES) throw new IOException("Filter list is too large");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("Could not replace cached filter list");
        }
    }

    private boolean isValidFilterList(String contents) {
        String trimmed = contents.trim();
        return trimmed.length() >= 256 && trimmed.startsWith("[Adblock Plus ");
    }

    private void installFilterLists(String[] lists) {
        boolean hasList = false;
        for (String list : lists) hasList |= list != null && !list.isEmpty();
        if (!hasList || !adBlockEnabled) return;
        nativeClearFilters();
        nativeFiltersReady = false;
        nativeBeginFilterLists();
        boolean added = false;
        for (String list : lists) {
            if (list != null && !list.isEmpty()) {
                if (!nativeAddFilterList(list)) {
                    nativeClearFilters();
                    return;
                }
                added = true;
            }
        }
        nativeFiltersReady = added && adBlockEnabled && nativeFinishFilterLists();
    }

    private void showConfirmation(String title, String message, String acceptLabel,
                                  String cancelLabel, Runnable onAccept) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(acceptLabel, (dialog, which) -> onAccept.run())
                .setNegativeButton(cancelLabel, null)
                .setCancelable(true)
                .show();
    }

    private void confirmDownload(String url, String userAgent,
                                 String contentDisposition, String mimeType) {
        Uri uri = Uri.parse(url);
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, R.string.download_protocol_blocked, Toast.LENGTH_LONG).show();
            return;
        }
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        String host = uri.getHost() == null ? getString(R.string.download_unknown_host) : uri.getHost();
        String warning = "http".equalsIgnoreCase(uri.getScheme())
                ? getString(R.string.download_http_warning) : "";
        showConfirmation(getString(R.string.download_title),
                getString(R.string.download_details, fileName, host,
                        mimeType == null ? getString(R.string.download_unknown_type) : mimeType, warning),
                getString(R.string.action_download), getString(R.string.action_cancel), () -> {
                    pendingDownloadUrl = url;
                    pendingDownloadUserAgent = userAgent;
                    pendingDownloadReferer = webView == null ? null : webView.getUrl();
                    pendingDownloadName = fileName;
                    pendingDownloadMime = mimeType;
                    startConfirmedDownload();
                });
    }

    private void startConfirmedDownload() {
        if (pendingDownloadUrl == null) return;
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 41);
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(pendingDownloadUrl));
        request.setTitle(pendingDownloadName);
        request.setDescription(getString(R.string.download_description));
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, pendingDownloadName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        if (pendingDownloadMime != null) request.setMimeType(pendingDownloadMime);
        if (pendingDownloadUserAgent != null) request.addRequestHeader("User-Agent", pendingDownloadUserAgent);
        if (pendingDownloadReferer != null) request.addRequestHeader("Referer", pendingDownloadReferer);
        String cookies = CookieManager.getInstance().getCookie(pendingDownloadUrl);
        if (cookies != null) request.addRequestHeader("Cookie", cookies);
        manager.enqueue(request);
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        pendingDownloadUrl = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 41 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startConfirmedDownload();
        } else if (requestCode == 41) {
            pendingDownloadUrl = null;
            Toast.makeText(this, R.string.download_permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void showChoices(String[] entries, Choice choice) {
        if (!tvMode) {
            new AlertDialog.Builder(this)
                    .setItems(entries, (dialog, which) -> choice.select(which)).show();
            return;
        }
        if (menuPanel != null) return;
        menuPanel = new FrameLayout(this);
        menuPanel.setBackgroundColor(0x99000000);
        menuPanel.setOnClickListener(view -> closeMenu());
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(16), dp(16), dp(16));
        list.setBackgroundResource(R.drawable.tv_address);
        list.setClickable(true);
        FrameLayout.LayoutParams panelSize = new FrameLayout.LayoutParams(
                Math.min(dp(620), root.getWidth() - dp(32)), -2, Gravity.CENTER);
        menuPanel.addView(list, panelSize);
        for (int i = 0; i < entries.length; i++) {
            final int index = i;
            Button item = button(entries[i], list);
            item.setOnClickListener(view -> {
                closeMenu();
                choice.select(index);
            });
        }
        root.addView(menuPanel, new FrameLayout.LayoutParams(-1, -1));
        cursorView.bringToFront();
    }

    private void closeMenu() {
        if (menuPanel == null) return;
        root.removeView(menuPanel);
        menuPanel = null;
    }

    private static boolean isAdHost(String host) {
        if (host == null) return false;
        String name = host.toLowerCase(Locale.ROOT);
        for (String domain : AD_HOSTS) {
            if (name.equals(domain) || name.endsWith("." + domain)) return true;
        }
        return false;
    }

    private void openAddress() {
        String input = address.getText().toString().trim();
        if (input.isEmpty()) return;
        String url;
        if (input.startsWith("https://") || input.startsWith("http://")) {
            url = input;
        } else if (input.contains(" ") || !input.contains(".")) {
            try {
                url = "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8");
            } catch (java.io.UnsupportedEncodingException error) {
                throw new AssertionError(error);
            }
        } else {
            url = "https://" + input;
        }
        navigateTo(url);
        address.clearFocus();
    }

    private void hideFullscreen() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        page.setVisibility(View.VISIBLE);
    }

    private void navigateBack() {
        if (menuPanel != null) {
            closeMenu();
        } else if (fullscreenView != null) {
            hideFullscreen();
        } else if (homePage) {
            finish();
        } else if (webView != null && webView.canGoBack()) {
            showLoading();
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("url", currentUrl);
        state.putBoolean("homePage", homePage);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        analogX = 0;
        analogY = 0;
        digitalX = 0;
        digitalY = 0;
        if (cursorView != null) cursorView.removeCallbacks(cursorTick);
        cursorTickScheduled = false;
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.resumeTimers();
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (cursorView != null) {
            cursorView.removeCallbacks(cursorTick);
            cursorView.removeCallbacks(hideCursor);
        }
        cursorTickScheduled = false;
        loadingLine.removeCallbacks(hideLoading);
        closeMenu();
        hideFullscreen();
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.stopLoading();
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        adBlockEnabled = false;
        nativeFiltersReady = false;
        if (Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(null);
        }
        adBlockExecutor.execute(MainActivity::nativeClearFilters);
        adBlockExecutor.shutdown();
        super.onDestroy();
    }
}
