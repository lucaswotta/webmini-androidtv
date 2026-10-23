package br.com.lucaswotta.navegadorleve;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME = "about:blank";
    private static final String YOUTUBE = "https://m.youtube.com/";
    private static final int MAX_TABS = 8;
    private static final byte[] EMPTY_BODY = new byte[0];
    private static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "amazon-adsystem.com", "taboola.com", "outbrain.com"
    };

    private static class Tab {
        String url;
        String title;
        Bundle history;

        Tab(String url) {
            this.url = url;
            title = url.equals(HOME) ? "Nova aba" : url;
        }
    }

    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int activeTab;
    private WebView webView;
    private EditText address;
    private Button tabButton;
    private FrameLayout root;
    private LinearLayout page;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private volatile boolean adBlockEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adBlockEnabled = getPreferences(MODE_PRIVATE).getBoolean("adBlockEnabled", false);
        root = new FrameLayout(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
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
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        Button back = button("‹", toolbar);
        back.setContentDescription("Voltar");
        back.setOnClickListener(view -> navigateBack());

        address = new EditText(this);
        address.setHint("Pesquisar ou digitar URL");
        address.setSingleLine(true);
        address.setSelectAllOnFocus(true);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                openAddress();
                return true;
            }
            return false;
        });
        toolbar.addView(address, new LinearLayout.LayoutParams(0, -2, 1));
        Button go = button("Ir", toolbar);
        go.setOnClickListener(view -> openAddress());
        tabButton = button("1", toolbar);
        tabButton.setContentDescription("Abas");
        tabButton.setOnClickListener(view -> showTabs());
        Button menu = button("⋮", toolbar);
        menu.setContentDescription("Mais opções");
        menu.setOnClickListener(view -> showMenu());

        if (savedInstanceState != null) {
            ArrayList<String> urls = savedInstanceState.getStringArrayList("urls");
            if (urls != null) {
                for (String url : urls) tabs.add(new Tab(url));
            }
            activeTab = savedInstanceState.getInt("activeTab", 0);
        }
        if (tabs.isEmpty()) {
            String launchUrl = incomingUrl(getIntent());
            tabs.add(new Tab(launchUrl == null ? HOME : launchUrl));
        }
        activeTab = Math.min(activeTab, tabs.size() - 1);
        setContentView(root);
        showActiveTab();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::navigateBack);
        }
    }

    private Button button(String label, LinearLayout toolbar) {
        Button button = new Button(this);
        button.setText(label);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        toolbar.addView(button, new LinearLayout.LayoutParams(-2, -2));
        return button;
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
            if (tabs.size() == MAX_TABS) webView.loadUrl(url);
            else openNewTab(url);
        }
    }

    private void showActiveTab() {
        Tab tab = tabs.get(activeTab);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
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
            public void onPageFinished(WebView view, String url) {
                if (view == webView && !url.equals("about:blank")) {
                    tab.url = url;
                    address.setText(url);
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
                showActiveTabEmpty();
                Toast.makeText(MainActivity.this,
                        "A página parou. Toque em Ir para recarregar.", Toast.LENGTH_LONG).show();
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                if (isUserGesture && !isDialog) {
                    String url = view.getHitTestResult().getExtra();
                    if (url != null && (url.startsWith("https://")
                            || url.startsWith("http://"))) {
                        view.post(() -> openNewTab(url));
                    }
                }
                return false;
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (view == webView && title != null) tab.title = title;
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
                page.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }
        });
        webView.setOnLongClickListener(view -> {
            String url = webView.getHitTestResult().getExtra();
            if (url == null || !url.startsWith("http")) return false;
            new AlertDialog.Builder(this)
                    .setItems(new String[]{"Abrir link em nova aba"},
                            (dialog, which) -> openNewTab(url))
                    .show();
            return true;
        });
        page.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        address.setText(tab.url.equals(HOME) ? "" : tab.url);
        tabButton.setText(String.format(Locale.getDefault(), "%d", tabs.size()));
        if (tab.history == null || webView.restoreState(tab.history) == null) {
            webView.loadUrl(tab.url);
        } else {
            webView.reload();
        }
        tab.history = null;
    }

    private void showActiveTabEmpty() {
        Tab tab = tabs.get(activeTab);
        String url = tab.url;
        tab.url = "about:blank";
        showActiveTab();
        tab.url = url;
        address.setText(url);
    }

    private void freezeActiveTab() {
        if (webView == null) return;
        Tab tab = tabs.get(activeTab);
        String url = webView.getUrl();
        if (url != null && !url.equals("about:blank")) tab.url = url;
        tab.history = new Bundle();
        webView.saveState(tab.history);
        destroyWebView();
    }

    private void destroyWebView() {
        if (webView == null) return;
        hideFullscreen();
        webView.stopLoading();
        if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }
        webView.destroy();
        webView = null;
    }

    private void switchTab(int index) {
        if (index == activeTab) return;
        freezeActiveTab();
        activeTab = index;
        showActiveTab();
    }

    private void openNewTab(String url) {
        if (tabs.size() >= MAX_TABS) {
            Toast.makeText(this, "Limite de 8 abas", Toast.LENGTH_SHORT).show();
            return;
        }
        freezeActiveTab();
        tabs.add(new Tab(url));
        activeTab = tabs.size() - 1;
        showActiveTab();
    }

    private void closeActiveTab() {
        destroyWebView();
        tabs.remove(activeTab);
        if (tabs.isEmpty()) tabs.add(new Tab(HOME));
        activeTab = Math.min(activeTab, tabs.size() - 1);
        showActiveTab();
    }

    private void showTabs() {
        String[] entries = new String[tabs.size() + 2];
        entries[0] = "+ Nova aba";
        for (int i = 0; i < tabs.size(); i++) {
            entries[i + 1] = (i == activeTab ? "• " : "") + tabs.get(i).title;
        }
        entries[entries.length - 1] = "Fechar aba atual";
        new AlertDialog.Builder(this).setTitle("Abas")
                .setItems(entries, (dialog, which) -> {
                    if (which == 0) openNewTab(HOME);
                    else if (which == entries.length - 1) closeActiveTab();
                    else switchTab(which - 1);
                }).show();
    }

    private void showMenu() {
        String adBlockLabel = adBlockEnabled
                ? "Bloqueio de anúncios: ligado" : "Bloqueio de anúncios: desligado";
        new AlertDialog.Builder(this).setItems(
                new String[]{"Avançar", "Recarregar", "Página inicial", "YouTube",
                        "Nova aba", adBlockLabel},
                (dialog, which) -> {
                    if (which == 0 && webView.canGoForward()) webView.goForward();
                    if (which == 1) webView.reload();
                    if (which == 2) webView.loadUrl(HOME);
                    if (which == 3) webView.loadUrl(YOUTUBE);
                    if (which == 4) openNewTab(HOME);
                    if (which == 5) {
                        adBlockEnabled = !adBlockEnabled;
                        getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("adBlockEnabled", adBlockEnabled).apply();
                        webView.reload();
                    }
                }).show();
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
                url = "https://lite.duckduckgo.com/lite/?q="
                        + URLEncoder.encode(input, "UTF-8");
            } catch (java.io.UnsupportedEncodingException error) {
                throw new AssertionError(error);
            }
        } else {
            url = "https://" + input;
        }
        webView.loadUrl(url);
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
        if (fullscreenView != null) hideFullscreen();
        else if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    public void onBackPressed() {
        navigateBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        ArrayList<String> urls = new ArrayList<>();
        for (Tab tab : tabs) urls.add(tab.url);
        state.putStringArrayList("urls", urls);
        state.putInt("activeTab", activeTab);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        super.onDestroy();
    }
}
