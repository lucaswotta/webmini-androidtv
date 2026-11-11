package io.github.lucaswotta.onepage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class NavigationPolicy {
    enum Decision { ALLOW, CONFIRM_HTTP, BLOCK }

    interface Search {
        String url(String query);
    }

    // Turns what was typed in the address bar into a URL: a full URL is kept, text with spaces
    // or without a dot is searched, and host names get HTTPS (HTTP for local addresses, which
    // have no certificate; the HTTP warning still applies).
    static String addressToUrl(String input, Search search) {
        String text = input.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")) return text;
        if (text.contains(" ")) return search.url(text);
        String host = text.split("[/?#]", 2)[0].replaceFirst(":\\d{1,5}$", "");
        boolean local = host.equals("localhost") || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        if (local) return "http://" + text;
        if (!host.contains(".")) return search.url(text);
        return "https://" + text;
    }

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

    // Referer for a download, as strict-origin-when-cross-origin would send it: the page URL
    // without fragment on the same origin, the origin alone across sites, nothing on a
    // downgrade to HTTP or when either side is not a web address.
    static String downloadReferer(String pageUrl, String downloadUrl) {
        String page = origin(pageUrl);
        String download = origin(downloadUrl);
        if (page == null || download == null) return null;
        if (page.startsWith("https:") && download.startsWith("http:")) return null;
        if (!page.equals(download)) return page + "/";
        int fragment = pageUrl.indexOf('#');
        return fragment < 0 ? pageUrl : pageUrl.substring(0, fragment);
    }

    // Without a public suffix list, two hosts count as the same site when one contains the
    // other or they end in the same two labels. That over-merges hosts under suffixes like
    // co.uk, which only means fewer navigations are checked, never that a login is cut.
    static boolean isCrossSite(String fromUrl, String toUrl) {
        String from = host(fromUrl);
        String to = host(toUrl);
        if (from == null || to == null || from.equals(to)) return false;
        if (from.endsWith("." + to) || to.endsWith("." + from)) return false;
        if (from.contains(":") || to.contains(":") || from.matches("[\\d.]+")
                || to.matches("[\\d.]+")) return true;
        return !lastLabels(from, 2).equals(lastLabels(to, 2));
    }

    private static String host(String url) {
        String origin = origin(url);
        if (origin == null) return null;
        String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static String lastLabels(String host, int count) {
        int start = host.length();
        for (int i = 0; i < count && start > 0; i++) start = host.lastIndexOf('.', start - 1);
        return host.substring(start + 1);
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
