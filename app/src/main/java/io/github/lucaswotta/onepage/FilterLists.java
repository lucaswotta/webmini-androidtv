package io.github.lucaswotta.onepage;

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

    // A list file starts with a header and is never tiny; this rejects error pages. Only the
    // first bytes are read, so a list is never held whole in the Java heap.
    static boolean isValid(byte[] head, int headLength, long totalLength) {
        if (totalLength < 256) return false;
        String start = new String(head, 0, headLength, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (start.startsWith("\u00EF\u00BB\u00BF")) start = start.substring(3);
        return start.trim().startsWith("[Adblock Plus ");
    }

    // The compiled engine is reused only for the same app build and the same list files.
    static String engineKey(long appVersion, String[] names, long[] lengths, long[] modified) {
        StringBuilder key = new StringBuilder("network-only|").append(appVersion);
        for (int i = 0; i < names.length; i++) {
            key.append('|').append(names[i]).append(':').append(lengths[i])
                    .append(':').append(modified[i]);
        }
        return key.toString();
    }
}
