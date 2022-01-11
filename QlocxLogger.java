package com.iboxendriverapp;

import org.json.JSONArray;

/**
 * Created by Qlocx on 2019-05-28.
 */

public interface QlocxLogger {
    void log(String category, String message);

    JSONArray getLogAsJSON();
}
