package com.lastinitial.stitch;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ak on 8/11/14.
 */
public class EntryDetailsActivity extends Activity {
    private long rowId = -1L;
    private String lookupKey = null;
    private ContactsDbAdapter mDbHelper;
    private LastContactUpdater mLastContactUpdater;
    private MixpanelAPI mMixpanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        String screenName = "com.lastinitial.stitch.EntryDetailsActivity#" + intent.getAction();
        AnalyticsUtil.logScreenImpression(this, screenName);

        setContentView(R.layout.entry_details);
        ImageView heroPhoto = (ImageView) findViewById(R.id.heroPhoto);

        TextView tvLastContactIcon = (TextView) findViewById(R.id.lastContactIcon);
        TextView tvFrequencyIcon = (TextView) findViewById(R.id.kitEveryIcon);
        TextView tvNextContactIcon = (TextView) findViewById(R.id.nextDescriptionIcon);
        tvLastContactIcon.setTypeface(FontUtils.getFontAwesome(this));
        tvFrequencyIcon.setTypeface(FontUtils.getFontAwesome(this));
        tvNextContactIcon.setTypeface(FontUtils.getFontAwesome(this));

        rowId = intent.getLongExtra(MainActivity.DETAILS_DB_ROWID, -1L);
        Uri uri = intent.getData();

        long lastContacted = 0L;

        mMixpanel = MixpanelAPI.getInstance(this, MainActivity.MIXPANEL_TOKEN);

        if (intent.getAction().equals(Intent.ACTION_VIEW)) {
            mMixpanel.track("Notification Tap-through", null);
        }

        mDbHelper = new ContactsDbAdapter(this);
        mDbHelper.open();

        mLastContactUpdater = new LastContactUpdater();

        String[] contactFields = {
                Contacts.DISPLAY_NAME,
                Contacts.PHOTO_URI,
                Contacts.LOOKUP_KEY,
                Contacts.LAST_TIME_CONTACTED,
        };
        Cursor cursor = getContentResolver().query(uri, contactFields, null, null, null);
        if (cursor.moveToFirst()) {
            lookupKey = cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY));
            String name = cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME));
            setTitle(name);

            String photoUriString =
                    cursor.getString(cursor.getColumnIndex(Contacts.PHOTO_URI));
            if (photoUriString != null) {
                heroPhoto.setImageURI(Uri.parse(photoUriString));
            } else {
                heroPhoto.setImageResource(R.drawable.ic_action_person);
            }
            int lastContactedIndex = cursor.getColumnIndex(Contacts.LAST_TIME_CONTACTED);
            lastContacted = cursor.getLong(lastContactedIndex);

            // If we haven't set the rowId and this lookupKey corresponds to someone in Stitch's DB
            if (rowId == -1L && mDbHelper.hasContact(lookupKey)) {
                Cursor dbCursor = mDbHelper.fetchContact(lookupKey);
                rowId = dbCursor.getLong(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_ROWID));
                dbCursor.close();
            }
        } else {
            CharSequence text = "Contact not found!";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
            return;
        }

        if (rowId == -1L) {
            // Create database entry, default frequency = 1 month.
            rowId = mDbHelper.createContact(
                    cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)),
                    lastContacted,
                    MainActivity.CONTACT_TYPE_SYSTEM,
                    MainActivity.FREQUENCY_MONTHLY,
                    1);

            // Initialize last contacted time.
            mLastContactUpdater.updateContact(this, mDbHelper, rowId);
        }

        // We're done with the system contact information, so close the cursor.
        cursor.close();

        // Set up tabs
        TabHost tabHost = (TabHost) findViewById(android.R.id.tabhost);
        tabHost.setup();
        TabHost.TabSpec spec = tabHost.newTabSpec("CONTACT_TAB");
        spec.setIndicator("Contact");
        spec.setContent(R.id.contactTab);
        tabHost.addTab(spec);

        TabHost.TabSpec spec2 = tabHost.newTabSpec("SETTINGS_TAB");
        spec2.setIndicator("Settings");
        spec2.setContent(R.id.settingsTab);
        tabHost.addTab(spec2);

        if (intent.getAction() == Intent.ACTION_EDIT) {
            tabHost.setCurrentTab(1);
        }

        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String s) {
                AnalyticsUtil.logAction(
                        EntryDetailsActivity.this, "navigation", "tab-changed-to-" + s);
            }
        });

        // Fill in phone numbers on contact tab
        LinearLayout contactTab = (LinearLayout) findViewById(R.id.contactTab);
        String[] phoneTypeField =
                new String[] {
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL
                };
        Cursor c = mLastContactUpdater.getPhoneNumbersForContact(this, lookupKey, phoneTypeField);
        if (c != null) {
            int numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);
            int labelIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL);
            final List<String> uniqueNumbers = new ArrayList<String>();
            while (c.moveToNext()) {
                final String number = c.getString(numberIdx);
                CharSequence type =
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                getResources(),
                                c.getInt(typeIdx),
                                c.getString(labelIdx));
                type = type.toString().toUpperCase();

                // Avoid displaying identical phone numbers.
                // This is gross, but PhoneNumberUtils doesn't offer a comparator and I'm in a hurry
                boolean isUnique = true;
                for (String uniqueNumber : uniqueNumbers) {
                    if (PhoneNumberUtils.compare(this, uniqueNumber, number)) {
                        isUnique = false;
                        break;
                    }
                }
                if (!isUnique) {
                    continue;
                }
                uniqueNumbers.add(number);

                LayoutInflater inflater = getLayoutInflater();
                View phoneNumberView = inflater.inflate(R.layout.item_phonenumber, null);
                phoneNumberView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        JSONObject props = new JSONObject();
                        try {
                            props.put("Number of Phone Numbers", uniqueNumbers.size());
                            props.put("Source", "Details Screen");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        mMixpanel.track("Call Made", null);

                        Uri uri = Uri.parse("tel:" + number);
                        Intent intent = new Intent(Intent.ACTION_CALL, uri);
                        startActivity(intent);
                    }
                });
                TextView phoneNumber = (TextView) phoneNumberView.findViewById(R.id.phoneNumber);
                phoneNumber.setText(PhoneNumberUtils.formatNumber(number));
                TextView phoneType = (TextView) phoneNumberView.findViewById(R.id.phoneNumberType);
                phoneType.setText(type);
                TextView smsLogo = (TextView) phoneNumberView.findViewById(R.id.smsLogo);
                TextView phoneLogo = (TextView) phoneNumberView.findViewById(R.id.phoneLogo);
                smsLogo.setTypeface(FontUtils.getFontAwesome(this));
                phoneLogo.setTypeface(FontUtils.getFontAwesome(this));
                smsLogo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        JSONObject props = new JSONObject();
                        try {
                            props.put("Number of Phone Numbers", uniqueNumbers.size());
                            props.put("Source", "Details Screen");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        mMixpanel.track("SMS Sent", null);

                        Uri uri = Uri.parse("smsto:" + number);
                        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
                        startActivity(intent);
                    }
                });
                if (c.getPosition() == 0) {
                    phoneNumberView.findViewById(R.id.horizontalDivider).setVisibility(View.INVISIBLE);
                }

                contactTab.addView(phoneNumberView);
            }
        }
        c.close();

        // Set up the settings tab
        Resources resources = getResources();
        final String[] freqArray = resources.getStringArray(R.array.frequency);
        final int[] freqTypes = resources.getIntArray(R.array.frequencyTypes);
        final int[] freqScalars = resources.getIntArray(R.array.frequencyScalars);

        SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekBar.setMax(freqArray.length - 1);
        final TextView frequencyText = (TextView) findViewById(R.id.frequencyDescription);

        Cursor dbCursor = mDbHelper.fetchContact(rowId);
        int freqTypeIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_FREQUENCY_TYPE);
        int freqScalarIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_FREQUENCY_SCALAR);
        int dbFreqType = dbCursor.getInt(freqTypeIndex);
        int dbFreqScalar = dbCursor.getInt(freqScalarIndex);

        int index = 0;
        for ( ; freqTypes[index] != dbFreqType || freqScalars[index] != dbFreqScalar; index++) {
            // nothing really. Looking for the right index value.
        }
        frequencyText.setText(freqArray[index]);
        seekBar.setProgress(index);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mDbHelper.updateContactFrequency(rowId, freqTypes[i], freqScalars[i]);

                frequencyText.setText(freqArray[i]);
                Cursor c = mDbHelper.fetchContact(rowId);
                long nextContact = c.getLong(c.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT));
                c.close();
                updateNextContactTextView(nextContact);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        int lastContactIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED);
        long lastContact = dbCursor.getLong(lastContactIndex);
        updateLastContactTextView(lastContact);

        int nextContactIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
        long nextContact = dbCursor.getLong(nextContactIndex);
        updateNextContactTextView(nextContact);

        dbCursor.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Long updatedTime = mLastContactUpdater.updateContact(this, mDbHelper, rowId);
        if (updatedTime != null) {
            updateLastContactTextView(updatedTime);
            Cursor c = mDbHelper.fetchContact(rowId);
            int nextContactIndex = c.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
            updateNextContactTextView(c.getLong(nextContactIndex));
            c.close();
        }
    }

    public void updateTimingTextView(TextView textView, Long newTime) {
        CharSequence lastContactString =
                RelativeDateUtils.getRelativeTimeSpanString(newTime, System.currentTimeMillis());
        textView.setText(lastContactString);
        textView.setTag(R.id.view_time_millis, new Long(newTime));
        textView.setTypeface(Typeface.DEFAULT);
    }

    public void updateLastContactTextView(Long lastContact) {
        TextView tvLastContact = (TextView) findViewById(R.id.lastContact);
        if (lastContact == null || lastContact == 0) {
            tvLastContact.setText(R.string.unknown_time);
        } else {
            updateTimingTextView(tvLastContact, lastContact);
        }
    }

    public void updateNextContactTextView(Long nextContact) {
        TextView tvNextContact = (TextView) findViewById(R.id.nextContact);
        updateTimingTextView(tvNextContact, nextContact);

        if (nextContact == null || nextContact == 0) {
            tvNextContact.setText("ASAP");
        } else {
            updateTimingTextView(tvNextContact, nextContact);
        }

        TextView tvClockIcon = (TextView) findViewById(R.id.nextDescriptionIcon);
        if (nextContact == null || nextContact == 0 || nextContact < System.currentTimeMillis()) {
            tvClockIcon.setTextColor(MainActivity.ALARM_ICON_COLOR);
        } else {
            tvClockIcon.setTextColor(MainActivity.LOW_PRIORITY_CLOCK_COLOR);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.entry, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_remove_contact) {
            deleteContact();
        }
        return super.onOptionsItemSelected(item);
    }

    public void viewSystemContact(View view) {
        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
        startActivity(intent);
    }

    public void deleteContact() {
        if (rowId != -1L) {
            mDbHelper.deleteContact(rowId);
        }
        AnalyticsUtil.logAction(this, "contacts", "entry-details-remove-contact");
        mMixpanel.track("Removed Contact", null);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDbHelper.close();
    }

    public void close(View view) {
        finish();
    }
}
