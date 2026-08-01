package com.shirpur.hostelpassmanagement.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.shirpur.hostelpassmanagement.models.Pass;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLiteHelper — local cache for approved passes (offline support).
 * Satisfies the "SQLite database" academic requirement.
 */
public class SQLiteHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "hostel_passes.db";
    private static final int    DB_VERSION = 1;

    // Table
    private static final String TABLE_PASSES = "passes";
    private static final String COL_PASS_ID     = "pass_id";
    private static final String COL_STUDENT_ID  = "student_id";
    private static final String COL_STUDENT_NAME = "student_name";
    private static final String COL_PHOTO_URL   = "photo_url";
    private static final String COL_TYPE        = "type";
    private static final String COL_STATUS      = "status";
    private static final String COL_VALIDITY    = "validity";
    private static final String COL_SYNCED      = "synced";

    private static final String CREATE_TABLE =
        "CREATE TABLE " + TABLE_PASSES + " (" +
        COL_PASS_ID     + " TEXT PRIMARY KEY, " +
        COL_STUDENT_ID  + " TEXT, " +
        COL_STUDENT_NAME + " TEXT, " +
        COL_PHOTO_URL   + " TEXT, " +
        COL_TYPE        + " TEXT, " +
        COL_STATUS      + " TEXT, " +
        COL_VALIDITY    + " TEXT, " +
        COL_SYNCED      + " INTEGER DEFAULT 0" +
        ")";

    public SQLiteHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PASSES);
        onCreate(db);
    }

    /** Insert or replace a pass in local cache */
    public void upsertPass(Pass pass) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PASS_ID,      pass.passId);
        values.put(COL_STUDENT_ID,   pass.studentId);
        values.put(COL_STUDENT_NAME, pass.studentName);
        values.put(COL_PHOTO_URL,    pass.studentPhotoUrl);
        values.put(COL_TYPE,         pass.type);
        values.put(COL_STATUS,       pass.status);
        values.put(COL_VALIDITY,     pass.validity);
        values.put(COL_SYNCED,       1);
        db.insertWithOnConflict(TABLE_PASSES, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    /** Fetch pass by passId for offline verify */
    public Pass getPassById(String passId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PASSES, null,
                COL_PASS_ID + "=?", new String[]{passId},
                null, null, null);
        Pass pass = null;
        if (cursor.moveToFirst()) {
            pass = cursorToPass(cursor);
        }
        cursor.close();
        db.close();
        return pass;
    }

    /** Fetch all cached passes for a student */
    public List<Pass> getPassesByStudentId(String studentId) {
        SQLiteDatabase db = getReadableDatabase();
        List<Pass> list = new ArrayList<>();
        Cursor cursor = db.query(TABLE_PASSES, null,
                COL_STUDENT_ID + "=?", new String[]{studentId},
                null, null, COL_PASS_ID + " DESC");
        while (cursor.moveToNext()) {
            list.add(cursorToPass(cursor));
        }
        cursor.close();
        db.close();
        return list;
    }

    /** Update pass status (e.g., APPROVED → OUT) */
    public void updatePassStatus(String passId, String newStatus) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_STATUS, newStatus);
        db.update(TABLE_PASSES, values, COL_PASS_ID + "=?", new String[]{passId});
        db.close();
    }

    private Pass cursorToPass(Cursor cursor) {
        Pass pass = new Pass();
        pass.passId          = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASS_ID));
        pass.studentId       = cursor.getString(cursor.getColumnIndexOrThrow(COL_STUDENT_ID));
        pass.studentName     = cursor.getString(cursor.getColumnIndexOrThrow(COL_STUDENT_NAME));
        pass.studentPhotoUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_PHOTO_URL));
        pass.type            = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE));
        pass.status          = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS));
        pass.validity        = cursor.getString(cursor.getColumnIndexOrThrow(COL_VALIDITY));
        return pass;
    }
}
