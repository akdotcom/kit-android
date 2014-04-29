package com.lastinitial.kit;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Created by ak on 4/29/14.
 */
public class FontUtils {
    static Typeface mFontAwesome = null;

    static synchronized Typeface getFontAwesome(Context context) {
        if (mFontAwesome == null) {
            mFontAwesome = Typeface.createFromAsset(
                    context.getAssets(),
                    "fonts/fontawesome-webfont.ttf");
        }
        return mFontAwesome;
    }
}
