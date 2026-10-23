package br.com.lucaswotta.navegadorleve;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
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
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.ScrollView;
import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.google.com/";
    private static final String ABOUT = "https://github.com/lucaswotta";
    private static final String BLANK = "about:blank";
    private static final String FAVORITES_KEY = "favorites";
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
    private float cursorX = -1;
    private float cursorY = -1;
    private float analogX;
    private float analogY;
    private int digitalX;
    private int digitalY;
    private int digitalSpeed = 6;
    private boolean cursorTickScheduled;
    private String currentUrl = HOME;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tvMode = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        if (tvMode) setTheme(R.style.TvTheme);
        super.onCreate(savedInstanceState);
        adBlockEnabled = getPreferences(MODE_PRIVATE).getBoolean("adBlockEnabled", false);
        popupsBlocked = getPreferences(MODE_PRIVATE).getBoolean("popupsBlocked", true);
        loadFavorites();

        root = new FrameLayout(this);
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
        home.setContentDescription("Página inicial");
        home.setOnClickListener(view -> {
            showHomePage();
        });

        address = new EditText(this);
        address.setHint("Pesquisar ou digitar URL");
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
        back.setContentDescription("Voltar");
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
        reload.setContentDescription("Recarregar");
        reload.setOnClickListener(view -> reloadPage());
        favoriteButton = button("☆", toolbar);
        favoriteButton.setContentDescription("Adicionar aos favoritos");
        favoriteButton.setOnClickListener(view -> toggleFavorite());
        Button menu = button("⋮", toolbar);
        menu.setContentDescription("Opções");
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
        } else {
            createWebView(currentUrl);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::navigateBack);
        }
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

        TextView title = new TextView(this);
        title.setText("WebMini");
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
        homeSearch.setHint("Pesquisar no Google");
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
        favoritesTitle.setText("Favoritos");
        favoritesTitle.setTextColor(Color.WHITE);
        favoritesTitle.setTextSize(18);
        favoritesTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1, -2);
        headingSize.setMargins(0, 0, 0, dp(10));
        landing.addView(favoritesTitle, headingSize);
        if (favorites.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Seus sites favoritos aparecem aqui.");
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
        if (webView == null) createWebView(url);
        else {
            showLoading();
            webView.loadUrl(url);
        }
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
        favoriteButton.setContentDescription(saved ? "Remover dos favoritos" : "Adicionar aos favoritos");
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
        applyPopupPolicy(settings);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                               WebResourceRequest request) {
                if (!adBlockEnabled || request.isForMainFrame()
                        || !isAdHost(request.getUrl().getHost())) return null;
                return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(EMPTY_BODY));
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl,
                                      android.graphics.Bitmap favicon) {
                if (view == webView) {
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
                Toast.makeText(MainActivity.this,
                        "A página parou. Digite a URL para recarregar.", Toast.LENGTH_LONG).show();
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
                if (isUserGesture && !isDialog) {
                    String link = view.getHitTestResult().getExtra();
                    if (link != null && (link.startsWith("https://")
                            || link.startsWith("http://"))) {
                        view.post(() -> webView.loadUrl(link));
                    }
                }
                return false;
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
        String popupLabel = popupsBlocked
                ? "Bloqueio Pop-Up: ligado" : "Bloqueio Pop-Up: desligado";
        String adBlockLabel = adBlockEnabled
                ? "Bloqueio de anúncios: ligado" : "Bloqueio de anúncios: desligado";
        String[] entries = {"Avançar", popupLabel, adBlockLabel, "Sobre"};
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
                if (webView != null) reloadPage();
            }
            if (which == 3) {
                navigateTo(ABOUT);
            }
        });
    }

    private void applyPopupPolicy(WebSettings settings) {
        settings.setSupportMultipleWindows(popupsBlocked);
        settings.setJavaScriptCanOpenWindowsAutomatically(!popupsBlocked);
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
        super.onDestroy();
    }
}
