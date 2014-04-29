package com.lastinitial.kit;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.QuickContactBadge;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class EntryDetailsActivity extends ActionBarActivity implements AdapterView.OnItemSelectedListener {

    private ContactsDbAdapter mDbHelper;
    private String lookupKey = null;
    private long rowId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entry_details);

        Tracker t = ((KitApplication) getApplication()).getTracker();
        t.setScreenName("com.lastinitial.kit.EntryDetailsActivity");
        // Send a screen view.
        t.send(new HitBuilders.AppViewBuilder().build());

        TextView tvLastContactIcon = (TextView) findViewById(R.id.lastDescription);
        TextView tvFrequencyIcon = (TextView) findViewById(R.id.kitEvery);
        TextView tvNextContactIcon = (TextView) findViewById(R.id.nextDescription);
        Button bDoneIcon = (Button) findViewById(R.id.button);
        tvLastContactIcon.setTypeface(FontUtils.getFontAwesome(this));
        tvFrequencyIcon.setTypeface(FontUtils.getFontAwesome(this));
        tvNextContactIcon.setTypeface(FontUtils.getFontAwesome(this));
        bDoneIcon.setTypeface(FontUtils.getFontAwesome(this));

        mDbHelper = new ContactsDbAdapter(this);
        mDbHelper.open();

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
//                contactImage.setImageResource(R.drawable.ic_action_person);
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
                    MainActivity.CONTACT_SYSTEM,
                    MainActivity.FREQUENCY_MONTHLY,
                    1);
        }

        // We're done with the system contact information, so close the cursor.
        cursor.close();


        Cursor dbCursor = mDbHelper.fetchContact(rowId);
        int freqType = dbCursor.getInt(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_FREQUENCY_TYPE));
        int freqScalar = dbCursor.getInt(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_FREQUENCY_SCALAR));

        Spinner fScalarSpinner = (Spinner) findViewById(R.id.fScalarSpinner);
        ArrayAdapter<CharSequence> scalarAdapter = ArrayAdapter.createFromResource(this,
                R.array.number_array, R.layout.date_spinner_item);
        // Specify the layout to use when the list of choices appears
        scalarAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        fScalarSpinner.setAdapter(scalarAdapter);
        fScalarSpinner.setSelection(freqScalar - 1); // Index is one less than value, since 0-indexed
        fScalarSpinner.setOnItemSelectedListener(this);

        Spinner fTypeSpinner = (Spinner) findViewById(R.id.fTypeSpinner);
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(this,
                R.array.frequency_unit_array, R.layout.date_spinner_item);
        // Specify the layout to use when the list of choices appears
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        fTypeSpinner.setAdapter(typeAdapter);
        fTypeSpinner.setSelection(freqType);
        fTypeSpinner.setOnItemSelectedListener(this);

//        SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar);
//        final String[] freqArray = getResources().getStringArray(R.array.frequency);
//        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
//                Log.v("SeekBar", i + " " + b);
//                // TODO(ak) this doesn't quite work. actually think about it for a bit
//                int window = (int) Math.ceil(100.0 / (freqArray.length - 1));
//                setTitle(freqArray[i/window]);
//            }
//
//            @Override
//            public void onStartTrackingTouch(SeekBar seekBar) {
//                Log.v("SeekBar", "onStartTrackingTouch");
//            }
//
//            @Override
//            public void onStopTrackingTouch(SeekBar seekBar) {
//                Log.v("SeekBar", "onStopTrackingTouch");
//            }
//        });


        final long lastContact = dbCursor.getLong(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_LAST_CONTACTED));
        updateLastContactTextView(lastContact);

        final View lastContactInfo = findViewById(R.id.lastContact);
        lastContactInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogFragment newFragment = new DatePickerFragment(lastContactInfo, lastContact);
                newFragment.show(getFragmentManager(), "datePicker");
            }
        });

        final long nextContact = dbCursor.getLong(dbCursor.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT));
        updateNextContactTextView(nextContact);

        final View nextContactInfo = findViewById(R.id.nextContact);
        nextContactInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DialogFragment newFragment = new DatePickerFragment(nextContactInfo, nextContact);
                newFragment.show(getFragmentManager(), "datePicker");
            }
        });

        dbCursor.close();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        LastContactUpdater updater = new LastContactUpdater();
        Long updatedTime = updater.updateContact(this, mDbHelper, rowId);
        if (updatedTime != null) {
            updateLastContactTextView(updatedTime);
        }
    }

    public void updateTimingTextView(TextView textView, Long newTime) {
        if (newTime == null || newTime == 0) {
            textView.setText(R.string.unknown_time);
            return;
        }
        CharSequence lastContactString = DateUtils.getRelativeTimeSpanString(
                newTime,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS);
        textView.setText(lastContactString);
    }

    public void updateLastContactTextView(Long lastContact) {
        TextView tvLastContact = (TextView) findViewById(R.id.lastContact);
        updateTimingTextView(tvLastContact, lastContact);
    }

    public void updateNextContactTextView(Long nextContact) {
        TextView tvLastContact = (TextView) findViewById(R.id.nextContact);
        updateTimingTextView(tvLastContact, nextContact);
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
        long mInitialTime;
        View mParentView;

        public DatePickerFragment(View textView, long initialTime) {
            mParentView = textView;
            mInitialTime = initialTime;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            Calendar c = Calendar.getInstance();
            if (mInitialTime != 0) {
                c.setTimeInMillis(mInitialTime);
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
        }
    }



    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        // Update to the frequency type
        if (R.id.fTypeSpinner == adapterView.getId()) {
            // Get the complimentary value
            Spinner freqScalar = (Spinner) findViewById(R.id.fScalarSpinner);
            // Type constant values correspond to their position in the selector. Scalar values
            // are off by one.
            mDbHelper.updateContactFrequency(rowId, i, freqScalar.getSelectedItemPosition() + 1);
        }

        if (R.id.fScalarSpinner == adapterView.getId()) {
            // Get the complimentary value
            Spinner freqType = (Spinner) findViewById(R.id.fTypeSpinner);
            // Type constant values correspond to their position in the selector. Scalar values
            // are off by one.
            mDbHelper.updateContactFrequency(rowId, freqType.getSelectedItemPosition(), i + 1);
        }
        Cursor c = mDbHelper.fetchContact(rowId);
        long nextContact = c.getLong(c.getColumnIndex(ContactsDbAdapter.KEY_NEXT_CONTACT));
        updateNextContactTextView(nextContact);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        Log.e("EntryDetailsActivity", "onNothingSelected called on " + adapterView);
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
