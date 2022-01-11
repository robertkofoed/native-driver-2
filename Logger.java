package com.iboxendriverapp;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryAndroid;
import android.content.Context;


/**
 * Created by Qlocx on 2019-06-05.
 */

public class Logger implements QlocxLogger {
    LogStream ls;

    interface LogStream {
        void newLog(String category, String message);
    }

    Logger(Context ctx) {
        SentryAndroid.init(ctx, options -> {
          options.setDsn("https://7d0f654b6dbc4c949c7dbd8e4a649e80@o456254.ingest.sentry.io/5582673");
        });
    }

    private Breadcrumb addBreadcrumb(String category, String message) {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setCategory(category);
        breadcrumb.setMessage(message);
        breadcrumb.setLevel(SentryLevel.INFO);
        Sentry.addBreadcrumb(breadcrumb);

        return breadcrumb;
    }

    public void subscribeToLogStream(LogStream ls) {
        this.ls = ls;
    }

    @Override
    public void log(String category, String message) {
        addBreadcrumb(category, message);

        Log.v("QlocxInterface", category + ": " + message);

        if(this.ls != null) {
            ls.newLog(category, message);
        }
    }

    @Override
    public JSONArray getLogAsJSON() {
        return null;
    }
}
