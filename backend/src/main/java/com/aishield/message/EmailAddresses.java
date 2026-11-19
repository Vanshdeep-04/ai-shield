package com.aishield.message;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small helpers for email address strings. */
public final class EmailAddresses {

    /** Deliberately simple: we only need to recognise and split addresses, not fully validate RFC 5322. */
    private static final Pattern ADDRESS = Pattern.compile(
            "([A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64})@([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+)");

    private EmailAddresses() {
    }

    /** Extracts the first address from a header such as {@code "PayPal" <service@paypal.com>}. */
    public static String extractAddress(String header) {
        if (header == null) {
            return null;
        }
        Matcher m = ADDRESS.matcher(header);
        return m.find() ? (m.group(1) + "@" + m.group(2)).toLowerCase(Locale.ROOT) : null;
    }

    /** Returns the lower-cased domain of an address, or {@code null}. */
    public static String domainOf(String address) {
        if (address == null) {
            return null;
        }
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return null;
        }
        return address.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    /** True when the text contains something that looks like an email address. */
    public static boolean containsAddress(String text) {
        return text != null && ADDRESS.matcher(text).find();
    }
}
