package com.lastinitial.kit;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

/**
 * Created by ak on 3/28/14.
 */
public class LastContactUpdater {
    public void update(Context context, ContactsDbAdapter contactsDb) {
        SharedPreferences prefs = context.getSharedPreferences(
                "UPDATES_INFO",
                0 /* MODE_PRIVATE */);
        long lastUpdate = prefs.getLong("LAST_UPDATE", 0);
        update(context, contactsDb, lastUpdate);
    }

    public void update(Context context, ContactsDbAdapter contactsDb, long lastUpdate) {
        ContentResolver resolver = context.getContentResolver();
        String[] lookupFields = {
                Contacts.LOOKUP_KEY,
                Contacts.LAST_TIME_CONTACTED,
        };

        Cursor cursor = contactsDb.fetchAllContacts();
        while (cursor.moveToNext()) {
            boolean shouldUpdate = false;

            String dbLookupKey = cursor.getString(cursor.getColumnIndex(
                    ContactsDbAdapter.KEY_LOOKUP_KEY));
            long dbLastContact = cursor.getLong(cursor.getColumnIndex(
                    ContactsDbAdapter.KEY_LAST_CONTACTED));
            int dbLastContactType = cursor.getType(
                    cursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACT_TYPE));

            Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, dbLookupKey);
            Cursor infoCursor = resolver.query(lookupUri, lookupFields, null, null, null);
            if (infoCursor.moveToFirst()) {
                long infoLastContact = infoCursor.getLong(infoCursor.getColumnIndex(Contacts.LAST_TIME_CONTACTED));
                if (infoLastContact > dbLastContact) {
                    Log.v("LastContactUpdater", "Updating lastContact from: " + dbLastContact + " to " + infoLastContact);
                    dbLastContact = infoLastContact;
                    dbLastContactType = MainActivity.CONTACT_SYSTEM;
                    shouldUpdate = true;
                }
                String infoLookupKey = infoCursor.getString(infoCursor.getColumnIndex(Contacts.LOOKUP_KEY));
                if (!dbLookupKey.equals(infoLookupKey)) {
                    Log.v("LastContactUpdater", "Updating lookupKey from: " + dbLookupKey + " to " + infoLookupKey);
                    dbLookupKey = infoLookupKey;
                    shouldUpdate = true;
                }
            }
            infoCursor.close();
            if (shouldUpdate) {
                contactsDb.updateContact(
                        cursor.getLong(cursor.getColumnIndex(contactsDb.KEY_ROWID)),
                        dbLookupKey,
                        dbLastContact,
                        dbLastContactType);
            }
        }
        cursor.close();

/** MANUALLY WALK THROUGH PHONE LOGS **/

//        ContentResolver resolver = context.getContentResolver();
//
//        String[] projection = {
//                CallLog.Calls._ID,
//                CallLog.Calls.NUMBER,
//                CallLog.Calls.DATE
//        };
//
//        // TODO(ak): limit to completed calls.
//        Cursor callCursor = resolver.query(
//                CallLog.Calls.CONTENT_URI,
//                projection,
//                "WHERE " + CallLog.Calls.DATE + " > ?",
//                new String[] { Long.toString(lastUpdate) },
//                CallLog.Calls.DATE + " DESC");
//
//        int numberIndex = callCursor.getColumnIndex(CallLog.Calls.NUMBER);
//        String[] keyProjection = {
//                PhoneLookup._ID,
//                PhoneLookup.LOOKUP_KEY
//        };
//
//        Map<Uri, Long> lastContacted = new HashMap<Uri, Long>();
//        while (callCursor.moveToNext()) {
//            // Lookup contact based on number
//            String number = callCursor.getString(numberIndex);
//            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
//            Cursor contactCursor = resolver.query(uri, keyProjection, null, null, null);
//
//            // If there's a corresponding contact
//            if (contactCursor.moveToFirst()) {
//                // Fetch the lookup_key for that contact
//                String lookupKey = contactCursor.getString(
//                        contactCursor.getColumnIndex(PhoneLookup.LOOKUP_KEY));
//                long contactId = contactCursor.getLong(
//                        contactCursor.getColumnIndex(PhoneLookup._ID));
//                Cursor dbCursor = contactsDb.fetchContact(lookupKey);
//                if (dbCursor != null) {
//                    long dbLastContacted = dbCursor.getLong(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED));
//                    long contactTime = callCursor.getLong(callCursor.getColumnIndex(CallLog.Calls.DATE));
//
//                }
//
//                Uri lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
//                Log.v("LastContactUpdater", lookupUri.toString());
//                long contactTime = callCursor.getLong(callCursor.getColumnIndex(CallLog.Calls.DATE));
//                if (lastContacted.containsKey(lookupUri)) {
//                    if (lastContacted.get(lookupUri) < contactTime) {
//                        lastContacted.put(lookupUri, contactTime);
//                    }
//                } else {
//                    lastContacted.put(lookupUri, contactTime);
//                }
//            }
//            contactCursor.close();
//        }
//        callCursor.close();
//        return lastContacted;

    }
}
