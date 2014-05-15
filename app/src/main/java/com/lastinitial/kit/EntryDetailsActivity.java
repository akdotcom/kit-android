package com.lastinitial.kit;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.QuickContactBadge;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class EntryDetailsActivity extends ActionBarActivity {

    private ContactsDbAdapter mDbHelper;
    private LastContactUpdater mLastContactUpdater;
    private String lookupKey = null;
    private long rowId = -1L;

    private ArrayAdapter<CharSequence> mTypeAdapter = null;
    private ArrayAdapter<CharSequence> mTypePluralAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entry_details);

        AnalyticsUtil.logScreenImpression(this, "com.lastinitial.kit.EntryDetailsActivity");

        mTypeAdapter = ArrayAdapter.createFromResource(this,
                R.array.frequency_unit_array, R.layout.date_spinner_item);
        mTypePluralAdapter = ArrayAdapter.createFromResource(this,
                R.array.frequency_unit_array_plural, R.layout.date_spinner_item);
        // Specify the layout to use when the list of choices appears
        mTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTypePluralAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        TextView tvLastContactIcon = (TextView) findViewById(R.id.lastContactIcon);
        TextView tvFrequencyIcon = (TextView) findViewById(R.id.kitEveryIcon);
        TextView tvNextContactIcon = (TextView) findViewById(R.id.nextDescriptionIcon);
        Button bDoneIcon = (Button) findViewById(R.id.buttonDone);
        tvLastContactIcon.setTypeface(FontUtils.getFontAwesome(this));
        tvFrequencyIcon.setTypeface(FontUtils.getFontAwesome(this));
        tvNextContactIcon.setTypeface(FontUtils.getFontAwesome(this));
//        bDoneIcon.setTypeface(FontUtils.getFontAwesome(this));
        bDoneIcon.setText("Done");

        mDbHelper = new ContactsDbAdapter(this);
        mDbHelper.open();

        mLastContactUpdater = new LastContactUpdater();

        Intent intent = getIntent();
        rowId = intent.getLongExtra(MainActivity.DETAILS_DB_ROWID, -1L);

        Uri uri = intent.getData();

        long lastContacted = 0L;
        QuickContactBadge contactImage = (QuickContactBadge) findViewById(R.id.contactImage);

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

            Uri contactUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
            contactImage.assignContactUri(contactUri);

            String photoUriString =
                    cursor.getString(cursor.getColumnIndex(Contacts.PHOTO_URI));
            if (photoUriString != null) {
                contactImage.setImageURI(Uri.parse(photoUriString));
            } else {
                contactImage.setImageToDefault();
            }

            int lastContactedIndex = cursor.getColumnIndex(Contacts.LAST_TIME_CONTACTED);
            lastContacted = cursor.getLong(lastContactedIndex);

            if(rowId == -1L && mDbHelper.hasContact(lookupKey)) {
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
                    cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY)),
                    lastContacted,
                    MainActivity.CONTACT_TYPE_SYSTEM,
                    MainActivity.FREQUENCY_MONTHLY,
                    1);

            // Initialize last contacted time.
            mLastContactUpdater.updateContact(this, mDbHelper, rowId);
        }

        // We're done with the system contact information, so close the cursor.
        cursor.close();

        Resources resources = getResources();
        final String[] freqArray = resources.getStringArray(R.array.frequency);
        final int[] freqTypes = resources.getIntArray(R.array.frequencyTypes);
        final int[] freqScalars = resources.getIntArray(R.array.frequencyScalars);
        if (freqArray.length != freqTypes.length || freqArray.length != freqScalars.length) {
            new Exception("Frequency arrays are not all equal length."
                    + " frequency: " + freqArray.length
                    + " frequencyTypes: " + freqTypes.length
                    + " frequencyScalars: " + freqScalars.length);
        }

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
        final long lastContact = dbCursor.getLong(lastContactIndex);
        updateLastContactTextView(lastContact);

        final View lastContactInfo = findViewById(R.id.lastContact);
        lastContactInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogFragment newFragment = new DatePickerFragment(lastContactInfo);
                newFragment.show(getFragmentManager(), "datePicker");
            }
        });

        int nextContactIndex = dbCursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT);
        final long nextContact = dbCursor.getLong(nextContactIndex);
        updateNextContactTextView(nextContact);

        final View nextContactInfo = findViewById(R.id.nextContact);
        nextContactInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogFragment newFragment = new DatePickerFragment(nextContactInfo);
                newFragment.show(getFragmentManager(), "datePicker");
            }
        });

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
            c.close();
            updateNextContactTextView(c.getLong(nextContactIndex));
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

    public void updateLastContact(long lastContact, int contactType) {
        mDbHelper.updateLastContacted(rowId, lastContact, contactType);
        Cursor c = mDbHelper.fetchContact(rowId);
        long nextContact = c.getLong(c.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT));
        c.close();
        updateLastContactTextView(lastContact);
        updateNextContactTextView(nextContact);
    }

    public void updateNextContact(long nextContact) {
        mDbHelper.updateNextContact(rowId, nextContact);
        updateNextContactTextView(nextContact);
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {
        View mParentView;

        public DatePickerFragment(View textView) {
            mParentView = textView;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            Calendar c = Calendar.getInstance();
            Object tagTime = mParentView.getTag(R.id.view_time_millis);
            if (tagTime != null) {
                c.setTimeInMillis((Long)tagTime);
            }
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            // Do something with the date chosen by the user
            Log.v("DatePickerFragment", "Date picked");
            Calendar calendar = new GregorianCalendar(year, month, day);
            EntryDetailsActivity activity = (EntryDetailsActivity) getActivity();
            if (mParentView.getId() == R.id.lastContact) {
                activity.updateLastContact(
                        calendar.getTimeInMillis(), MainActivity.CONTACT_TYPE_MANUAL);
            } else if (mParentView.getId() == R.id.nextContact) {
                activity.updateNextContact(calendar.getTimeInMillis());
            }
            mParentView.setTag(R.id.view_time_millis, new Long(calendar.getTimeInMillis()));
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

    public void deleteContact() {
        if (rowId != -1L) {
            mDbHelper.deleteContact(rowId);
        }
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
