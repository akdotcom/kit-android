package com.lastinitial.stitch;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

/**
 * Created by ak on 4/30/14.
 */
public class AnalyticsUtil {

    public static void logScreenImpression(Activity activity, String screenName) {
        if (isRelease(activity)) {
            Tracker t = ((StitchApplication) activity.getApplication()).getTracker();
            t.setScreenName(screenName);
            // Send a screen view.
            t.send(new HitBuilders.AppViewBuilder().build());
        } else {
            Log.v("AnalyticsUtil", "Skipping analytics call for screenName: " + screenName);
        }
    }

    public static void logAction(Activity activity, String category, String actionName) {
        if (isRelease(activity)) {
            Tracker t = ((StitchApplication) activity.getApplication()).getTracker();
            t.send(new HitBuilders.EventBuilder()
                    .setCategory(category)
                    .setAction(actionName)
                    .build());
        } else {
            Log.v("AnalyticsUtil",
                  "Skipping analytics call for category: " + category + ", action: " + actionName);
        }
    }

    private static boolean isRelease(Activity activity) {
        return (0 == (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
    }
}
