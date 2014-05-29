package com.lastinitial.stitch;

import android.text.format.DateUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Created by ak on 4/29/14.
 */
public class RelativeDateUtils {

    static final long DAY_IN_MILLIS = DateUtils.DAY_IN_MILLIS;
    static final long WEEK_IN_MILLIS = DateUtils.WEEK_IN_MILLIS;
    static final long MONTH_IN_MILLIS = 30L * DAY_IN_MILLIS;
    static final long YEAR_IN_MILLIS = 365L * DAY_IN_MILLIS;
    static final List<Locale> ENGLISH_LOCALES =
            Arrays.asList(Locale.ENGLISH, Locale.UK, Locale.CANADA, Locale.US);

    /**
     * Android's getRelativeTime utilities don't support relative times past a week,
     * so hard code nicer relative-time behavior for English speakers and default
     * to meh behavior for everyone else.
     * @return a relative string, to the best of our abilities.
     */
    public static CharSequence getRelativeTimeSpanString(long time, long now) {
        Locale currentLocale = Locale.getDefault();
        long diff = Math.abs(time - now);
        if (!ENGLISH_LOCALES.contains(currentLocale)) {
            long resolution = DAY_IN_MILLIS;
            if (diff > WEEK_IN_MILLIS) {
                resolution = WEEK_IN_MILLIS;
            }
            return DateUtils.getRelativeTimeSpanString(time, now, resolution);
        } else {
            long scalar = 0;
            String units = "day";

            if (diff < 6 * DAY_IN_MILLIS) {
                // Default library does the right thing in this case
                return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.DAY_IN_MILLIS);
            } else if (diff < 2L * MONTH_IN_MILLIS) {
                scalar = Math.round(Long.valueOf(diff).doubleValue() / WEEK_IN_MILLIS);
                units = "week";
            } else if (diff < YEAR_IN_MILLIS) {
                scalar = Math.round(Long.valueOf(diff).doubleValue() / MONTH_IN_MILLIS);
                units = "month";
            } else {
                scalar = Math.round(Long.valueOf(diff).doubleValue() / YEAR_IN_MILLIS);
                units = "year";
            }
            if (scalar != 1 ) {
                units += "s";
            }
            if (time < now) {
                return Long.toString(scalar) + " " + units + " ago";
            } else {
                return "in " + Long.toString(scalar) + " " + units;
            }
        }
    }
}
