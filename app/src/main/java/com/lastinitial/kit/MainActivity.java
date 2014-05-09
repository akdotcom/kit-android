package com.lastinitial.kit;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity implements
        LoaderManager.LoaderCallbacks<Cursor>{

    public static final String DETAILS_DB_ROWID = "com.lastinitial.kit.DETAILS_DB_ROWID";

    public static final int FREQUENCY_DAILY = 0;
    public static final int FREQUENCY_WEEKLY = 1;
    public static final int FREQUENCY_MONTHLY = 2;
    public static final int FREQUENCY_YEARLY = 3;

    public static final int CONTACT_TYPE_SYSTEM = 1;
    public static final int CONTACT_TYPE_MANUAL = 2;
    public static final int CONTACT_TYPE_CALL = 3;
    public static final int CONTACT_TYPE_SMS = 4;

    // Loader for this component
    private static final int CONTACTS_LOADER = 0;

    private ContactsDbAdapter mDbHelper;
    private ResourceCursorAdapter mAdapter;
    private LastContactUpdater mUpdater = new LastContactUpdater();

    public static final int LOW_PRIORITY_TEXT_COLOR = Color.parseColor("#666666");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AnalyticsUtil.logScreenImpression(this, "com.lastinitial.kit.MainActivity");

        mDbHelper = new ContactsDbAdapter(this);
        mDbHelper.open();

        mUpdater = new LastContactUpdater();
//        mUpdater.update(this, mDbHelper); // This now happens in onResume


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

        View listBackground = findViewById(R.id.listBackground);

        TextView snoozeIcon = (TextView)listBackground.findViewById(R.id.snoozeIcon);
        snoozeIcon.setTypeface(FontUtils.getFontAwesome(this));
        TextView snoozeText = (TextView)listBackground.findViewById(R.id.snoozeText);
        long now = System.currentTimeMillis();
        snoozeText.setText(RelativeDateUtils.getRelativeTimeSpanString(
                now + SnoozeUtil.DEFAULT_SNOOZE_TIME, now));

        TextView talkedIcon = (TextView)listBackground.findViewById(R.id.talkedIcon);
        talkedIcon.setTypeface(FontUtils.getFontAwesome(this));

        ListView listView = (ListView) findViewById(R.id.entriesList);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Uri contactUri = (Uri) view.getTag(R.id.view_lookup_uri);
                ContactsContract.QuickContact.showQuickContact(
                        adapterView.getContext(),
                        view, contactUri,
                        ContactsContract.QuickContact.MODE_LARGE,
                        null);
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

                int nextContactIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
                long nextContact = cursor.getLong(nextContactIndex);

                int[] attributes = { android.R.attr.colorBackground };
                TypedArray array = getTheme().obtainStyledAttributes(attributes);
                int colorBackground = array.getColor(0, Color.WHITE);
                TextView contactName = (TextView) view.findViewById(R.id.contactName);
                TextView nextTv = (TextView) view.findViewById(R.id.next_value);
                TextView clockIcon = (TextView) view.findViewById(R.id.next_description);
                clockIcon.setTypeface(FontUtils.getFontAwesome(context));
                TextView alarmIcon = (TextView) view.findViewById(R.id.alarmIcon);
                alarmIcon.setTypeface(FontUtils.getFontAwesome(getApplicationContext()));

                CharSequence nextText = null;
                long currentTime = System.currentTimeMillis();
                if (nextContact > 0) {
                    nextText =
                            RelativeDateUtils.getRelativeTimeSpanString(nextContact, currentTime);
                } else {
                    nextText = "ASAP";
                }

                if (nextContact < System.currentTimeMillis()) {
                    view.setBackgroundColor(Color.WHITE);
                    contactName.setTextColor(Color.BLACK);
                    alarmIcon.setVisibility(View.VISIBLE);
                    nextTv.setVisibility(View.GONE);
                    clockIcon.setVisibility(View.GONE);
                } else {
                    // Explicitly do this because otherwise reused imageViews sometimes keep their
                    // former color
                    view.setBackgroundColor(colorBackground);
                    contactName.setTextColor(LOW_PRIORITY_TEXT_COLOR);
                    alarmIcon.setVisibility(View.GONE);
                    nextTv.setVisibility(View.VISIBLE);
                    clockIcon.setVisibility(View.VISIBLE);
                    nextTv.setText(nextText);
                }

                view.setTag(R.id.view_lookup_uri, lookupUri);
                final int dbIdIndex = cursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID);
                final long dbId = cursor.getLong(dbIdIndex);
                view.setTag(R.id.view_db_rowid, dbId);

                ImageView ivContactOptions = (ImageView) view.findViewById(R.id.contactOptions);
                ivContactOptions.setTag(R.id.view_lookup_uri, lookupUri);
                ivContactOptions.setTag(R.id.view_db_rowid, dbId);
                ivContactOptions.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Uri contactUri = (Uri) view.getTag(R.id.view_lookup_uri);
                        Intent intent = new Intent(getApplicationContext(), EntryDetailsActivity.class);
                        intent.setData(contactUri);
                        intent.setAction(Intent.ACTION_MAIN);
                        startActivity(intent);
                    }
                });
            }
        };
        listView.setAdapter(mAdapter);

        SwipeDismissListViewTouchListener touchListener =
                new SwipeDismissListViewTouchListener(
                        listView,
                        listBackground,
                        new SwipeDismissListViewTouchListener.DismissCallbacks() {
                            @Override
                            public boolean canDismiss(int position) {
                                return true;
                            }

                            public void onDismiss(ListView listView,
                                                  int[] reverseSortedPositions,
                                                  boolean isDismissRight) {
                                for (int position : reverseSortedPositions) {
                                    long itemId = mAdapter.getItemId(position);
                                    if (isDismissRight) {
                                        SnoozeUtil snoozeUtil = new SnoozeUtil();
                                        snoozeUtil.snoozeContact(
                                                getApplicationContext(),
                                                itemId,
                                                SnoozeUtil.DEFAULT_SNOOZE_TIME);
                                    } else {
                                        mDbHelper.updateLastContacted(itemId,
                                                System.currentTimeMillis(),
                                                CONTACT_TYPE_MANUAL);
                                    }
                                }
                                // Todo(ak): This is gross. Because the ResourceCursorAdapter
                                // does all this gunk below (like swapping cursors)
                                // to load things in the background before
                                // rendering, .notifyDataSetChanged() gets called all the time
                                // and can't initiate a refresh of the list because that would
                                // cause an infinite loop.
                                // mAdapter.notifyDataSetChanged();
                                getLoaderManager().restartLoader(
                                        CONTACTS_LOADER, null, MainActivity.this);
                            }
                        });

        listView.setOnTouchListener(touchListener);
        listView.setOnScrollListener(touchListener.makeScrollListener());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Explicitly hide these in case they've been left visible
        findViewById(R.id.snoozeBackground).setVisibility(View.INVISIBLE);
        findViewById(R.id.talkedBackground).setVisibility(View.INVISIBLE);

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        boolean isRelease = (0 == (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
        if (isRelease) {
            MenuItem notifications = menu.findItem(R.id.action_run_notifications);
            notifications.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_run_notifications) {
            Intent intent = new Intent(this, PeriodicUpdater.class);
            sendBroadcast(intent);
        } else if (id == R.id.action_about) {
            Intent intent = new Intent(this, About.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
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
                intent.setData(contactUri);
                intent.setAction(Intent.ACTION_EDIT);
                startActivity(intent);
            }
        }
    }
}