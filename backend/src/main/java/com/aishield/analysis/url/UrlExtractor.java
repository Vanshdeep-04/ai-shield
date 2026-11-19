package com.aishield.analysis.url;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** Finds web URLs written as plain text (http://, https:// or www.). */
@Component
public class UrlExtractor {

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[^\\s<>\"'`{}|\\\\^\\[\\]]+)");
    private static final int MAX_URLS = 200;

    public List<String> extract(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher m = URL.matcher(text);
        while (m.find() && found.size() < MAX_URLS) {
            String url = trimTrailingPunctuation(m.group(1));
            if (url.length() > "www.".length() + 2) {
                found.add(url);
            }
        }
        return new ArrayList<>(found);
    }

    /** Removes sentence punctuation and unbalanced closing brackets that are not part of the URL. */
    static String trimTrailingPunctuation(String url) {
        String s = url;
        while (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            if (".,;:!?'\"".indexOf(last) >= 0) {
                s = s.substring(0, s.length() - 1);
            } else if (last == ')' && count(s, '(') < count(s, ')')) {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        return s;
    }

    private static long count(String s, char c) {
        return s.chars().filter(ch -> ch == c).count();
    }
}
