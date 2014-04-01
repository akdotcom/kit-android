package me.happylabs.kit;

import android.util.Log;

/**
 * Created by ak on 3/31/14.
 */
public class TextUtils {

    public static String describeFrequency(int type, int scalar) {
        String description = "";
        if (scalar == 1) {
            switch(type) {
                case(MainActivity.FREQUENCY_HOURLY):
                    description = "an hour";
                    break;
                case(MainActivity.FREQUENCY_DAILY):
                    description = "a day";
                    break;
                case(MainActivity.FREQUENCY_WEEKLY):
                    description = "a week";
                    break;
                case(MainActivity.FREQUENCY_MONTHLY):
                    description = "a month";
                    break;
                default:
                    throw new IllegalArgumentException("Unknown frequency type: " + type);
            }
        } else {
            switch(type) {
                case (MainActivity.FREQUENCY_HOURLY):
                    description = scalar + " hours";
                    break;
                case (MainActivity.FREQUENCY_DAILY):
                    description = scalar + "days";
                    break;
                case (MainActivity.FREQUENCY_WEEKLY):
                    description = scalar + "weeks";
                    break;
                case (MainActivity.FREQUENCY_MONTHLY):
                    description = scalar + "months";
                    break;
                default:
                    throw new IllegalArgumentException("Unknown frequency type: " + type);
            }
        }
        return description;
    }
}
