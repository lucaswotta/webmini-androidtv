package io.github.lucaswotta.webmini;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilterListsTest {
    @Test
    public void regionalListsExistOnlyForPortugueseAndSpanish() {
        assertEquals("https://easylist-downloads.adblockplus.org/easylistportuguese.txt",
                FilterLists.regionalUrl("pt"));
        assertEquals("https://easylist-downloads.adblockplus.org/easylistspanish.txt",
                FilterLists.regionalUrl("es"));
        assertNull(FilterLists.regionalUrl("en"));
        assertNull(FilterLists.regionalUrl(""));
    }

    @Test
    public void listsBecomeStaleAfterSevenDays() {
        long day = 24L * 60 * 60 * 1000;
        assertFalse(FilterLists.isStale(0, 7 * day - 1));
        assertTrue(FilterLists.isStale(0, 7 * day));
        assertTrue(FilterLists.isStale(0, 30 * day));
    }

    private static boolean valid(String contents) {
        byte[] bytes = contents.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return FilterLists.isValid(bytes, Math.min(bytes.length, 64), bytes.length);
    }

    @Test
    public void rejectsErrorPagesAndShortFiles() {
        StringBuilder list = new StringBuilder("[Adblock Plus 2.0]\n");
        while (list.length() < 300) list.append("||ads.example.com^\n");
        assertTrue(valid(list.toString()));
        assertTrue(valid("\uFEFF\n" + list));
        assertFalse(valid("<!DOCTYPE html>" + list));
        assertFalse(valid("[Adblock Plus 2.0]\n||a.com^"));
    }

    @Test
    public void engineKeyChangesWithAppVersionAndListFiles() {
        String[] names = {"easylist.txt", "easyprivacy.txt"};
        String key = FilterLists.engineKey(5, names, new long[]{10, 20}, new long[]{1, 2});
        assertEquals(key, FilterLists.engineKey(5, names, new long[]{10, 20}, new long[]{1, 2}));
        assertFalse(key.equals(FilterLists.engineKey(6, names, new long[]{10, 20}, new long[]{1, 2})));
        assertFalse(key.equals(FilterLists.engineKey(5, names, new long[]{10, 21}, new long[]{1, 2})));
        assertFalse(key.equals(FilterLists.engineKey(5, names, new long[]{10, 20}, new long[]{1, 3})));
        assertFalse(key.equals(FilterLists.engineKey(5, new String[]{"easylist.txt"},
                new long[]{10}, new long[]{1})));
    }
}
