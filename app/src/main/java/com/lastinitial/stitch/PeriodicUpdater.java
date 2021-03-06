package com.lastinitial.stitch;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Created by ak on 3/31/14.
 */
public class PeriodicUpdater extends BroadcastReceiver {
    public static final String NOTIFICATION_ID_EXTRA = "NOTIFICATION_ID_EXTRA";
    public static final int INVALID_NOTIFICATION_ID = -1;
    private static final int SINGLE_NOTIFICATION_ID = 0;
    private static final String TAG = "PeriodicUpdater";
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

        long numContacts = (int) contactsDb.numContacts();

        // Normally this logic would live inside of AnalyticsUtil / StitchApplication, but the
        // PeriodicUpdater doesn't necessarily run inside of the Application, so it can't rely
        // on there being an instance of the Application available.
        if (AnalyticsUtil.isRelease(context)) {
            GoogleAnalytics analytics = GoogleAnalytics.getInstance(context);
            Tracker tracker = analytics.newTracker(R.xml.app_tracker);
            tracker.send(new HitBuilders.EventBuilder()
                    .setCategory("heartbeats")
                    .setAction("numContacts")
                    .setValue(numContacts)
                    .build());
        }

        lastContactUpdater.update(context, contactsDb);
        Cursor dbCursor = contactsDb.fetchAllContacts();
        if (dbCursor == null) {
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        int rowIdIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID);
        int lookupKeyIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LOOKUP_KEY);
        int nextContactIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
        int lastContactedIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED);

        List<String> names = new LinkedList<String>();
        List<Uri> photos = new LinkedList<Uri>();
        List<Long> ids = new LinkedList<Long>();

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.notification_icon)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_LOW);

        while (dbCursor.moveToNext()) {
            long nextContact = dbCursor.getLong(nextContactIndex);
            if (nextContact < System.currentTimeMillis()) {
                String lookupKey = dbCursor.getString(lookupKeyIndex);
                Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
                String[] lookupFields = {
                        Contacts.LOOKUP_KEY,
                        Contacts.DISPLAY_NAME,
                        Contacts.PHOTO_URI,
                };
                Cursor c = resolver.query(lookupUri, lookupFields, null, null, null);
                if (c == null || !c.moveToFirst()) {
                    Log.e(TAG, "Reminder requested for missing contact. Lookup key: " + lookupKey);
                    return;
                }

                String photoUriString = c.getString(c.getColumnIndex(Contacts.PHOTO_URI));
                if (photoUriString != null) {
                    photos.add(Uri.parse(photoUriString));
                } else {
                    photos.add(null);
                }
                names.add(c.getString(c.getColumnIndex(Contacts.DISPLAY_NAME)));
                ids.add(dbCursor.getLong(rowIdIndex));

                c.close();
            } else {
                // Results are sorted by nextContact time descending, so we can stop iterating
                break;
            }
        }

        MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, MainActivity.MIXPANEL_TOKEN);
        JSONObject props = new JSONObject();
        try {
            props.put("Alerting Users", ids.size());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        mixpanel.track("Notified", props);

        if (ids.size() > 0) {
            int index = new Random().nextInt(ids.size());

            notificationBuilder.setContentTitle("Stitch with " + names.get(index));
            dbCursor.moveToPosition(index);
            long lastContacted = dbCursor.getLong(lastContactedIndex);
            String durationDescription = null;
            if (lastContacted == 0) {
                durationDescription = context.getString(R.string.its_been_a_while);
            } else {
                durationDescription = context.getString(R.string.you_last_talked) + " ";
                durationDescription += RelativeDateUtils.getRelativeTimeSpanString(
                        lastContacted, System.currentTimeMillis());
            }
            notificationBuilder.setContentText(durationDescription);
            if (photos.get(index) != null) {
                setNotificationImage(context, photos.get(index), notificationBuilder);
            }

            String lookupKey = dbCursor.getString(lookupKeyIndex);
            long rowId = dbCursor.getLong(rowIdIndex);

            Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
            Intent intent = new Intent(context, EntryDetailsActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(uri);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            notificationBuilder.setContentIntent(pendingIntent);

            Cursor c = LastContactUpdater.getPhoneNumbersForContact(context, lookupKey, null);
            if (c != null && c.moveToFirst()) {
                Intent callIntent = new Intent(context, EntryDetailsActivity.class);
                callIntent.setAction(Intent.ACTION_CALL);
                callIntent.setData(uri);
                callIntent.putExtra(NOTIFICATION_ID_EXTRA, SINGLE_NOTIFICATION_ID);
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                stackBuilder.addParentStack(EntryDetailsActivity.class);
                stackBuilder.addNextIntent(callIntent);
                PendingIntent callPIntent = stackBuilder.getPendingIntent(
                        0, PendingIntent.FLAG_CANCEL_CURRENT);
                String callString = context.getResources().getString(R.string.notification_call);
                notificationBuilder.addAction(
                        android.R.drawable.sym_action_call, callString, callPIntent);

                Intent smsIntent = new Intent(context, EntryDetailsActivity.class);
                smsIntent.setAction(Intent.ACTION_SENDTO);
                smsIntent.setData(uri);
                smsIntent.putExtra(NOTIFICATION_ID_EXTRA, SINGLE_NOTIFICATION_ID);
                stackBuilder = TaskStackBuilder.create(context);
                stackBuilder.addParentStack(EntryDetailsActivity.class);
                stackBuilder.addNextIntent(smsIntent);
                PendingIntent smsPIntent = stackBuilder.getPendingIntent(
                        0, PendingIntent.FLAG_CANCEL_CURRENT);
                String smsString = context.getResources().getString(R.string.notification_sms);
                notificationBuilder.addAction(
                        android.R.drawable.sym_action_chat, smsString, smsPIntent);
            }
            if (c != null) {
                c.close();
            }

            Intent snoozeIntent = new Intent(context, SnoozeUtil.class);
            snoozeIntent.setAction(SnoozeUtil.ACTION_SNOOZE);
            snoozeIntent.putExtra(MainActivity.DETAILS_DB_ROWID, rowId);
            snoozeIntent.putExtra(NOTIFICATION_ID_EXTRA, SINGLE_NOTIFICATION_ID);
            PendingIntent snoozePIntent =
                    PendingIntent.getService(
                            context, 0, snoozeIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            String snooze = context.getResources().getString(R.string.notification_snooze);
            notificationBuilder.addAction(
                    R.drawable.clock_notification_icon, snooze, snoozePIntent);

            Notification notification = notificationBuilder.build();
            notificationManager.notify(SINGLE_NOTIFICATION_ID, notification);
        } else {
            // No notifications to be sent. Cancel this notification in case there's somehow one
            // lingering.
            notificationManager.cancel(SINGLE_NOTIFICATION_ID);
        }

        dbCursor.close();
    }

    public static void cancelNotification(Context context, int id) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(id);
    }

    protected void setNotificationImage(Context context,
                                        Uri imageUri,
                                        Notification.Builder notificationBuilder) {
        Resources res = context.getResources();
        int iconHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
        int iconWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                    context.getContentResolver(), imageUri);
            bitmap = Bitmap.createScaledBitmap(bitmap, iconWidth, iconHeight, false);
            notificationBuilder.setLargeIcon(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    protected void registerAlarmService(Context context) {
        alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent periodicIntent = new Intent(context, PeriodicUpdater.class);
        alarmIntent = PendingIntent.getBroadcast(context, 0, periodicIntent, 0);
        alarmMgr.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                AlarmManager.INTERVAL_HALF_HOUR,
                AlarmManager.INTERVAL_HALF_DAY,
                alarmIntent);
    }
}
