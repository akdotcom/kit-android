package me.happylabs.kit;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.*;

import me.happylabs.kit.app.R;


public class MainActivity extends ActionBarActivity implements
        LoaderManager.LoaderCallbacks<Cursor>{

    public static final String DETAILS_LOOKUP_URI = "me.happylabs.kit.DETAILS_LOOKUP_URI";
    public static final String DETAILS_DB_ROWID = "me.happylabs.kit.DETAILS_DB_ROWID";

    public static final int FREQUENCY_HOURLY = 0;
    public static final int FREQUENCY_DAILY = 1;
    public static final int FREQUENCY_WEEKLY = 2;
    public static final int FREQUENCY_MONTHLY = 3;

    public static final int CONTACT_TYPE_PHONE = 1;
    public static final int CONTACT_TYPE_SMS = 2;
    public static final int CONTACT_TYPE_MANUAL = 3;

    public static final int INSERT_ID = Menu.FIRST;
    public static final int RUN_NOTIFICATIONS = Menu.FIRST;

    // Loader for this component
    private static final int CONTACTS_LOADER = 0;

    private ContactsDbAdapter mDbHelper;
    private ResourceCursorAdapter mAdapter;
    private LastContactUpdater mUpdater = new LastContactUpdater();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDbHelper = new ContactsDbAdapter(this);
        mDbHelper.open();

        mUpdater = new LastContactUpdater();
        mUpdater.update(this, mDbHelper);

        ComponentName receiver = new ComponentName(this, PeriodicUpdater.class);
        PackageManager pm = this.getPackageManager();

        int state = pm.getComponentEnabledSetting(receiver);

        if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            // We've been started up for the first time
            pm.setComponentEnabledSetting(receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);

            // Manually start background service
            PeriodicUpdater periodicUpdater = new PeriodicUpdater();
            periodicUpdater.registerAlarmService(this);
        }

        // Now for the UI
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(R.id.entriesList);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Uri contactUri = (Uri) view.getTag(R.id.view_lookup_uri);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(contactUri);
                startActivity(intent);
            }
        });

        /*
         * Initializes the CursorLoader. The URL_LOADER value is eventually passed
         * to onCreateLoader().
         */
        getLoaderManager().initLoader(CONTACTS_LOADER, null, this);

        mAdapter = new ResourceCursorAdapter(
                this,
                R.layout.item_last_contact,
                null, 0 ) {
            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                TextView tvName = (TextView) view.findViewById(R.id.contactName);
                ImageView imageView = (ImageView) view.findViewById(R.id.quickbadge);

                int lookupKeyIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_LOOKUP_KEY);
                String lookupKey = cursor.getString(lookupKeyIndex);
                final Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);

                ContentResolver resolver = context.getContentResolver();
                Uri res = Contacts.lookupContact(resolver, lookupUri);
                String[] lookupFields = {
                        Contacts._ID,
                        Contacts.LOOKUP_KEY,
                        Contacts.DISPLAY_NAME,
                        Contacts.PHOTO_THUMBNAIL_URI,
                        Contacts.LAST_TIME_CONTACTED
                };
                Cursor c = resolver.query(res, lookupFields, null, null, null);
                if (c.moveToFirst()) {
                    tvName.setText(c.getString(c.getColumnIndex(PhoneLookup.DISPLAY_NAME)));
                    String thumbnailUri = c.getString(c.getColumnIndex(Contacts.PHOTO_THUMBNAIL_URI));
                    if (thumbnailUri != null) {
                        imageView.setImageURI(Uri.parse(thumbnailUri));
                    } else {
                        imageView.setImageResource(R.drawable.ic_action_person);
                    }
                    c.close();
                } else {
                    tvName.setText("Unknown");
                }

                view.setTag(R.id.view_lookup_uri, lookupUri);
                final int dbIdIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID);
                final long dbId = cursor.getLong(dbIdIndex);
                view.setTag(R.id.view_db_rowid, dbId);

                View optionsView = view.findViewById(R.id.contactOptions);
                optionsView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        contactDetails(lookupUri, dbId);
                    }
                });
            }
        };
        listView.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUpdater.update(this, mDbHelper);
        getLoaderManager().restartLoader(CONTACTS_LOADER, null, this);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle)
    {
        /*
         * Takes action based on the ID of the Loader that's being created
         */
        switch (loaderID) {
            case CONTACTS_LOADER:
                // Returns a new CursorLoader
                return new SimpleCursorLoader(this) {
                    @Override
                    public Cursor loadInBackground() {
                        return mDbHelper.fetchAllContacts();
                    }
                };
            default:
                // An invalid id was passed in
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        /*
         * Moves the query results into the adapter, causing the
         * ListView fronting this adapter to re-display
         */
        mAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);    }

    //
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, R.id.run_notifications, 0, R.string.run_notifications);
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.run_notifications) {
            Intent intent = new Intent(this, PeriodicUpdater.class);
            sendBroadcast(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    public void contactDetails(Uri contactUri, Long rowId) {
        Intent intent = new Intent(this, EntryDetailsActivity.class);
        intent.putExtra(DETAILS_LOOKUP_URI, contactUri.toString());
        if (rowId != null) {
            intent.putExtra(DETAILS_DB_ROWID, rowId);
        }
        startActivity(intent);
    }

    public void addContact(View view) {
        addContact();
    }

    public void addContact() {
        Intent pickContactIntent = new Intent( Intent.ACTION_PICK, Contacts.CONTENT_FILTER_URI );
        pickContactIntent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(pickContactIntent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == Activity.RESULT_OK) {
                Uri contactUri = data.getData();
                Log.v(this.getClass().getCanonicalName(), "picker returned URI: " + contactUri);
                Intent intent = new Intent(this, EntryDetailsActivity.class);
                intent.putExtra(DETAILS_LOOKUP_URI, contactUri.toString());
                startActivity(intent);
            }
        }
    }
}