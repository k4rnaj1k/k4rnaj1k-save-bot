package com.k4rnaj1k.savebot.utils;

import java.net.MalformedURLException;
import java.net.URL;

public class UrlUtils {

    public static boolean isUrl(String urlString) {
        try {
            new URL(urlString);  // If this doesn't throw, it's at least a syntactically valid URL
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
