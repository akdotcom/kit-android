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
import android.text.format.DateUtils;
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
                int nextContactIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
                int lastContactedIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED);

                long nextContact = cursor.getLong(nextContactIndex);
                long lastContacted = cursor.getLong(lastContactedIndex);

                if (lastContacted != 0 && nextContact < System.currentTimeMillis()) {
                    sendReminder(context, notificationManager, cursor);
                }
            }
        }
    }


    protected void registerAlarmService(Context context) {
        alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent periodicIntent = new Intent(context, PeriodicUpdater.class);
        alarmIntent = PendingIntent.getBroadcast(context, 0, periodicIntent, 0);
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
        int lastContactIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED);
        int rowIdIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID);

        String lookupKey = dbCursor.getString(lookupKeyIndex);
        long lastContact = dbCursor.getLong(lastContactIndex);
        long rowId = dbCursor.getLong(rowIdIndex);

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
        CharSequence durationDescription = DateUtils.getRelativeTimeSpanString(
                lastContact, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS);

        Intent intent = new Intent(context, EntryDetailsActivity.class);
        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        intent.setData(uri);
        intent.putExtra(MainActivity.DETAILS_DB_ROWID, rowId);
        intent.setAction(Intent.ACTION_MAIN);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(name)
                .setContentText("You last talked " + durationDescription)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_LOW);

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

        // Use the db row id as the notification id so we know how to update it in the future.
        int notificationId =
                (int) dbCursor.getLong(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID));
        Notification notification = notificationBuilder.build();
        notificationManager.notify(notificationId, notification);
    }
}
