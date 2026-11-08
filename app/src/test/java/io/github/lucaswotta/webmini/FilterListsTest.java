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

    @Test
    public void rejectsErrorPagesAndShortFiles() {
        StringBuilder valid = new StringBuilder("[Adblock Plus 2.0]\n");
        while (valid.length() < 300) valid.append("||ads.example.com^\n");
        assertTrue(FilterLists.isValid(valid.toString()));
        assertFalse(FilterLists.isValid("<!DOCTYPE html>" + valid));
        assertFalse(FilterLists.isValid("[Adblock Plus 2.0]\n||a.com^"));
    }
}
