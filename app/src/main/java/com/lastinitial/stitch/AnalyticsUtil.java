package com.lastinitial.stitch;

import android.app.Activity;
import android.content.pm.ApplicationInfo;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

/**
 * Created by ak on 4/30/14.
 */
public class AnalyticsUtil {

    public static void logScreenImpression(Activity activity, String screenName) {
        boolean isRelease =
                (0 == (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
        if (isRelease) {
            Tracker t = ((StitchApplication) activity.getApplication()).getTracker();
            t.setScreenName(screenName);
            // Send a screen view.
            t.send(new HitBuilders.AppViewBuilder().build());
        }
    }
}
