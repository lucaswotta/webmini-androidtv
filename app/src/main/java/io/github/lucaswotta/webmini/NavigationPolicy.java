package io.github.lucaswotta.webmini;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class NavigationPolicy {
    enum Decision { ALLOW, CONFIRM_HTTP, BLOCK }

    static Decision decide(String url) {
        String origin = origin(url);
        if (origin == null) return Decision.BLOCK;
        return origin.startsWith("http:") ? Decision.CONFIRM_HTTP : Decision.ALLOW;
    }

    // WebView reports POST navigations to no callback the user could answer,
    // so a form sent over HTTP is refused instead of confirmed or converted to GET.
    static boolean blocksHttpForm(String url, String method) {
        String origin = origin(url);
        return origin != null && origin.startsWith("http:") && !"GET".equalsIgnoreCase(method);
    }

    static Decision decide(String url, boolean mainFrame, String method) {
        Decision decision = decide(url);
        if (decision == Decision.CONFIRM_HTTP
                && (!mainFrame || !"GET".equalsIgnoreCase(method))) return Decision.BLOCK;
        return decision;
    }

    static String origin(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null || uri.getRawUserInfo() != null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            int port = uri.getPort();
            if (port < -1 || port > 65535) return null;
            if (port == (scheme.equals("https") ? 443 : 80)) port = -1;
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + (port == -1 ? "" : ":" + port);
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
