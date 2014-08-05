package com.lastinitial.stitch;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ak on 3/28/14.
 */
public class LastContactUpdater {

    private static final String LAST_UPDATE_KEY = "LAST_UPDATE";

    // Walk through recent calls / texts and update the last contacted information for any
    // watched contacts.
    public void update(Context context, ContactsDbAdapter contactsDb) {
        SharedPreferences prefs = context.getSharedPreferences(
                "UPDATES_INFO",
                0 /* MODE_PRIVATE */);
        long lastUpdate = prefs.getLong(LAST_UPDATE_KEY, 0);
        Log.v("LastContactUpdater", "last update key: " + lastUpdate);
        if (lastUpdate == 0) {
            lastUpdate = System.currentTimeMillis();
            Log.v("LastContactUpdater", "Setting last update key to now");
        }
        long now = System.currentTimeMillis();
        update(context, contactsDb, lastUpdate);
        prefs.edit().putLong(LAST_UPDATE_KEY, now).apply();
    }

    private void update(Context context, ContactsDbAdapter contactsDb, long lastUpdate) {
        // Update contact keys in case people merge/change contacts
        contactsDb.updateLookupKeys();

        ContentResolver resolver = context.getContentResolver();

        Cursor dbCursor = contactsDb.fetchAllContacts();
        Map<String, Pair<Long, Long>> dbKeysIdsLc = new HashMap<String, Pair<Long, Long>>();
        while (dbCursor.moveToNext()) {
            String key = dbCursor.getString(
                    dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LOOKUP_KEY));
            long rowId = dbCursor.getLong(
                    dbCursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID));
            long lastContacted = dbCursor.getLong(
                    dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED));
            dbKeysIdsLc.put(key, Pair.create(rowId, lastContacted));
        }
        dbCursor.close();

        String[] projection = {
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
        };

        Cursor callCursor = resolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                CallLog.Calls.DATE + " > ? AND " + CallLog.Calls.DURATION + " > 15",
                new String[] { Long.toString(lastUpdate) },
                CallLog.Calls.DATE + " DESC");

        if (callCursor != null) {
            int numberIndex = callCursor.getColumnIndex(CallLog.Calls.NUMBER);
            int contactTimeIndex = callCursor.getColumnIndex(CallLog.Calls.DATE);

            processTelephonyCursor(
                    resolver,
                    contactsDb,
                    callCursor,
                    numberIndex,
                    contactTimeIndex,
                    MainActivity.CONTACT_TYPE_CALL,
                    dbKeysIdsLc);

            callCursor.close();
        }

        // Use hard coded content URIs because the Telephony APIs are v19+ only
        // TODO(ak): can we do this in a better way with API checks?
        String[] smsProjection = {
                "address", "date"
        };

        Uri messageByPhoneUri = null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // Use hard coded content URIs because the Telephony APIs are v19+ only
            messageByPhoneUri = Uri.parse("content://mms-sms/conversations");
        } else {
            messageByPhoneUri = Telephony.MmsSms.CONTENT_CONVERSATIONS_URI;
        }

        // Put this in try/catch because some phones (e.g. HTC One) don't seem to handle our
        // hack nicely and spit an NPE.
        Cursor smsCursor = null;
        try {
            smsCursor = resolver.query(
                    messageByPhoneUri,
                    smsProjection,
                    "date > " + Long.toString(lastUpdate),
                    null,
                    "date DESC");
        } catch (NullPointerException e) {
            Log.e("LastContactUpdater",
                    "Unable to query resolver for " + messageByPhoneUri.toString());
        }

        if (smsCursor != null) {
            int numberIndex = smsCursor.getColumnIndex(smsProjection[0]);
            int contactTimeIndex = smsCursor.getColumnIndex(smsProjection[1]);

            processTelephonyCursor(
                    resolver,
                    contactsDb,
                    smsCursor,
                    numberIndex,
                    contactTimeIndex,
                    MainActivity.CONTACT_TYPE_SMS,
                    dbKeysIdsLc);

            smsCursor.close();
        }
    }

    protected void processTelephonyCursor(
            ContentResolver resolver,
            ContactsDbAdapter contactsDb,
            Cursor telephonyCursor,
            int numIndex,
            int dateIndex,
            int contactType,
            Map<String, Pair<Long, Long>> dbKeyIdLcs) {
        String[] keyProjection = {
                PhoneLookup._ID,
                PhoneLookup.LOOKUP_KEY
        };

        while (telephonyCursor.moveToNext()) {
            // Lookup contact based on number
            String number = telephonyCursor.getString(numIndex);
            if (number == null || number.isEmpty()) {
                // Not sure why this might happen, but seeing some crash reports that seem to
                // indicate that it might.
                // Caused by: java.lang.IllegalArgumentException: URI: content://com.android.contacts/phone_lookup/, calling user: com.lastinitial.stitch, calling package:com.lastinitial.stitch
                continue;
            }
            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor contactCursor = resolver.query(uri, keyProjection, null, null, null);

            if (contactCursor == null) {
                // Didn't find them. Sad.
                return;
            }

            // If there's a corresponding contact
            if (contactCursor.moveToFirst()) {
                // Fetch the lookup_key for that contact
                String lookupKey = contactCursor.getString(
                        contactCursor.getColumnIndex(PhoneLookup.LOOKUP_KEY));
                if (dbKeyIdLcs.get(lookupKey) != null) {
                    long contactTime = telephonyCursor.getLong(dateIndex);

                    long dbLastContacted = dbKeyIdLcs.get(lookupKey).second;
                    if (contactTime > dbLastContacted) {
                        long dbRowId = dbKeyIdLcs.get(lookupKey).first;
                        contactsDb.updateLastContacted(dbRowId, contactTime, contactType);
                        // Update our local understanding so it's correct for other communications
                        dbKeyIdLcs.put(lookupKey, Pair.create(dbRowId, dbLastContacted));
                    }

                }
            }
            contactCursor.close();
        }
    }

    /**
     * Update the lastContact field for a single contact
     *
     * Works by fetching the most recent call / text message for each phone number associated
     * with a user, and updated the last contacted field if needed.
     * @return the updated value, if the value has been updated. Otherwise return null.
     */
    public Long updateContact(Context context, ContactsDbAdapter contactsDb, long rowId) {
        Log.v("LastContactUpdater", "updateContact called");
        Cursor dbCursor = contactsDb.fetchContact(rowId);
        // Got a report of CursorIndexOutOfBoundsException: Index 0 requested, with a size of 0.
        // Hopefully this avoids it?
        if (dbCursor == null || dbCursor.isAfterLast()) {
            return null;
        }
        long dbLastContacted = dbCursor.getLong(
                dbCursor.getColumnIndexOrThrow(ContactsDbAdapter.KEY_LAST_CONTACTED));
        String dbLookupKey = dbCursor.getString(
                dbCursor.getColumnIndexOrThrow(ContactsDbAdapter.KEY_LOOKUP_KEY));
        long newLastContacted = dbLastContacted;
        dbCursor.close();

        // Fetch the user's system contact ID
        final Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, dbLookupKey);
        ContentResolver resolver = context.getContentResolver();
        Uri res = Contacts.lookupContact(resolver, lookupUri);
        if (res == null) {
            // Contact no longer exists in the address book but is still in the Stitch DB
            return null;
        }
        String[] lookupFields = {
                Contacts._ID,
        };
        Cursor c = resolver.query(res, lookupFields, null, null, null);
        if (c == null || !c.moveToFirst()) {
            // Contact not found via lookup key
            return null;
        }
        long contactId = c.getLong(c.getColumnIndex(Contacts._ID));

        // Fetch all phone numbers belonging to this contact
        Cursor phoneCursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER },
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + contactId,
                null,
                null);
        List<String> numbers = new ArrayList<String>();
        if (phoneCursor != null) {
            int numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (phoneCursor.moveToNext()) {
                numbers.add(phoneCursor.getString(numberIndex));
            }
        }


        // For all these numbers, check if there's been a recent call
        for (String number : numbers) {
            Uri uri = Uri.withAppendedPath(CallLog.Calls.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor cursor = resolver.query(
                    uri,
                    new String[]{ CallLog.Calls.DATE },
                    CallLog.Calls.DATE + " > " + newLastContacted +
                            " AND " + CallLog.Calls.DURATION + " > 15",
                    null,
                    CallLog.Calls.DATE + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                newLastContacted = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
                Log.v("LastContactUpdater/updateContact", "new lastContacted: " + newLastContacted);
                contactsDb.updateLastContacted(
                        rowId, newLastContacted, MainActivity.CONTACT_TYPE_CALL);
                cursor.close();
            }
        }

        // For all the numbers, check if there's been a recent text message.
        // Use hard coded content URIs because the Telephony APIs are v19+ only
        Uri messageByPhoneUri = Uri.parse("content://mms-sms/messages/byphone/");
        for (String number: numbers) {
            Uri uri = Uri.withAppendedPath(messageByPhoneUri, Uri.encode(number));

            Cursor smsCursor = context.getContentResolver().query(
                    uri,
                    new String[] { "date" },
                    "date > " + newLastContacted,
                    null,
                    "date DESC");
            if (smsCursor != null && smsCursor.moveToFirst()) {
                newLastContacted = smsCursor.getLong(smsCursor.getColumnIndex("date"));
                Log.v("LastContactUpdater/updateContact", "new lastContacted: " + newLastContacted);
                contactsDb.updateLastContacted(
                        rowId, newLastContacted, MainActivity.CONTACT_TYPE_SMS);
                smsCursor.close();
            }
        }

        if (newLastContacted != dbLastContacted) {
            return newLastContacted;
        } else {
            return null;
        }
    }
}