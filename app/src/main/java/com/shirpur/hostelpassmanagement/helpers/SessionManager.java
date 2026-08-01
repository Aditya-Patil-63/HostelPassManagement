package com.shirpur.hostelpassmanagement.helpers;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME         = "HostelPassSession";
    private static final String KEY_UID           = "uid";
    private static final String KEY_ROLE          = "role";
    private static final String KEY_NAME          = "name";
    private static final String KEY_YEAR          = "year";
    private static final String KEY_PHOTO_URL     = "photoUrl";
    private static final String KEY_PHONE         = "phone";

    private static final String KEY_PARENT_PHONE  = "parentPhone";
    private static final String KEY_EMPLOYEE_ID   = "employeeId";
    private static final String KEY_STUDENT_SAP_ID    = "studentSapId";
    private static final String KEY_ATTENDANCE_PERCENT = "attendancePercent";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String uid, String role, String name, String phone, String year,
                            String photoUrl, String parentPhone,
                            String employeeId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_UID,          uid          != null ? uid          : "");
        editor.putString(KEY_ROLE,         role         != null ? role         : "");
        editor.putString(KEY_NAME,         name         != null ? name         : "");
        editor.putString(KEY_PHONE,        phone        != null ? phone        : "");
        editor.putString(KEY_YEAR,         year         != null ? year         : "");
        editor.putString(KEY_PHOTO_URL,    photoUrl     != null ? photoUrl     : "");

        editor.putString(KEY_PARENT_PHONE, parentPhone  != null ? parentPhone  : "");
        editor.putString(KEY_EMPLOYEE_ID,  employeeId   != null ? employeeId   : "");
        editor.apply();
    }

    /** Saves the SAP-style student ID generated at registration. */
    public void saveStudentSapId(String sapId) {
        prefs.edit().putString(KEY_STUDENT_SAP_ID, sapId != null ? sapId : "").apply();
    }

    /** Saves the student's declared attendance percentage. */
    public void saveAttendancePercent(int percent) {
        prefs.edit().putInt(KEY_ATTENDANCE_PERCENT, percent).apply();
    }

    public String getUid()              { return prefs.getString(KEY_UID, null); }
    public String getRole()             { return prefs.getString(KEY_ROLE, null); }
    public String getName()             { return prefs.getString(KEY_NAME, ""); }
    public String getPhone()            { return prefs.getString(KEY_PHONE, ""); }
    public String getYear()             { return prefs.getString(KEY_YEAR, ""); }
    public String getPhotoUrl()         { return prefs.getString(KEY_PHOTO_URL, ""); }

    public String getParentPhone()      { return prefs.getString(KEY_PARENT_PHONE, ""); }
    public String getEmployeeId()       { return prefs.getString(KEY_EMPLOYEE_ID, ""); }
    public String getStudentSapId()     { return prefs.getString(KEY_STUDENT_SAP_ID, ""); }
    public int    getAttendancePercent(){ return prefs.getInt(KEY_ATTENDANCE_PERCENT, 0); }

    public boolean isLoggedIn() {
        return getUid() != null;
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
