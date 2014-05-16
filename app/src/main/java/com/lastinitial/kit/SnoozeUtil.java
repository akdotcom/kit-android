package com.lastinitial.kit;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.text.format.DateUtils;
import android.util.Log;

/**
 * Created by ak on 4/30/14.
 */
public class SnoozeUtil extends IntentService {
    protected static final String ACTION_SNOOZE = "com.lastinitial.kit.SNOOZE";

    protected static final long DEFAULT_SNOOZE_TIME = 3L * DateUtils.DAY_IN_MILLIS;

    public SnoozeUtil() {
        super("SnoozeUtil");
    }

    public SnoozeUtil(String name) {
        super(name);
    }

    @Override
    protected synchronized void onHandleIntent(Intent intent) {
        if (ACTION_SNOOZE.equals(intent.getAction())) {
            long rowId = intent.getLongExtra(MainActivity.DETAILS_DB_ROWID, -1L);
            if (rowId < 0) {
                Log.e("SnoozeUtil", "Snooze called without rowId");
                return;
            }
            snoozeContact(this, rowId, DEFAULT_SNOOZE_TIME);

            Intent notificationIntent = new Intent(this, PeriodicUpdater.class);
            sendBroadcast(notificationIntent);
        } else {
            Log.v("SnoozeUtil", "Unkonwn action: " + intent.getAction());
        }
    }

    public synchronized void snoozeContact(Context context, long rowId, long snoozeTime) {
        ContactsDbAdapter contactsDbAdapter = new ContactsDbAdapter(context);
        contactsDbAdapter.open();

        Cursor c = contactsDbAdapter.fetchContact(rowId);
        long nextContact = c.getLong(c.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT));
        c.close();
        long now = System.currentTimeMillis();
        if (nextContact < now) {
            nextContact = now;
        }

        nextContact += snoozeTime;
        contactsDbAdapter.updateNextContact(rowId, nextContact);

        contactsDbAdapter.close();
    }
}
