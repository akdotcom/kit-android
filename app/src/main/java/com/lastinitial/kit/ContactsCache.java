package com.lastinitial.kit;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ak on 5/14/14.
 */
public class ContactsCache {
    private static final Map<String, Uri> IMAGES = new HashMap<String, Uri>();
    private static final Map<String, String> NAMES = new HashMap<String, String>();

    private ContentResolver mContentResolver;

    public ContactsCache(Context context) {
        mContentResolver = context.getContentResolver();
    }

    Uri getContactImage(String lookupKey) {
        if (!IMAGES.containsKey(lookupKey)) {
            populateCache(lookupKey);
            Log.v("ContactsCache", "Image miss! " + lookupKey);
        } else {
//            Log.v("ContactsCache", "Image hit! " + lookupKey);
        }
        return IMAGES.get(lookupKey);
    }

    String getContactName(String lookupKey) {
        if (!NAMES.containsKey(lookupKey)) {
            populateCache(lookupKey);
            Log.v("ContactsCache", "Name miss! " + lookupKey);
        } else {
//            Log.v("ContactsCache", "Name hit! " + lookupKey);
        }
        return NAMES.get(lookupKey);
    }

    void setContactImage(String lookupKey, Uri contactImage) {
        IMAGES.put(lookupKey, contactImage);
    }

    void setContactImage(String lookupKey, String contactImageUriString) {
        Uri contactImage;
        if (contactImageUriString != null) {
            contactImage = Uri.parse(contactImageUriString);
        } else {
            contactImage = null;
        }
        IMAGES.put(lookupKey, contactImage);
    }
    void setContactName(String lookupKey, String name) {
        NAMES.put(lookupKey, name);
    }

    private void populateCache(String lookupKey) {
        final Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        Uri res = Contacts.lookupContact(mContentResolver, lookupUri);
        String[] lookupFields = {
                Contacts._ID,
                Contacts.DISPLAY_NAME,
                Contacts.PHOTO_THUMBNAIL_URI,
        };
        Cursor c = mContentResolver.query(res, lookupFields, null, null, null);
        if (c.moveToFirst()) {
            String thumbnailUri = c.getString(c.getColumnIndex(Contacts.PHOTO_THUMBNAIL_URI));
            if (thumbnailUri != null) {
                IMAGES.put(lookupKey, Uri.parse(thumbnailUri));
            }
            NAMES.put(lookupKey, c.getString(c.getColumnIndex(Contacts.DISPLAY_NAME)));
        }
    }
}
