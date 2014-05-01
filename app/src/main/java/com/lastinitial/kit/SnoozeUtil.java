package com.lastinitial.kit;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
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
    protected void onHandleIntent(Intent intent) {
        if (ACTION_SNOOZE.equals(intent.getAction())) {
            long rowId = intent.getLongExtra(MainActivity.DETAILS_DB_ROWID, -1L);
            if (rowId < 0) {
                Log.e("SnoozeUtil", "Snooze called without rowId");
            }
            ContactsDbAdapter contactsDbAdapter = new ContactsDbAdapter(this);
            contactsDbAdapter.open();

            long nextContact = System.currentTimeMillis() + DEFAULT_SNOOZE_TIME;
            contactsDbAdapter.updateNextContact(rowId, nextContact);

            contactsDbAdapter.close();

            Intent notificationIntent = new Intent(this, PeriodicUpdater.class);
            sendBroadcast(notificationIntent);
        } else {
            Log.v("SnoozeUtil", "Unkonwn action: " + intent.getAction());
        }
    }
}
