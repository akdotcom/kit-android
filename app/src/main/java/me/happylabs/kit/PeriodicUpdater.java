package me.happylabs.kit;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.util.AbstractList;

import me.happylabs.kit.app.R;

/**
 * Created by ak on 3/31/14.
 */
public class PeriodicUpdater extends BroadcastReceiver {
    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v("PeriodicUpdater", "onReceive called");

        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            Log.v("PeriodicUpdater", "Called on boot!");
            // Booted! Set up the alarm to fire two times a day.
            registerAlarmService(context);
        } else {
            Log.v("BackgroundUpdateService", "Called with action: " + intent.getAction());
            NotificationManager notificationManager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            LastContactUpdater lastContactUpdater = new LastContactUpdater();
            ContactsDbAdapter contactsDb = new ContactsDbAdapter(context);
            contactsDb.open();

            lastContactUpdater.update(context, contactsDb);
            Cursor cursor = contactsDb.fetchAllContacts();
            while (cursor.moveToNext()) {
                Log.v("PeriodicUpdater", "evaluating row " + cursor.getLong(cursor.getColumnIndex(contactsDb.KEY_ROWID)));
                int nextContactIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
                int lastContactedIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED);
                int lookupKeyIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_LOOKUP_KEY);
                long nextContact = cursor.getLong(nextContactIndex);
                long lastContacted = cursor.getLong(lastContactedIndex);

                if (lastContacted != 0 && nextContact < System.currentTimeMillis()) {
                    sendReminder(context, notificationManager, cursor);
                }
            }
        }
    }


    protected void registerAlarmService(Context context) {
        Log.v("PeriodicUpdater", "registerAlarmService called");
        alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent periodicIntent = new Intent(context, PeriodicUpdater.class);
        alarmIntent = PendingIntent.getBroadcast(context, 0, periodicIntent, 0);
        // TODO(Ak): set these to proper values (HALF_DAY)
        alarmMgr.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_HALF_DAY,
                alarmIntent);
    }

    protected void sendReminder(Context context,
                                NotificationManager notificationManager,
                                Cursor dbCursor) {
        Log.v("PeriodicUpdate", "sendReminder called");
        int lookupKeyIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LOOKUP_KEY);
        int freqTypeIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_FREQUENCY_TYPE);
        int freqScalarIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_FREQUENCY_SCALAR);

        String lookupKey = dbCursor.getString(lookupKeyIndex);
        int freqType = dbCursor.getInt(freqTypeIndex);
        int freqScalar = dbCursor.getInt(freqScalarIndex);

        // Fetch information about the user we're reminding about
        Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        ContentResolver resolver = context.getContentResolver();
        Uri contentUri = ContactsContract.Contacts.lookupContact(resolver, lookupUri);
        String[] lookupFields = {
                Contacts._ID,
                Contacts.DISPLAY_NAME,
                Contacts.PHOTO_URI,
        };
        Cursor c = resolver.query(contentUri, lookupFields, null, null, null);
        if (!c.moveToFirst()) {
            Log.e("PeriodicUpdater",
                    "Reminder requested for non-existent contact. Lookup key: " + lookupKey);
        }

        // Grab info from the different cursors as needed to fill in the notification details
        String name = c.getString(c.getColumnIndex(Contacts.DISPLAY_NAME));
        String frequencyDescription = TextUtils.describeFrequency(freqType, freqScalar);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        intent.setData(uri);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setContentTitle("Keep in touch with " + name)
                .setContentText("It's been " + frequencyDescription
                        + " since you last talked")
                .setContentIntent(pendingIntent);

        // Use the profile photo of the contact in question if available.
        String photoUriString = c.getString(c.getColumnIndex(Contacts.PHOTO_URI));
        if (photoUriString != null) {
            Uri imageUri = Uri.parse(photoUriString);
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        context.getContentResolver(), imageUri);
                Resources res = context.getResources();
                int height = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
                int width = (int) res.getDimension(android.R.dimen.notification_large_icon_width);
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
                notificationBuilder.setLargeIcon(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        notificationBuilder.setSmallIcon(context.getApplicationInfo().icon);

        // Use the db row id as the notification id so we know how to update it in the future.
        int rowId = (int) dbCursor.getLong(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID));
        Notification notification = notificationBuilder.build();
        notificationManager.notify(rowId, notification);
    }
}
