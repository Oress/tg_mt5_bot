package org.ipan.hedging.telegram.utils;

public class SearchingUtils {

    public static String paramterContains(String val) {
        if (val == null) return null;

        return '%' + val + '%';
    }
}
