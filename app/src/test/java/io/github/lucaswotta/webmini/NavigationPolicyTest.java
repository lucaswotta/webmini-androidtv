package io.github.lucaswotta.webmini;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;

public class NavigationPolicyTest {
    @Test
    public void allowsOnlyValidHttpsDestinations() {
        assertEquals(NavigationPolicy.Decision.ALLOW,
                NavigationPolicy.decide("https://example.com/login?next=http://other.test"));
        assertEquals(NavigationPolicy.Decision.ALLOW,
                NavigationPolicy.decide("HTTPS://EXAMPLE.COM:443/"));
        assertEquals(NavigationPolicy.Decision.ALLOW,
                NavigationPolicy.decide("https://[::1]:8443/"));
    }

    @Test
    public void requiresHttpConfirmationEveryTime() {
        String url = "http://example.com/form";
        assertEquals(NavigationPolicy.Decision.CONFIRM_HTTP, NavigationPolicy.decide(url));
        assertEquals(NavigationPolicy.Decision.CONFIRM_HTTP, NavigationPolicy.decide(url));
        assertEquals(NavigationPolicy.Decision.CONFIRM_HTTP,
                NavigationPolicy.decide("HTTP://EXAMPLE.COM/redirect"));
    }

    @Test
    public void blocksExternalSchemesAndIntentFallbacks() {
        String[] urls = {"intent://example.com/#Intent;scheme=https;end", "file:///sdcard/a",
                "content://provider/a", "javascript:alert(1)", "data:text/html,test",
                "about:blank", "tel:123", "mailto:a@example.com", "//example.com", "example.com"};
        for (String url : urls) {
            assertEquals(url, NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide(url));
        }
    }

    @Test
    public void blocksMalformedOrAmbiguousAddresses() {
        String[] urls = {null, "", "https:///path", "https://", "https://example.com:65536/",
                "https://user:password@example.com/", "https://example.com\\@evil.test/",
                "https://example.com/\n", "https://example.com:bad/"};
        for (String url : urls) {
            assertEquals(url, NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide(url));
        }
    }

    @Test
    public void neverOffersToReplayHttpPostOrSubframeNavigation() {
        assertEquals(NavigationPolicy.Decision.BLOCK,
                NavigationPolicy.decide("http://example.com/form", true, "POST"));
        assertEquals(NavigationPolicy.Decision.BLOCK,
                NavigationPolicy.decide("http://example.com/frame", false, "GET"));
        assertEquals(NavigationPolicy.Decision.CONFIRM_HTTP,
                NavigationPolicy.decide("http://example.com/redirect", true, "GET"));
        assertEquals(NavigationPolicy.Decision.ALLOW,
                NavigationPolicy.decide("https://example.com/login", true, "POST"));
    }

    @Test
    public void exceptionsMatchOnlyTheExactOrigin() {
        assertEquals("https://example.com", NavigationPolicy.origin("HTTPS://EXAMPLE.COM:443/a"));
        assertEquals("http://example.com", NavigationPolicy.origin("http://example.com:80/a"));
        assertEquals("https://sub.example.com", NavigationPolicy.origin("https://sub.example.com/a"));
        assertEquals("https://example.com:8443", NavigationPolicy.origin("https://example.com:8443/a"));
        String origin = NavigationPolicy.origin("https://example.com/");
        assertNotEquals(origin, NavigationPolicy.origin("http://example.com/"));
        assertNotEquals(origin, NavigationPolicy.origin("https://sub.example.com/"));
        assertNotEquals(origin, NavigationPolicy.origin("https://example.com:8443/"));
        assertNull(NavigationPolicy.origin("file:///example.com"));
    }

    @Test
    public void refusesFormsSentOverHttp() {
        assertTrue(NavigationPolicy.blocksHttpForm("http://example.com/login", "POST"));
        assertFalse(NavigationPolicy.blocksHttpForm("http://example.com/", "GET"));
        assertFalse(NavigationPolicy.blocksHttpForm("https://example.com/login", "POST"));
        assertFalse(NavigationPolicy.blocksHttpForm("intent://x", "POST"));
    }

    @Test
    public void parsesWhatIsTypedInTheAddressBar() {
        NavigationPolicy.Search search = query -> "search:" + query;
        assertEquals("https://example.com", NavigationPolicy.addressToUrl("example.com", search));
        assertEquals("http://example.com", NavigationPolicy.addressToUrl("http://example.com", search));
        assertEquals("search:two words", NavigationPolicy.addressToUrl("two words", search));
        assertEquals("search:android", NavigationPolicy.addressToUrl("android", search));
        assertEquals("http://localhost:8080", NavigationPolicy.addressToUrl("localhost:8080", search));
        assertEquals("http://192.168.0.10:8765/x", NavigationPolicy.addressToUrl("192.168.0.10:8765/x", search));
        assertEquals("https://example.com/path?q=1", NavigationPolicy.addressToUrl("example.com/path?q=1", search));
    }
}
