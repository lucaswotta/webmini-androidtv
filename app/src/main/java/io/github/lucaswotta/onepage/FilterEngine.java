package io.github.lucaswotta.onepage;

import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// Owns the filter lists on disk and the native engine built from them. The list text never
// enters the Java heap: files are downloaded to disk and read by the native side.
final class FilterEngine {
    static {
        System.loadLibrary("onepage_adblock");
    }

    static final String[] REGIONAL_CHOICES = {"auto", "pt", "es", "none"};

    private static final String EASYLIST_URL = "https://easylist.to/easylist/easylist.txt";
    private static final String EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt";
    private static final String[] LIST_FILES =
            {"easylist.txt", "easyprivacy.txt", "easylist-regional.txt"};
    private static final int REGIONAL_LIST = 2;
    private static final String ENGINE_FILE = "engine.dat";
    private static final int MAX_LIST_BYTES = 5 * 1024 * 1024;
    private static final long DOWNLOAD_DEADLINE_MILLIS = 60_000;

    // The native engine is process-wide; only the newest instance may replace or clear it.
    private static final AtomicInteger OWNER = new AtomicInteger();
    private static final Object ENGINE_LOCK = new Object();
    private static String installedKey;

    private final File directory;
    private final SharedPreferences preferences;
    private final long appVersion;
    private final Runnable onSettled;
    private final int owner = OWNER.incrementAndGet();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean enabled;
    private volatile boolean ready;
    // False while the engine was built without EasyList or EasyPrivacy.
    private volatile boolean complete;
    private volatile boolean closed;
    private volatile HttpURLConnection activeDownload;
    private boolean running;
    private boolean reloadRequested;

    private static native boolean nativeBuild(String[] listPaths, String enginePath);
    private static native boolean nativeLoad(String enginePath);
    private static native void nativeClear();
    private static native boolean nativeShouldBlock(String url, String pageUrl,
            String resourceType, String method);

    // onSettled runs on a worker thread each time a load attempt ends, with or without an engine.
    FilterEngine(File directory, SharedPreferences preferences, long appVersion,
                 Runnable onSettled) {
        this.directory = directory;
        this.preferences = preferences;
        this.appVersion = appVersion;
        this.onSettled = onSettled;
    }

    boolean isNewest() {
        return owner == OWNER.get();
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean isReady() {
        return ready;
    }

    boolean isComplete() {
        return complete;
    }

    synchronized boolean isLoading() {
        return running;
    }

    boolean shouldBlock(String url, String pageUrl, String resourceType, String method) {
        return ready && nativeShouldBlock(url, pageUrl, resourceType, method);
    }

    void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            load();
        } else {
            ready = false;
            executor.execute(this::clearIfOwner);
        }
    }

    // Loads the cached engine, then refreshes stale lists. A call during a load queues one more.
    void load() {
        synchronized (this) {
            if (!enabled || closed) return;
            if (running) {
                reloadRequested = true;
                return;
            }
            running = true;
        }
        executor.execute(() -> {
            try {
                deleteAbandonedDownloads();
                install();
                onSettled.run();
                if (refreshLists()) install();
            } finally {
                boolean retry;
                synchronized (this) {
                    running = false;
                    retry = reloadRequested && enabled && !closed;
                    reloadRequested = false;
                }
                onSettled.run();
                if (retry) load();
            }
        });
    }

    // A download cut by the process being killed leaves its temporary file behind. Recent
    // ones may belong to another instance that is still downloading.
    private void deleteAbandonedDownloads() {
        File[] files = directory.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 10 * 60 * 1000;
        for (File file : files) {
            if (file.getName().endsWith(".tmp") && file.lastModified() < cutoff
                    && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    void close() {
        closed = true;
        enabled = false;
        ready = false;
        HttpURLConnection download = activeDownload;
        // Closing a TLS socket writes to the network, which is not allowed on the main thread.
        if (download != null) new Thread(download::disconnect).start();
        executor.execute(this::clearIfOwner);
        executor.shutdown();
    }

    String regionalChoice() {
        String saved = preferences.getString("regionalList", "auto");
        for (String choice : REGIONAL_CHOICES) if (choice.equals(saved)) return saved;
        return "auto";
    }

    private String regionalLanguage() {
        String choice = regionalChoice();
        if (choice.equals("auto")) return Locale.getDefault().getLanguage();
        return choice.equals("none") ? "" : choice;
    }

    private boolean active() {
        return enabled && !closed && owner == OWNER.get();
    }

    private File listFile(int index) {
        return new File(directory, LIST_FILES[index]);
    }

    private boolean listUsable(int index) {
        if (index == REGIONAL_LIST && (FilterLists.regionalUrl(regionalLanguage()) == null
                || !regionalLanguage().equals(
                        preferences.getString("regionalListLanguage", "")))) return false;
        return validList(listFile(index));
    }

    private static boolean validList(File file) {
        long length = file.length();
        if (!file.isFile() || length > MAX_LIST_BYTES) return false;
        byte[] head = new byte[64];
        try (InputStream input = new FileInputStream(file)) {
            int count = Math.max(0, input.read(head));
            return FilterLists.isValid(head, count, length);
        } catch (IOException error) {
            return false;
        }
    }

    // The running engine stays active until the new one is ready, so a failed update never
    // leaves the page without the lists it already had.
    private void install() {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> paths = new ArrayList<>();
        for (int i = 0; i < LIST_FILES.length; i++) {
            if (!listUsable(i)) continue;
            names.add(LIST_FILES[i]);
            paths.add(listFile(i).getAbsolutePath());
        }
        if (paths.isEmpty()) return;
        long[] lengths = new long[paths.size()];
        long[] modified = new long[paths.size()];
        for (int i = 0; i < lengths.length; i++) {
            File file = new File(paths.get(i));
            lengths[i] = file.length();
            modified[i] = file.lastModified();
        }
        String key = FilterLists.engineKey(appVersion, names.toArray(new String[0]),
                lengths, modified);
        String enginePath = new File(directory, ENGINE_FILE).getAbsolutePath();
        synchronized (ENGINE_LOCK) {
            if (!active()) return;
            boolean installed = key.equals(installedKey);
            if (!installed && key.equals(preferences.getString("engineKey", null))) {
                installed = nativeLoad(enginePath);
            }
            if (!installed) {
                installed = nativeBuild(paths.toArray(new String[0]), enginePath);
                if (installed) preferences.edit().putString("engineKey", key).apply();
            }
            if (!installed || !active()) return;
            installedKey = key;
            complete = names.contains(LIST_FILES[0]) && names.contains(LIST_FILES[1]);
            ready = true;
        }
    }

    private void clearIfOwner() {
        synchronized (ENGINE_LOCK) {
            if (owner != OWNER.get()) return;
            nativeClear();
            installedKey = null;
        }
    }

    private boolean listStale(int index, String url) {
        if (url == null) return false;
        if (!listUsable(index)) return true;
        return FilterLists.isStale(preferences.getLong("filterListUpdatedAt" + index, 0),
                System.currentTimeMillis());
    }

    // Returns true when a list file changed and the engine has to be rebuilt.
    private boolean refreshLists() {
        String language = regionalLanguage();
        String[] urls = {EASYLIST_URL, EASYPRIVACY_URL, FilterLists.regionalUrl(language)};
        boolean changed = false;
        for (int i = 0; i < urls.length; i++) {
            if (!active()) return false;
            if (!listStale(i, urls[i])) continue;
            changed |= downloadList(i, urls[i], language);
        }
        return changed && active();
    }

    private boolean downloadList(int index, String url, String language) {
        File destination = listFile(index);
        // Two instances can download during an Activity recreation; each keeps its own file.
        File temporary = new File(directory, LIST_FILES[index] + "." + owner + ".tmp");
        boolean conditional = listUsable(index)
                && url.equals(preferences.getString("filterListUrl" + index, null));
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            activeDownload = connection;
            if (closed) return false;
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "OnePage filter-list updater");
            if (conditional) {
                String etag = preferences.getString("filterListEtag" + index, null);
                String modified = preferences.getString("filterListModified" + index, null);
                if (etag != null) connection.setRequestProperty("If-None-Match", etag);
                if (modified != null) connection.setRequestProperty("If-Modified-Since", modified);
            }
            int status = connection.getResponseCode();
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) return false;
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED && conditional) {
                preferences.edit().putLong("filterListUpdatedAt" + index,
                        System.currentTimeMillis()).apply();
                return false;
            }
            if (status != HttpURLConnection.HTTP_OK) return false;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary)) {
                copyLimited(input, output);
            }
            // The user may have switched region while this list downloaded.
            if (!validList(temporary) || !active()
                    || (index == REGIONAL_LIST && !language.equals(regionalLanguage()))) {
                return false;
            }
            boolean changed = !listUsable(index) || !sameContents(temporary, destination);
            if (changed && !temporary.renameTo(destination)) return false;
            SharedPreferences.Editor editor = preferences.edit()
                    .putLong("filterListUpdatedAt" + index, System.currentTimeMillis())
                    .putString("filterListUrl" + index, url)
                    .putString("filterListEtag" + index, connection.getHeaderField("ETag"))
                    .putString("filterListModified" + index,
                            connection.getHeaderField("Last-Modified"));
            if (index == REGIONAL_LIST) editor.putString("regionalListLanguage", language);
            editor.apply();
            return changed;
        } catch (IOException | RuntimeException error) {
            // No network, a refused connection or a disconnect from close(): the cached list
            // stays and the next start tries again.
            return false;
        } finally {
            activeDownload = null;
            if (connection != null) connection.disconnect();
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    private void copyLimited(InputStream input, FileOutputStream output) throws IOException {
        long deadline = System.nanoTime() + DOWNLOAD_DEADLINE_MILLIS * 1_000_000;
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_LIST_BYTES) throw new IOException("Filter list is too large");
            if (closed || System.nanoTime() > deadline) {
                throw new IOException("Filter list download stopped");
            }
            output.write(buffer, 0, count);
        }
    }

    private static boolean sameContents(File first, File second) throws IOException {
        if (first.length() != second.length()) return false;
        try (InputStream a = new java.io.BufferedInputStream(new FileInputStream(first));
             InputStream b = new java.io.BufferedInputStream(new FileInputStream(second))) {
            int value;
            while ((value = a.read()) != -1) {
                if (value != b.read()) return false;
            }
            return true;
        }
    }
}
