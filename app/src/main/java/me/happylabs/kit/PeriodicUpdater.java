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
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import java.io.IOException;
import java.util.AbstractList;
import java.util.LinkedList;
import java.util.List;

import me.happylabs.kit.app.R;

/**
 * Created by ak on 3/31/14.
 */
public class PeriodicUpdater extends BroadcastReceiver {
    private static String TAG = "PeriodicUpdater";
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
            updateNotifications(context);
        }
    }

    public void updateNotifications(Context context) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        LastContactUpdater lastContactUpdater = new LastContactUpdater();
        ContactsDbAdapter contactsDb = new ContactsDbAdapter(context);
        contactsDb.open();

        lastContactUpdater.update(context, contactsDb);
        Cursor dbCursor = contactsDb.fetchAllContacts();
        if (dbCursor == null) {
            return;
        }

        int numNotifications = 0;
        ContentResolver resolver = context.getContentResolver();
        int rowIdIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID);
        int lookupKeyIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LOOKUP_KEY);
        int nextContactIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
        int lastContactedIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED);

        Resources res = context.getResources();
        int iconHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
        int iconWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);

        List<String> names = new LinkedList<String>();

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.notification_icon)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_LOW);

        boolean largeImageSet = false;

        while (dbCursor.moveToNext()) {
            long nextContact = dbCursor.getLong(nextContactIndex);
            if (nextContact < System.currentTimeMillis()) {
                String lookupKey = dbCursor.getString(lookupKeyIndex);
                Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
                String[] lookupFields = {
                        Contacts.DISPLAY_NAME,
                        Contacts.PHOTO_URI,
                };
                Cursor c = resolver.query(lookupUri, lookupFields, null, null, null);
                if (c != null && c.moveToFirst()) {
                    numNotifications++;
                }
                else {
                    Log.e(TAG, "Reminder requested for missing contact. Lookup key: " + lookupKey);
                    return;
                }
                String photoUriString = c.getString(c.getColumnIndex(Contacts.PHOTO_URI));
                if (!largeImageSet && photoUriString != null) {
                    Uri imageUri = Uri.parse(photoUriString);
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                                context.getContentResolver(), imageUri);
                        bitmap = Bitmap.createScaledBitmap(bitmap, iconWidth, iconHeight, false);
                        notificationBuilder.setLargeIcon(bitmap);
                        largeImageSet = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                names.add(c.getString(c.getColumnIndex(Contacts.DISPLAY_NAME)));
                c.close();
            } else {
                // Results are sorted by nextContact time descending, so we can stop iterating
                break;
            }
        }
        if (!largeImageSet) {
            // TODO(ak): do something better here?
        }

        if (numNotifications == 1) {
            notificationBuilder.setContentTitle("KIT: " + names.get(0));
            // Contacts were returned in order of nextContact descending, so if there's only one
            // person to notify on, it's the first contact in the list.
            dbCursor.moveToFirst();
            long lastContacted = dbCursor.getLong(lastContactedIndex);
            String durationDescription = null;
            if (lastContacted == 0) {
                durationDescription = context.getString(R.string.its_been_a_while);
            } else {
                durationDescription = context.getString(R.string.you_last_talked) + " ";
                durationDescription += DateUtils.getRelativeTimeSpanString(
                        lastContacted, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS);
            }
            notificationBuilder.setContentText(durationDescription);

            String lookupKey = dbCursor.getString(lookupKeyIndex);
            long rowId = dbCursor.getLong(rowIdIndex);
            Intent intent = new Intent(context, EntryDetailsActivity.class);
            Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
            intent.setData(uri);
            intent.putExtra(MainActivity.DETAILS_DB_ROWID, rowId);
            intent.setAction(Intent.ACTION_MAIN);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
            notificationBuilder.setContentIntent(pendingIntent);

            Notification notification = notificationBuilder.build();
            notificationManager.notify(0, notification);

        } else if (numNotifications > 1) {
            notificationBuilder.setContentTitle(context.getString(R.string.keep_in_touch));

            // TODO(ak): Think about how to localize this.
            String contentText = "with ";
            if (numNotifications == 2) {
                contentText += names.get(0) + " and " + names.get(1);
            } else {
                contentText += names.get(0) + " and " + (numNotifications-1) + " others";
            }
            notificationBuilder.setContentText(contentText);
            notificationBuilder.setNumber(numNotifications);

            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
            notificationBuilder.setContentIntent(pendingIntent);

            Notification notification = notificationBuilder.build();
            notificationManager.notify(0, notification);
        } else {
            // No notifications to be sent
        }

        dbCursor.close();
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
}
