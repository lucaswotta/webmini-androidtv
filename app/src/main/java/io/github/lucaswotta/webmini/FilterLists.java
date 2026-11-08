package io.github.lucaswotta.webmini;

final class FilterLists {
    static final long MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final String HOST = "https://easylist-downloads.adblockplus.org/";

    private FilterLists() {}

    // Returns null for languages without a regional list.
    static String regionalUrl(String language) {
        if ("pt".equals(language)) return HOST + "easylistportuguese.txt";
        if ("es".equals(language)) return HOST + "easylistspanish.txt";
        return null;
    }

    static boolean isStale(long updatedAtMillis, long nowMillis) {
        return nowMillis - updatedAtMillis >= MAX_AGE_MILLIS;
    }

    // A list file starts with a header and is never tiny; this rejects error pages.
    static boolean isValid(String contents) {
        String trimmed = contents.trim();
        return trimmed.length() >= 256 && trimmed.startsWith("[Adblock Plus ");
    }
}
