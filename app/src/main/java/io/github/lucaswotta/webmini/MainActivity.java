package io.github.lucaswotta.webmini;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.webkit.WebStorage;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.widget.ScrollView;
import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.google.com/";
    private static final String ABOUT = "https://github.com/lucaswotta/webmini-androidtv";
    private static final String BLANK = "about:blank";
    private static final String FAVORITES_KEY = "favorites";
    // Two rows of two buttons fit the 720p TV viewport; a third row is clipped.
    private static final int MAX_FAVORITES = 4;
    private static final int VOICE_REQUEST = 7;
    private static final String[][] SEARCH_ENGINES = {
            {"Google", "https://www.google.com/search?q="},
            {"DuckDuckGo", "https://duckduckgo.com/?q="},
            {"Bing", "https://www.bing.com/search?q="},
            {"Brave", "https://search.brave.com/search?q="},
    };
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
    private LinearLayout toolbarView;
    private LinearLayout findBar;
    private EditText findField;
    private TextView findCount;
    private Button findNextButton;
    private boolean cursorShown = true;
    private long edgeScrollStarted;
    private int edgeScrollDirection;
    private long edgeScrollXStarted;
    private int edgeScrollXDirection;
    private final Runnable hideCursor = () -> {
        if (tvMode && cursorShown) {
            cursorView.setVisibility(View.INVISIBLE);
            cursorShown = false;
        }
    };
    private static final long CURSOR_IDLE_MS = 2500;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private FilterEngine filterEngine;
    private boolean popupsBlocked;
    private boolean navigationErrorShown;
    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadReferer;
    private String pendingDownloadName;
    private String pendingDownloadMime;
    private float cursorX = -1;
    private float cursorY = -1;
    private float analogX;
    private float analogY;
    private int digitalX;
    private int digitalY;
    private int digitalSpeed = 6;
    private boolean cursorTickScheduled;
    private String currentUrl = HOME;
    // Snapshot read once per request on WebView's network threads, so a request never mixes
    // the URL of one navigation with the origin or the exemption of another.
    private static final class PageContext {
        final String url;
        final boolean exempt;

        PageContext(String url, boolean exempt) {
            this.url = url;
            this.exempt = exempt;
        }
    }
    private volatile PageContext pageContext = new PageContext(HOME, false);
    // First navigation waiting for the filter engine, so it is not loaded unfiltered.
    private String heldUrl;
    private static final long FILTER_WAIT_MS = 6000;
    private final Runnable releaseHeldNavigation = () -> loadHeldNavigation(true);
    private int restoredScrollY;
    private int nightMode;
    private static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private final java.util.concurrent.atomic.AtomicInteger blockedCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private boolean desktopSite;
    private int textZoom = 100;
    private final java.util.Set<String> exemptOrigins =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final Runnable hideLoading = () -> loadingLine.setVisibility(View.INVISIBLE);
    private final Runnable cursorTick = new Runnable() {
        @Override
        public void run() {
            cursorTickScheduled = false;
            if (!tvMode || homePage || menuPanel != null || (analogX == 0 && analogY == 0
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
        nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        filterEngine = new FilterEngine(getFilesDir(), getPreferences(MODE_PRIVATE),
                installTime(), () -> runOnUiThread(() -> loadHeldNavigation(false)));
        filterEngine.setEnabled(getPreferences(MODE_PRIVATE).getBoolean("adBlockEnabled", true));
        popupsBlocked = getPreferences(MODE_PRIVATE).getBoolean("popupsBlocked", true);
        desktopSite = getPreferences(MODE_PRIVATE).getBoolean("desktopSite", false);
        int savedZoom = getPreferences(MODE_PRIVATE).getInt("textZoom", 100);
        textZoom = savedZoom == 125 || savedZoom == 150 ? savedZoom : 100;
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
        toolbarView = toolbar;
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setBackgroundColor(getResources().getColor(
                tvMode ? R.color.tv_surface : R.color.ui_background));
        toolbar.setPadding(dp(tvMode ? 16 : 4), dp(4), dp(tvMode ? 16 : 4), dp(4));
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
        address.setBackgroundResource(R.drawable.tv_address);
        address.setTextColor(getResources().getColor(tvMode ? R.color.tv_text : R.color.ui_text));
        address.setHintTextColor(getResources().getColor(tvMode ? R.color.tv_muted : R.color.ui_muted));
        address.setTextSize(tvMode ? 17 : 16);
        address.setPadding(dp(16), 0, dp(16), 0);
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
                0, dp(tvMode ? 48 : 44), 1));
        addressBox.addView(address, new FrameLayout.LayoutParams(-1, -1));
        loadingLine = new View(this);
        loadingLine.setBackgroundColor(getResources().getColor(
                tvMode ? R.color.brand_amber : R.color.ui_accent));
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
        createFindBar();

        if (tvMode) {
            View line = new View(this);
            line.setBackgroundColor(getResources().getColor(R.color.tv_border));
            page.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        content = new FrameLayout(this);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        if (tvMode) createCursor();

        String launchUrl = savedInstanceState == null ? incomingUrl(getIntent())
                : savedInstanceState.getString("url");
        setCurrentUrl(launchUrl == null ? HOME : launchUrl);
        if (savedInstanceState != null) restoredScrollY = savedInstanceState.getInt("scrollY");
        if (launchUrl == null || (savedInstanceState != null
                && savedInstanceState.getBoolean("homePage"))) {
            showHomePage();
        } else {
            showHomePage();
            navigateTo(launchUrl);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::navigateBack);
        }
    }

    // Changes on every install or update, which is when a compiled engine may be stale.
    private long installTime() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException error) {
            throw new AssertionError(error);
        }
    }

    private void setCurrentUrl(String url) {
        currentUrl = url;
        String origin = NavigationPolicy.origin(url);
        pageContext = new PageContext(url, origin != null && exemptOrigins.contains(origin));
    }

    private Button button(String label, LinearLayout parent) {
        Button button = new Button(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(
                dp(tvMode ? 48 : 40), dp(tvMode ? 48 : 44));
        button.setAllCaps(false);
        button.setTextSize(24);
        button.setTextColor(getResources().getColorStateList(R.color.tv_button_text, getTheme()));
        button.setBackgroundResource(R.drawable.tv_button);
        button.setStateListAnimator(null);
        button.setPadding(0, 0, 0, 0);
        int icon = 0;
        switch (label) {
            case "⌂": icon = R.drawable.ic_home; break;
            case "←": icon = R.drawable.ic_back; break;
            case "⟳": icon = R.drawable.ic_reload; break;
            case "⋮": icon = R.drawable.ic_menu; break;
            case "▲": icon = R.drawable.ic_find_previous; break;
            case "▼": icon = R.drawable.ic_find_next; break;
            case "✕": icon = R.drawable.ic_close; break;
        }
        if (icon != 0) {
            button.setText("");
            button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            int inset = (tvMode ? 48 : 40) / 2 - 12;
            button.setPadding(dp(inset), 0, dp(inset), 0);
        }
        parent.addView(button, size);
        return button;
    }

    // Built once and hidden, so reopening it keeps the last query.
    private void createFindBar() {
        findBar = new LinearLayout(this);
        findBar.setGravity(Gravity.CENTER);
        findBar.setBackgroundColor(getResources().getColor(
                tvMode ? R.color.tv_surface : R.color.ui_background));
        findBar.setPadding(dp(tvMode ? 16 : 4), dp(4), dp(tvMode ? 16 : 4), dp(4));
        findBar.setVisibility(View.GONE);
        page.addView(findBar, new LinearLayout.LayoutParams(-1, -2));
        findField = new EditText(this);
        findField.setHint(R.string.menu_find);
        findField.setSingleLine(true);
        findField.setGravity(Gravity.CENTER_VERTICAL);
        findField.setSelectAllOnFocus(true);
        findField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        findField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        findField.setBackgroundResource(R.drawable.tv_address);
        findField.setTextColor(getResources().getColor(tvMode ? R.color.tv_text : R.color.ui_text));
        findField.setHintTextColor(getResources().getColor(tvMode ? R.color.tv_muted : R.color.ui_muted));
        findField.setTextSize(tvMode ? 17 : 16);
        findField.setPadding(dp(16), 0, dp(16), 0);
        findField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                findInPage();
                return true;
            }
            return false;
        });
        findBar.addView(findField, new LinearLayout.LayoutParams(0, dp(tvMode ? 48 : 44), 1));
        findCount = new TextView(this);
        findCount.setTextColor(getResources().getColor(tvMode ? R.color.tv_muted : R.color.ui_muted));
        findCount.setTextSize(16);
        findCount.setGravity(Gravity.CENTER);
        findCount.setMinWidth(dp(72));
        findCount.setPadding(dp(8), 0, dp(8), 0);
        findBar.addView(findCount, new LinearLayout.LayoutParams(-2, -2));
        Button previous = button("▲", findBar);
        previous.setContentDescription(getString(R.string.find_previous));
        previous.setOnClickListener(view -> {
            if (webView != null) webView.findNext(false);
        });
        findNextButton = button("▼", findBar);
        findNextButton.setContentDescription(getString(R.string.find_next));
        findNextButton.setOnClickListener(view -> {
            if (webView != null) webView.findNext(true);
        });
        Button close = button("✕", findBar);
        close.setContentDescription(getString(R.string.find_close));
        close.setOnClickListener(view -> closeFindBar());
    }

    private boolean findBarShown() {
        return findBar != null && findBar.getVisibility() == View.VISIBLE;
    }

    private void showFindBar() {
        if (webView == null) return;
        webView.setFindListener((active, total, done) -> findCount.setText(
                getString(R.string.find_count, total == 0 ? 0 : active + 1, total)));
        findCount.setText("");
        findBar.setVisibility(View.VISIBLE);
        findField.requestFocus();
        // The keyboard only attaches to a field that has been laid out.
        findField.post(() -> {
            if (findField.isFocused()) showSoftKeyboard(findField);
        });
    }

    private void findInPage() {
        String query = findField.getText().toString();
        if (webView == null || query.isEmpty()) return;
        webView.findAllAsync(query);
        hideSoftKeyboard(findField);
        // Each OK on this button then jumps to the next match.
        findNextButton.requestFocus();
    }

    private void closeFindBar() {
        if (!findBarShown()) return;
        // Hiding the focused row would hand focus to the first toolbar button.
        if (focusInside(findBar)) {
            hideSoftKeyboard(findField);
            focusPage();
        }
        findBar.setVisibility(View.GONE);
        if (webView != null) webView.clearMatches();
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
        closeFindBar();
        if (webView != null) {
            webView.stopLoading();
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        homePage = true;
        heldUrl = null;
        root.removeCallbacks(releaseHeldNavigation);
        loadingLine.removeCallbacks(hideLoading);
        loadingLine.setVisibility(View.INVISIBLE);
        stopPointer();
        if (address.isFocused()) address.clearFocus();
        setCurrentUrl(HOME);
        address.setText("");
        favoriteButton.setText("☆");
        favoriteButton.setEnabled(false);
        content.removeAllViews();

        boolean compact = tvMode && getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density < 440;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getResources().getColor(
                tvMode ? android.R.color.black : R.color.ui_background));
        LinearLayout landing = new LinearLayout(this);
        landing.setOrientation(LinearLayout.VERTICAL);
        landing.setGravity(Gravity.CENTER);
        landing.setPadding(dp(tvMode ? 28 : 20), dp(compact ? 8 : 24),
                dp(tvMode ? 28 : 20), dp(compact ? 8 : 20));
        scroll.addView(landing, new ScrollView.LayoutParams(-1, -2));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER);
        landing.addView(brand, new LinearLayout.LayoutParams(-1, dp(compact ? 52 : 80)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int markSize = compact ? 52 : 80;
        LinearLayout.LayoutParams logoSize = new LinearLayout.LayoutParams(dp(markSize), dp(markSize));
        logoSize.setMarginEnd(dp(16));
        brand.addView(logo, logoSize);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(getResources().getColor(tvMode ? R.color.tv_text : R.color.ui_text));
        title.setTextSize(compact ? 30 : 40);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setLetterSpacing(0.035f);
        title.setIncludeFontPadding(false);
        brand.addView(title, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchRowSize = new LinearLayout.LayoutParams(-1, dp(compact ? 56 : 64));
        searchRowSize.setMargins(0, dp(compact ? 8 : 20), 0, dp(compact ? 8 : 24));
        landing.addView(searchRow, searchRowSize);
        homeSearch = new EditText(this);
        homeSearch.setSingleLine(true);
        homeSearch.setHint(getString(R.string.home_search_hint,
                SEARCH_ENGINES[searchEngineIndex()][0]));
        homeSearch.setTextColor(getResources().getColor(tvMode ? R.color.tv_text : R.color.ui_text));
        homeSearch.setHintTextColor(getResources().getColor(tvMode ? R.color.tv_muted : R.color.ui_muted));
        homeSearch.setTextSize(tvMode ? 20 : 18);
        homeSearch.setPadding(dp(16), 0, dp(12), 0);
        homeSearch.setBackgroundResource(R.drawable.home_search);
        homeSearch.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        homeSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        homeSearch.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                search(homeSearch.getText().toString());
                return true;
            }
            return false;
        });
        searchRow.addView(homeSearch, new LinearLayout.LayoutParams(0, -1, 1));
        if (voiceSearchAvailable()) {
            ImageButton micButton = new ImageButton(this);
            micButton.setImageResource(R.drawable.ic_mic);
            micButton.setBackgroundResource(R.drawable.tv_button);
            micButton.setPadding(dp(14), dp(14), dp(14), dp(14));
            micButton.setStateListAnimator(null);
            micButton.setContentDescription(getString(R.string.voice_search));
            micButton.setOnClickListener(view -> startVoiceSearch());
            LinearLayout.LayoutParams micSize = new LinearLayout.LayoutParams(dp(52), -1);
            micSize.setMarginStart(dp(8));
            searchRow.addView(micButton, micSize);
        }
        Button searchButton = new Button(this);
        searchButton.setText("");
        searchButton.setContentDescription(getString(R.string.action_search));
        searchButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        searchButton.setPadding(dp(14), 0, dp(14), 0);
        searchButton.setMinWidth(0);
        searchButton.setMinimumWidth(0);
        searchButton.setBackgroundResource(R.drawable.tv_button);
        searchButton.setStateListAnimator(null);
        searchButton.setOnClickListener(view -> search(homeSearch.getText().toString()));
        LinearLayout.LayoutParams submitSize = new LinearLayout.LayoutParams(dp(52), -1);
        submitSize.setMarginStart(dp(8));
        searchRow.addView(searchButton, submitSize);

        TextView favoritesTitle = new TextView(this);
        favoritesTitle.setText(R.string.favorites_title);
        favoritesTitle.setTextColor(getResources().getColor(tvMode ? R.color.tv_muted : R.color.ui_muted));
        favoritesTitle.setTextSize(16);
        favoritesTitle.setLetterSpacing(0.06f);
        favoritesTitle.setIncludeFontPadding(false);
        favoritesTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1, -2);
        headingSize.setMargins(0, 0, 0, dp(8));
        landing.addView(favoritesTitle, headingSize);
        if (favorites.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.favorites_empty);
            empty.setTextColor(getResources().getColor(tvMode ? R.color.tv_muted : R.color.ui_muted));
            empty.setTextSize(16);
            landing.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            int shownFavorites = Math.min(favorites.size(), MAX_FAVORITES);
            for (int i = 0; i < shownFavorites; i += 2) {
                LinearLayout row = new LinearLayout(this);
                // A lone favorite keeps half the row instead of stretching across it.
                row.setWeightSum(2);
                LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, dp(compact ? 56 : 64));
                rowSize.setMargins(-dp(4), 0, -dp(4), dp(4));
                landing.addView(row, rowSize);
                for (int slot = i; slot < Math.min(i + 2, shownFavorites); slot++) {
                    String url = favorites.get(slot);
                    Button item = new Button(this);
                    String host = Uri.parse(url).getHost();
                    String displayHost = host == null ? url : host;
                    String prefix = tvMode ? String.format(Locale.ROOT, "%02d   ", slot + 1) : "";
                    SpannableString destination = new SpannableString(prefix + displayHost);
                    android.content.res.ColorStateList secondary = getResources()
                            .getColorStateList(R.color.tv_button_secondary, getTheme());
                    if (!prefix.isEmpty()) {
                        destination.setSpan(new TextAppearanceSpan("sans-serif", Typeface.NORMAL,
                                -1, secondary, null), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    int hostStart = prefix.length();
                    if (displayHost.startsWith("www.")) {
                        destination.setSpan(new TextAppearanceSpan(null, Typeface.NORMAL, -1,
                                secondary, null), hostStart, hostStart + 4,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        hostStart += 4;
                    }
                    int suffix = displayHost.indexOf('.', hostStart - prefix.length());
                    if (host != null && suffix >= 0) {
                        destination.setSpan(new TextAppearanceSpan(null, Typeface.NORMAL, -1,
                                secondary, null), prefix.length() + suffix, destination.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    item.setText(destination);
                    item.setContentDescription(displayHost);
                    item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    item.setTextColor(getResources().getColorStateList(R.color.tv_button_text, getTheme()));
                    item.setAllCaps(false);
                    item.setTextSize(tvMode ? 18 : 16);
                    item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    item.setSingleLine(true);
                    item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    item.setPadding(dp(tvMode ? 18 : 12), 0, dp(tvMode ? 18 : 12), 0);
                    item.setStateListAnimator(null);
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
        if (tvMode) {
            // Hold focus on the container so no button looks selected and the
            // keyboard stays closed until the user moves to the search field.
            scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            scroll.setFocusable(true);
            scroll.post(() -> { if (homePage) scroll.requestFocus(); });
        }
    }

    private int searchEngineIndex() {
        int saved = getPreferences(MODE_PRIVATE).getInt("searchEngine", 0);
        return saved >= 0 && saved < SEARCH_ENGINES.length ? saved : 0;
    }

    private String searchUrl(String query) {
        try {
            return SEARCH_ENGINES[searchEngineIndex()][1] + URLEncoder.encode(query, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            throw new AssertionError(error);
        }
    }

    private void search(String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return;
        navigateTo(searchUrl(trimmed));
    }

    private void chooseSearchEngine() {
        String[] names = new String[SEARCH_ENGINES.length];
        for (int i = 0; i < names.length; i++) names[i] = SEARCH_ENGINES[i][0];
        showChoices(names, which -> {
            getPreferences(MODE_PRIVATE).edit().putInt("searchEngine", which).apply();
            if (homeSearch != null) {
                homeSearch.setHint(getString(R.string.home_search_hint, SEARCH_ENGINES[which][0]));
            }
        });
    }

    private boolean voiceSearchAvailable() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .resolveActivity(getPackageManager()) != null;
    }

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (android.content.ActivityNotFoundException ignored) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VOICE_REQUEST || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> spoken = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (spoken != null && !spoken.isEmpty()) search(spoken.get(0));
    }

    private void navigateTo(String url) {
        // shouldOverrideUrlLoading does not run for loadUrl, so typed addresses,
        // favorites and links from other apps are checked here.
        if (NavigationPolicy.decide(url) == NavigationPolicy.Decision.BLOCK) {
            Toast.makeText(this, R.string.navigation_blocked, Toast.LENGTH_SHORT).show();
            return;
        }
        if (needsHttpWarning(url)) {
            confirmHttp(url);
            return;
        }
        loadConfirmedUrl(url);
    }

    private void loadConfirmedUrl(String url) {
        root.removeCallbacks(releaseHeldNavigation);
        if (filterEngine.isEnabled() && !filterEngine.isReady() && filterEngine.isLoading()) {
            heldUrl = url;
            address.setText(url);
            showLoading();
            Toast.makeText(this, R.string.filter_preparing, Toast.LENGTH_SHORT).show();
            root.postDelayed(releaseHeldNavigation, FILTER_WAIT_MS);
            return;
        }
        heldUrl = null;
        if (webView == null) createWebView(url);
        else {
            showLoading();
            webView.loadUrl(url);
        }
    }

    private void loadHeldNavigation(boolean timedOut) {
        if (heldUrl == null || isFinishing() || isDestroyed()) return;
        if (!timedOut && !filterEngine.isReady() && filterEngine.isLoading()) return;
        String url = heldUrl;
        heldUrl = null;
        root.removeCallbacks(releaseHeldNavigation);
        if (filterEngine.isEnabled() && !filterEngine.isReady()) {
            Toast.makeText(this, R.string.filter_not_ready, Toast.LENGTH_LONG).show();
        }
        if (webView == null) createWebView(url);
        else webView.loadUrl(url);
    }

    private boolean needsHttpWarning(String url) {
        return NavigationPolicy.decide(url) == NavigationPolicy.Decision.CONFIRM_HTTP;
    }

    private void confirmHttp(String url) {
        showConfirmation(getString(R.string.http_warning_title),
                getString(R.string.http_warning_message, Uri.parse(url).getHost()),
                getString(R.string.action_continue), getString(R.string.action_cancel),
                () -> loadConfirmedUrl(url));
    }

    private boolean overrideNavigation(String url, boolean mainFrame, String method) {
        NavigationPolicy.Decision decision = NavigationPolicy.decide(url, mainFrame, method);
        if (decision == NavigationPolicy.Decision.ALLOW) return false;
        if (decision == NavigationPolicy.Decision.CONFIRM_HTTP) {
            confirmHttp(url);
        } else {
            Toast.makeText(this, R.string.navigation_blocked, Toast.LENGTH_SHORT).show();
        }
        return true;
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
        if (favorites.contains(currentUrl)) {
            favorites.remove(currentUrl);
        } else if (favorites.size() >= MAX_FAVORITES) {
            // The grid has no free slot, so the page enters by replacing a stored favorite.
            chooseFavorite(getString(R.string.favorites_full, MAX_FAVORITES), which -> {
                favorites.set(which, currentUrl);
                persistFavorites();
                updateFavoriteButton();
            });
            return;
        } else {
            favorites.add(0, currentUrl);
        }
        persistFavorites();
        updateFavoriteButton();
    }

    private void chooseFavorite(String title, Choice choice) {
        String[] entries = favorites.toArray(new String[0]);
        showChoices(title, entries, choice);
    }

    private void removeFavorite() {
        chooseFavorite(getString(R.string.menu_remove_favorite), which -> {
            favorites.remove(which);
            persistFavorites();
            if (homePage) showHomePage();
            else updateFavoriteButton();
            Toast.makeText(this, R.string.favorite_removed, Toast.LENGTH_SHORT).show();
        });
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
        navigationErrorShown = false;
        webView = new WebView(this);
        webView.resumeTimers();
        if (tvMode) webView.setBackgroundColor(Color.BLACK);
        WebSettings settings = webView.getSettings();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        applyPopupPolicy(settings);
        applyViewSettings(settings);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                               WebResourceRequest request) {
                return filterNetworkRequest(request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return overrideNavigation(request.getUrl().toString(), request.isForMainFrame(),
                        request.getMethod());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return overrideNavigation(url, true, "GET");
            }

            @Override
            public void onFormResubmission(WebView view, Message dontResend, Message resend) {
                dontResend.sendToTarget();
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl,
                                      android.graphics.Bitmap favicon) {
                if (view == webView && !BLANK.equals(pageUrl)) {
                    navigationErrorShown = false;
                    blockedCount.set(0);
                    setCurrentUrl(pageUrl);
                    if (loadingLine.getVisibility() != View.VISIBLE) showLoading();
                }
                if (view == webView) closeFindBar();
                if (view == webView && tvMode) view.requestFocus();
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (view == webView && !pageUrl.equals(BLANK)) {
                    setCurrentUrl(pageUrl);
                    address.setText(pageUrl);
                    updateFavoriteButton();
                    if (restoredScrollY > 0) {
                        int scrollY = restoredScrollY;
                        restoredScrollY = 0;
                        // The page has no height to scroll until its first frame is drawn.
                        view.postVisualStateCallback(0, new WebView.VisualStateCallback() {
                            @Override
                            public void onComplete(long requestId) {
                                if (view == webView) view.scrollTo(0, scrollY);
                            }
                        });
                    }
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
                String stoppedUrl = currentUrl;
                showNavigationError(webView, () -> loadConfirmedUrl(stoppedUrl));
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                // Only transport failures land here. An HTTP error status comes with the
                // server's own document, which stays on screen.
                if (request.isForMainFrame()) {
                    showNavigationError(view, "GET".equalsIgnoreCase(request.getMethod())
                            ? MainActivity.this::reloadPage : null);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (view != webView || loadingLine.getVisibility() != View.VISIBLE) return;
                // A new animation per progress step would restart on every callback.
                loadingLine.animate().cancel();
                loadingLine.setScaleX(Math.max(0.2f, progress / 100f));
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
        // Keep focus on the page; otherwise it stays highlighted on the Home button.
        if (tvMode) webView.requestFocus();
        homePage = false;
        address.setText(url.equals(BLANK) ? currentUrl : url);
        updateFavoriteButton();
        webView.loadUrl(url);
    }

    private void showNavigationError(WebView failedView, Runnable retry) {
        boolean canRetry = retry != null;
        String failedUrl = currentUrl;
        failedView.post(() -> {
            if (failedView != webView || isFinishing() || isDestroyed()
                    || !failedUrl.equals(currentUrl) || navigationErrorShown) return;
            navigationErrorShown = true;
            loadingLine.removeCallbacks(hideLoading);
            loadingLine.setVisibility(View.INVISIBLE);
            Toast.makeText(this, R.string.page_failed, Toast.LENGTH_LONG).show();
            String[] actions = canRetry
                    ? new String[]{getString(R.string.nav_reload), getString(R.string.edit_address),
                            getString(R.string.nav_home)}
                    : new String[]{getString(R.string.edit_address), getString(R.string.nav_home)};
            showChoices(actions, which -> {
                if (failedView != webView || !failedUrl.equals(currentUrl)) return;
                if (canRetry && which == 0) retry.run();
                else if (which == (canRetry ? 1 : 0)) {
                    address.setText(failedUrl);
                    address.requestFocus();
                    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                            .showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
                } else showHomePage();
            });
            // The failed page must not show through the panel (Chromium's error icon).
            if (menuPanel != null) {
                android.graphics.drawable.LayerDrawable cover =
                        new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                                new android.graphics.drawable.ColorDrawable(Color.BLACK)});
                cover.setLayerInset(0, 0, content.getTop(), 0, 0);
                menuPanel.setBackground(cover);
            }
        });
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
        float nextX = cursorX + horizontal;
        int sideDirection = nextX > root.getWidth() - dp(28) ? 1 : nextX < 0 ? -1 : 0;
        if (sideDirection != 0 && webView != null) {
            long now = SystemClock.uptimeMillis();
            if (edgeScrollXDirection != sideDirection) {
                edgeScrollXDirection = sideDirection;
                edgeScrollXStarted = now;
            }
            webView.scrollBy(sideDirection
                    * dp(3 + Math.min(17, (int) ((now - edgeScrollXStarted) / 180))), 0);
        } else {
            edgeScrollXDirection = 0;
            edgeScrollXStarted = 0;
        }
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
            // Focus drives the home screen, menus and the toolbar, so the stick only
            // moves the pointer over the web page.
            if (homePage || menuPanel != null || toolbarHasFocus()
                    || focusedEditor() != null || keyboardAcceptingText()) {
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
        int key = event.getKeyCode();
        if (jumpPage(event)) return true;
        EditText editor = tvMode ? focusedEditor() : null;
        if (tvMode && key == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                // Leaving a half-typed address or the toolbar first keeps Back from
                // navigating away while the user is still editing.
                if (editor != null && editor != findField) leaveEditor(editor);
                else if (toolbarHasFocus() && !focusInside(findBar) && menuPanel == null) {
                    leaveToolbar();
                } else navigateBack();
            }
            return true;
        }
        if (tvMode && editor == null && keyboardAcceptingText()) {
            return super.dispatchKeyEvent(event);
        }
        if (editor != null) {
            digitalX = 0;
            digitalY = 0;
            if (cursorView != null) cursorView.removeCallbacks(cursorTick);
            cursorTickScheduled = false;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int direction = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (moveTextCaret(editor, direction)) return true;
                    // At the text edge the key hands focus to the neighbouring button.
                    return super.dispatchKeyEvent(event);
                }
                return true;
            }
            if (!homePage && (key == KeyEvent.KEYCODE_DPAD_DOWN
                            || (key == KeyEvent.KEYCODE_DPAD_UP && editor == findField))
                    && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    hideSoftKeyboard(editor);
                    address.requestFocus();
                } else if (editor == address && findBarShown()) {
                    hideSoftKeyboard(editor);
                    findField.requestFocus();
                } else {
                    leaveEditor(editor);
                }
                return true;
            }
            if (!keyboardAcceptingText() && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0
                    && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                            || key == KeyEvent.KEYCODE_BUTTON_A)) {
                showSoftKeyboard(editor);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (tvMode && toolbarHasFocus() && menuPanel == null && !homePage) {
            if (key == KeyEvent.KEYCODE_DPAD_UP
                    || (key == KeyEvent.KEYCODE_DPAD_DOWN
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                boolean inFindBar = focusInside(findBar);
                if (key == KeyEvent.KEYCODE_DPAD_UP && inFindBar
                        && event.getAction() == KeyEvent.ACTION_DOWN) {
                    address.requestFocus();
                } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && !inFindBar && findBarShown()) {
                    findField.requestFocus();
                } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    leaveToolbar();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        // The home screen has no page to point at, so the D-pad moves focus between its buttons.
        if (tvMode && menuPanel == null && !homePage) {
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                    || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                    || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                    || key == KeyEvent.KEYCODE_BUTTON_A) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (key == KeyEvent.KEYCODE_DPAD_UP && event.getRepeatCount() == 0
                            && fullscreenView == null
                            && (webView == null || webView.getScrollY() == 0)) {
                        // Nothing scrolls at the top of the page, so the key reaches the
                        // address bar instead of the pointer.
                        address.requestFocus();
                        if (cursorView != null) cursorView.removeCallbacks(hideCursor);
                        hideCursor.run();
                        return true;
                    }
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

    // Long pages are slow to cross with the pointer. While a field is being edited the same
    // keys belong to the text, and in fullscreen to the video.
    private boolean jumpPage(KeyEvent event) {
        int key = event.getKeyCode();
        boolean down = key == KeyEvent.KEYCODE_PAGE_DOWN || key == KeyEvent.KEYCODE_CHANNEL_DOWN
                || key == KeyEvent.KEYCODE_MOVE_END;
        boolean up = key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_CHANNEL_UP
                || key == KeyEvent.KEYCODE_MOVE_HOME;
        if ((!down && !up) || webView == null || homePage || menuPanel != null
                || fullscreenView != null || focusedEditor() != null || keyboardAcceptingText()) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean whole = key == KeyEvent.KEYCODE_MOVE_HOME || key == KeyEvent.KEYCODE_MOVE_END;
            if (down) webView.pageDown(whole);
            else webView.pageUp(whole);
        }
        return true;
    }

    private EditText focusedEditor() {
        if (address != null && address.isFocused()) return address;
        if (homeSearch != null && homeSearch.isFocused()) return homeSearch;
        if (findField != null && findField.isFocused()) return findField;
        return null;
    }

    private boolean keyboardAcceptingText() {
        InputMethodManager inputMethod = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        return inputMethod != null && inputMethod.isAcceptingText();
    }

    private void showSoftKeyboard(EditText editor) {
        InputMethodManager inputMethod = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethod != null) {
            inputMethod.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // The find bar counts as toolbar: in both rows the D-pad moves focus, not the pointer.
    private boolean toolbarHasFocus() {
        return focusInside(toolbarView) || focusInside(findBar);
    }

    // Derived from the real focus so it cannot drift from what the user sees.
    private boolean focusInside(View container) {
        View focused = getCurrentFocus();
        while (focused != null && container != null) {
            if (focused == container) return true;
            focused = focused.getParent() instanceof View ? (View) focused.getParent() : null;
        }
        return false;
    }

    // clearFocus() would hand focus back to the first toolbar button, so move it to the page.
    private void focusPage() {
        if (webView != null) webView.requestFocus();
    }

    private void hideSoftKeyboard(View view) {
        InputMethodManager inputMethod = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethod != null) {
            inputMethod.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void leaveEditor(EditText editor) {
        focusPage();
        hideSoftKeyboard(editor);
        if (!homePage) revealCursor();
    }

    private void leaveToolbar() {
        focusPage();
        revealCursor();
    }

    // Returns false when the caret is already at the edge, so the key can move focus instead.
    private boolean moveTextCaret(EditText editor, int direction) {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end < 0) return false;
        if (start != end) {
            editor.setSelection(direction < 0 ? Math.min(start, end) : Math.max(start, end));
            return true;
        }
        if ((direction < 0 && start == 0) || (direction > 0 && start == editor.length())) {
            return false;
        }
        int next = Character.offsetByCodePoints(editor.getText(), start, direction);
        editor.setSelection(next);
        return true;
    }

    private int adBlockStateLabel() {
        if (!filterEngine.isEnabled()) return R.string.tracker_block_disabled;
        if (filterEngine.isReady() && filterEngine.isComplete()) {
            return R.string.tracker_block_enabled;
        }
        return filterEngine.isLoading()
                ? R.string.tracker_block_loading : R.string.tracker_block_reduced;
    }

    private void showMenu() {
        String adBlockLabel = getString(adBlockStateLabel());
        int blocked = blockedCount.get();
        if (filterEngine.isEnabled() && blocked > 0 && !homePage) adBlockLabel += " (" + blocked + ")";
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.menu_forward));
        actions.add(() -> {
            if (webView != null && webView.canGoForward()) webView.goForward();
        });
        if (!homePage && webView != null) {
            labels.add(getString(R.string.menu_find));
            actions.add(this::showFindBar);
            labels.add(getString(R.string.menu_go_to));
            actions.add(this::goToMenu);
        }
        labels.add(getString(popupsBlocked
                ? R.string.popup_block_enabled : R.string.popup_block_disabled));
        actions.add(() -> {
            popupsBlocked = !popupsBlocked;
            getPreferences(MODE_PRIVATE).edit().putBoolean("popupsBlocked", popupsBlocked).apply();
            if (webView != null) applyPopupPolicy(webView.getSettings());
        });
        labels.add(adBlockLabel);
        actions.add(this::adBlockMenu);
        labels.add(getString(R.string.menu_preferences));
        actions.add(this::preferencesMenu);
        if (!favorites.isEmpty()) {
            labels.add(getString(R.string.menu_remove_favorite));
            actions.add(this::removeFavorite);
        }
        labels.add(getString(R.string.menu_clear_data));
        actions.add(this::clearDataMenu);
        labels.add(getString(R.string.menu_about));
        actions.add(() -> navigateTo(ABOUT));
        showChoices(labels.toArray(new String[0]), which -> actions.get(which).run());
    }

    // The same jumps as the page and channel keys, for remotes that lack them.
    private void goToMenu() {
        String[] entries = {getString(R.string.go_top), getString(R.string.go_screen_up),
                getString(R.string.go_screen_down), getString(R.string.go_bottom)};
        showChoices(entries, which -> {
            if (webView == null) return;
            if (which <= 1) webView.pageUp(which == 0);
            else webView.pageDown(which == 3);
        });
    }

    private void preferencesMenu() {
        String[] entries = {
                getString(R.string.menu_search_engine, SEARCH_ENGINES[searchEngineIndex()][0]),
                getString(desktopSite ? R.string.desktop_site_on : R.string.desktop_site_off),
                getString(R.string.text_size, textZoom)};
        showChoices(entries, which -> {
            if (which == 0) chooseSearchEngine();
            if (which == 1) {
                desktopSite = !desktopSite;
                getPreferences(MODE_PRIVATE).edit().putBoolean("desktopSite", desktopSite).apply();
                if (webView != null) {
                    applyViewSettings(webView.getSettings());
                    reloadPage();
                }
            }
            if (which == 2) {
                textZoom = textZoom >= 150 ? 100 : textZoom + 25;
                getPreferences(MODE_PRIVATE).edit().putInt("textZoom", textZoom).apply();
                if (webView != null) applyViewSettings(webView.getSettings());
            }
        });
    }

    // Phones get the mobile layout by default; a TV at couch distance often reads better with
    // the desktop one and a larger text.
    private void applyViewSettings(WebSettings settings) {
        settings.setTextZoom(textZoom);
        settings.setUserAgentString(desktopSite ? DESKTOP_USER_AGENT : null);
        settings.setUseWideViewPort(desktopSite);
        settings.setLoadWithOverviewMode(desktopSite);
    }

    private void adBlockMenu() {
        String origin = NavigationPolicy.origin(currentUrl);
        boolean siteMenu = filterEngine.isEnabled() && !homePage && origin != null;
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        labels.add(getString(adBlockStateLabel()));
        actions.add(this::toggleAdBlock);
        if (siteMenu) {
            boolean exempt = exemptOrigins.contains(origin);
            labels.add(getString(exempt ? R.string.site_filter_off : R.string.site_filter_on,
                    Uri.parse(currentUrl).getHost()));
            actions.add(() -> {
                if (exempt) exemptOrigins.remove(origin);
                else exemptOrigins.add(origin);
                setCurrentUrl(currentUrl);
                reloadPage();
            });
        }
        labels.add(getString(R.string.regional_list, regionalListName()));
        actions.add(this::chooseRegionalList);
        showChoices(labels.toArray(new String[0]), which -> actions.get(which).run());
    }

    private void toggleAdBlock() {
        boolean enabled = !filterEngine.isEnabled();
        getPreferences(MODE_PRIVATE).edit().putBoolean("adBlockEnabled", enabled).apply();
        filterEngine.setEnabled(enabled);
        if (webView != null) reloadPage();
    }

    private String regionalListName() {
        switch (filterEngine.regionalChoice()) {
            case "pt": return getString(R.string.regional_pt);
            case "es": return getString(R.string.regional_es);
            case "none": return getString(R.string.regional_none);
            default: return getString(R.string.regional_auto);
        }
    }

    private void chooseRegionalList() {
        String[] names = {getString(R.string.regional_auto), getString(R.string.regional_pt),
                getString(R.string.regional_es), getString(R.string.regional_none)};
        showChoices(names, which -> {
            getPreferences(MODE_PRIVATE).edit()
                    .putString("regionalList", FilterEngine.REGIONAL_CHOICES[which]).apply();
            filterEngine.load();
        });
    }

    private void clearDataMenu() {
        String[] entries = {getString(R.string.clear_cache), getString(R.string.clear_site_data),
                getString(R.string.clear_everything)};
        showChoices(entries, which -> {
            if (which == 0 || which == 2) clearWebViewCache();
            if (which == 0) {
                Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show();
                return;
            }
            WebStorage.getInstance().deleteAllData();
            CookieManager.getInstance().removeAllCookies(removed -> {
                CookieManager.getInstance().flush();
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show();
                if (webView != null && !homePage) reloadPage();
            });
        });
    }

    // The home screen has no WebView, so a short-lived one reaches the shared HTTP cache.
    private void clearWebViewCache() {
        WebView target = webView != null ? webView : new WebView(this);
        target.clearCache(true);
        target.clearFormData();
        if (target != webView) target.destroy();
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
        if (request.isForMainFrame()
                && NavigationPolicy.blocksHttpForm(request.getUrl().toString(), request.getMethod())) {
            runOnUiThread(() -> Toast.makeText(this, R.string.http_form_blocked,
                    Toast.LENGTH_LONG).show());
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                    new java.util.HashMap<>(), new ByteArrayInputStream(EMPTY_BODY));
        }
        if (!filterEngine.isEnabled() || request.isForMainFrame()) return null;
        PageContext context = pageContext;
        if (context.exempt) return null;
        boolean blocked = filterEngine.isReady()
                ? filterEngine.shouldBlock(request.getUrl().toString(), context.url,
                        requestType(request), request.getMethod())
                : isAdHost(request.getUrl().getHost());
        if (!blocked) return null;
        blockedCount.incrementAndGet();
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(EMPTY_BODY));
    }

    private void showConfirmation(String title, String message, String acceptLabel,
                                  String cancelLabel, Runnable onAccept) {
        AlertDialog dialog = (tvMode ? new AlertDialog.Builder(this, R.style.TvDialog)
                : new AlertDialog.Builder(this))
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(acceptLabel, (view, which) -> onAccept.run())
                .setNegativeButton(cancelLabel, null)
                .setCancelable(true)
                .create();
        dialog.show();
        if (!tvMode) return;
        // Same amber focus as the rest of the interface instead of the system pill.
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            Button action = dialog.getButton(which);
            action.setBackgroundResource(R.drawable.tv_button);
            action.setTextColor(getResources().getColorStateList(R.color.tv_button_text, getTheme()));
        }
    }

    private void confirmDownload(String url, String userAgent,
                                 String contentDisposition, String mimeType) {
        Uri uri = Uri.parse(url);
        if (NavigationPolicy.decide(url) == NavigationPolicy.Decision.BLOCK) {
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
                    pendingDownloadReferer = webView == null ? null
                            : NavigationPolicy.downloadReferer(webView.getUrl(), url);
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
        showChoices(null, entries, choice);
    }

    private void showChoices(String title, String[] entries, Choice choice) {
        if (!tvMode) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setItems(entries, (panel, which) -> choice.select(which));
            if (title != null) dialog.setTitle(title);
            dialog.show();
            return;
        }
        if (menuPanel != null) return;
        stopPointer();
        menuPanel = new FrameLayout(this);
        menuPanel.setBackgroundColor(0x99000000);
        menuPanel.setOnClickListener(view -> closeMenu());
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(12), dp(12), dp(12));
        list.setBackgroundResource(R.drawable.menu_panel);
        list.setClickable(true);
        if (title != null) {
            TextView heading = new TextView(this);
            heading.setText(title);
            heading.setTextColor(getResources().getColor(R.color.tv_muted));
            heading.setTextSize(16);
            heading.setPadding(dp(20), dp(4), dp(20), dp(8));
            LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1, -2);
            headingSize.bottomMargin = dp(8);
            list.addView(heading, headingSize);
        }
        FrameLayout.LayoutParams panelSize = new FrameLayout.LayoutParams(
                Math.min(dp(600), root.getWidth() - dp(48)), -2, Gravity.CENTER);
        View panelContent = list;
        // Eight rows plus padding already pass a 720p TV, so a longer list scrolls.
        if (root.getHeight() > 0 && entries.length + (title == null ? 0 : 1) > 7) {
            ScrollView scroll = new ScrollView(this);
            scroll.addView(list);
            panelContent = scroll;
            panelSize.height = root.getHeight() - dp(80);
        }
        menuPanel.addView(panelContent, panelSize);
        View firstItem = null;
        for (int i = 0; i < entries.length; i++) {
            final int index = i;
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(getResources().getColor(R.color.tv_divider));
                LinearLayout.LayoutParams dividerSize = new LinearLayout.LayoutParams(-1, dp(1));
                dividerSize.setMargins(dp(20), 0, dp(20), 0);
                list.addView(divider, dividerSize);
            }
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(20), 0, dp(20), 0);
            item.setBackgroundResource(R.drawable.tv_button);
            item.setFocusable(true);
            item.setFocusableInTouchMode(true);
            item.setClickable(true);
            item.setStateListAnimator(null);
            list.addView(item, new LinearLayout.LayoutParams(-1, dp(48)));
            int separator = entries[i].lastIndexOf(": ");
            TextView label = new TextView(this);
            label.setText(separator < 0 ? entries[i] : entries[i].substring(0, separator));
            label.setTextSize(18);
            label.setDuplicateParentStateEnabled(true);
            label.setTextColor(getResources().getColorStateList(R.color.tv_button_text, getTheme()));
            item.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            if (separator >= 0) {
                TextView value = new TextView(this);
                value.setText(entries[i].substring(separator + 2));
                value.setTextSize(16);
                value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                value.setPadding(dp(16), 0, 0, 0);
                value.setDuplicateParentStateEnabled(true);
                value.setTextColor(getResources().getColorStateList(R.color.tv_button_value, getTheme()));
                item.addView(value, new LinearLayout.LayoutParams(-2, -2));
            }
            item.setContentDescription(entries[i]);
            item.setOnClickListener(view -> {
                closeMenu();
                choice.select(index);
            });
            if (firstItem == null) firstItem = item;
        }
        root.addView(menuPanel, new FrameLayout.LayoutParams(-1, -1));
        cursorView.bringToFront();
        // The D-pad moves focus inside the menu; start on the first entry.
        if (tvMode && firstItem != null) firstItem.requestFocus();
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
        String url = NavigationPolicy.addressToUrl(input, this::searchUrl);
        navigateTo(url);
        address.clearFocus();
        if (tvMode && webView != null) webView.requestFocus();
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
        } else if (findBarShown()) {
            closeFindBar();
        } else if (homePage) {
            finish();
        } else if (webView != null && webView.canGoBack()) {
            showLoading();
            webView.goBack();
        } else {
            finish();
        }
    }

    // API 33+ goes through the OnBackInvokedCallback registered in onCreate.
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        navigateBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("url", currentUrl);
        state.putBoolean("homePage", homePage);
        state.putInt("scrollY", webView == null ? 0 : webView.getScrollY());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        stopPointer();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    // A held direction would keep a callback per frame, and scroll the page, after the menu,
    // the home screen or another window took the keys that would have released it.
    private void stopPointer() {
        analogX = 0;
        analogY = 0;
        digitalX = 0;
        digitalY = 0;
        if (cursorView != null) cursorView.removeCallbacks(cursorTick);
        cursorTickScheduled = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) stopPointer();
    }

    // The manifest keeps the Activity, and the page, alive when a remote, keyboard or display
    // mode changes; only the native layout that depends on the screen is rebuilt.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        int night = configuration.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (night != nightMode && !tvMode) {
            recreate();
            return;
        }
        nightMode = night;
        if (homePage && focusedEditor() == null) showHomePage();
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
        root.removeCallbacks(releaseHeldNavigation);
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.stopLoading();
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        if (Build.VERSION.SDK_INT >= 24 && filterEngine.isNewest()) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(null);
        }
        filterEngine.close();
        super.onDestroy();
    }
}
