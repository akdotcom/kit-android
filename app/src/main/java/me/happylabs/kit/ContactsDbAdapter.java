package me.happylabs.kit;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by ak on 3/27/14.
 */
public class ContactsDbAdapter {
    public static final String KEY_ROWID = "_id";
    public static final String KEY_LOOKUP_KEY = "lookup_key";
    public static final String KEY_LAST_CONTACTED = "last_contacted";
    public static final String KEY_LAST_CONTACT_TYPE = "last_contact_type";
    public static final String KEY_FREQUENCY_TYPE = "frequency_type";
    public static final String KEY_FREQUENCY_SCALAR = "frequency_scalar";
    public static final String KEY_NEXT_CONTACT = "next_contact";

    private static final String TAG = "ContactsDbAdapter";

    private static final String DATABASE_NAME = "data";
    private static final String DATABASE_TABLE = "contacts";
    private static final int DATABASE_VERSION = 1;

    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;
    private final Context mCtx;

    private static final long HOURLY_MILLIS  = 3600000L;
    private static final long DAILY_MILLIS   = 86400000L;
    private static final long WEEKLY_MILLIS  = 604800000L;
    private static final long MONTHLY_MILLIS = 2592000000L; // 30 days

    /**
     * Database creation sql statement
     */
    private static final String DATABASE_CREATE =
            "create table contacts ("
                    + "_id integer primary key autoincrement, "
                    + "lookup_key text not null ,"
                    + "last_contacted integer, "
                    + "last_contact_type integer, "
                    + "frequency_type integer not null, "
                    + "frequency_scalar integer not null, "
                    + "next_contact integer"
                    + ");";


    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS notes");
            onCreate(db);
        }
    }


    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     *
     * @param ctx the Context within which to work
     */
    public ContactsDbAdapter(Context ctx) {
        this.mCtx = ctx;
    }

    /**
     * Open the contacts database. If it cannot be opened, try to create a new
     * instance of the database. If it cannot be created, throw an exception to
     * signal the failure
     *
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    public ContactsDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        mDbHelper.close();
    }


    /**
     * Create a new contact using the fields provided. If the contact is
     * successfully created return the new rowId for that contact, otherwise return
     * a -1 to indicate failure.
     *
     * @return rowId or -1 if failed
     */
    public long createContact(String lookupKey,
                              Long lastContacted,
                              Integer lastContactType,
                              Integer frequencyType,
                              Integer frequencyScalar) {

        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_LOOKUP_KEY, lookupKey);
        initialValues.put(KEY_LAST_CONTACTED, lastContacted);
        initialValues.put(KEY_LAST_CONTACT_TYPE, lastContactType);
        initialValues.put(KEY_FREQUENCY_TYPE, frequencyType);
        initialValues.put(KEY_FREQUENCY_SCALAR, frequencyScalar);
        long nextContact = calculateNextContact(lastContacted, frequencyType, frequencyScalar);
        initialValues.put(KEY_NEXT_CONTACT, nextContact);

        return mDb.insert(DATABASE_TABLE, null, initialValues);
    }

    /**
     * Calculate when the next contact should be given the previous timing and desired frequency.
     *
     * TODO(ak): actually implement
     * @param lastContacted
     * @param frequencyType
     * @param frequencyScalar
     * @return
     */
    public long calculateNextContact(Long lastContacted, int frequencyType, int frequencyScalar) {
        if (lastContacted == null || lastContacted == 0L) {
            return 0L;
        }
        long unitMillis = 0;
        switch(frequencyType) {
            case(MainActivity.FREQUENCY_HOURLY):
                unitMillis = HOURLY_MILLIS;
                break;
            case(MainActivity.FREQUENCY_DAILY):
                unitMillis = DAILY_MILLIS;
                break;
            case(MainActivity.FREQUENCY_WEEKLY):
                unitMillis = WEEKLY_MILLIS;
                break;
            case(MainActivity.FREQUENCY_MONTHLY):
                unitMillis = MONTHLY_MILLIS;
                break;
            default:
                Log.e("calculateNextContact", "Unknown frequencyType: " + frequencyType);
        }

        return lastContacted + unitMillis * ((long) frequencyScalar);
    }

    /**
     * Delete the contact with the given rowId
     *
     * @param rowId id of contact to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteContact(long rowId) {
        return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
    }

    /**
     * Return a Cursor over the list of all contacts in the database, ordered by when they should
     * next be contacted (in ascending order)
     *
     * @return Cursor over all contacts
     */
    public Cursor fetchAllContacts() {
        return mDb.query(DATABASE_TABLE, null, null, null, null, null, KEY_NEXT_CONTACT + " ASC");
    }

    /**
     * Return a Cursor positioned at the contact that matches the given rowId
     *
     * @param rowId id of note to retrieve
     * @return Cursor positioned to matching contact, if found. Null if not found.
     * @throws SQLException if contact could not be found/retrieved
     */
    public Cursor fetchContact(long rowId) throws SQLException {

        Cursor mCursor =

                mDb.query(true, DATABASE_TABLE, null, KEY_ROWID + "=" + rowId, null,
                        null, null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

    /**
     * Return a Cursor positioned at the contact that matches the given rowId
     *
     * @param lookupKey a lookupKey of contact to retrieve
     * @return Cursor positioned to matching contact, if found. Null if not found.
     * @throws SQLException if contact could not be found/retrieved
     */
    public Cursor fetchContact(String lookupKey) throws SQLException {

        Cursor mCursor =
                mDb.query(true, DATABASE_TABLE, null, KEY_LOOKUP_KEY + "=?", new String[] { lookupKey},
                        null, null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }

    /**
     * Update the contact using the details provided. The note to be updated is
     * specified using the rowId, and it is altered to use the
     * values passed in
     *
     * @param rowId id of contact to update
     * @return true if the contact was successfully updated, false otherwise
     */
    public boolean updateContact(long rowId,
                                 String lookupKey,
                                 Long lastContacted) {
        ContentValues args = new ContentValues();
        args.put(KEY_LOOKUP_KEY, lookupKey);
        args.put(KEY_LAST_CONTACTED, lastContacted);

        Cursor cursor = fetchContact(rowId);
        int freqType = cursor.getInt(cursor.getColumnIndex(KEY_FREQUENCY_TYPE));
        int freqScalar = cursor.getInt(cursor.getColumnIndex(KEY_FREQUENCY_SCALAR));
        long nextContact = calculateNextContact(lastContacted, freqType, freqScalar);

        args.put(KEY_NEXT_CONTACT, nextContact);

        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
    }

    public boolean updateLastContacted(long rowId, Long lastContacted) {
        ContentValues args = new ContentValues();
        args.put(KEY_LAST_CONTACTED, lastContacted);

        Cursor cursor = fetchContact(rowId);
        int freqType = cursor.getInt(cursor.getColumnIndex(KEY_FREQUENCY_TYPE));
        int freqScalar = cursor.getInt(cursor.getColumnIndex(KEY_FREQUENCY_SCALAR));
        long nextContact = calculateNextContact(lastContacted, freqType, freqScalar);

        args.put(KEY_NEXT_CONTACT, nextContact);

        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;

    }

    public boolean updateContactFrequency(long rowId, int frequencyType, int frequencyScalar) {
        ContentValues args = new ContentValues();
        args.put(KEY_FREQUENCY_TYPE, frequencyType);
        args.put(KEY_FREQUENCY_SCALAR, frequencyScalar);

        Cursor cursor = fetchContact(rowId);
        int lastContactedIndex = cursor.getColumnIndex(KEY_LAST_CONTACTED);
        long lastContacted = cursor.getLong(lastContactedIndex);
        long nextContact = calculateNextContact(lastContacted, frequencyType, frequencyScalar);
        args.put(KEY_NEXT_CONTACT, nextContact);

        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
    }

    public boolean hasContact(String lookupKey) {
        Cursor cursor = mDb.rawQuery(
                "select 1 from " + DATABASE_TABLE + " where " + KEY_LOOKUP_KEY + "=?",
                new String[] { lookupKey });
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        return exists;
    }
}
