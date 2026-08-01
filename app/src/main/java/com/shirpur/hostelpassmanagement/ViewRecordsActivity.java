package com.shirpur.hostelpassmanagement;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.models.LeaveRequest;
import com.shirpur.hostelpassmanagement.models.OutPassRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViewRecordsActivity extends AppCompatActivity {

    private FirebaseHelper fbHelper;
    private ListView listView;
    
    private List<Object> allRecords = new ArrayList<>();
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_records);

        fbHelper = new FirebaseHelper();
        listView = findViewById(R.id.listViewRecords);

        adapter = new RecordAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadAllRecords();
    }

    private void loadAllRecords() {
        fbHelper.getAllLeaveRequests(querySnapshot -> {
            for(DocumentSnapshot doc : querySnapshot.getDocuments()){
                allRecords.add(doc.toObject(LeaveRequest.class));
            }
            
            fbHelper.getAllOutPassRequests(querySnapshot2 -> {
                for(DocumentSnapshot doc : querySnapshot2.getDocuments()){
                    allRecords.add(doc.toObject(OutPassRequest.class));
                }
                
                // Shuffle or sort if needed
                adapter.notifyDataSetChanged();
                
            }, e -> Toast.makeText(this, "E: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            
        }, e -> Toast.makeText(this, "E: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private class RecordAdapter extends BaseAdapter {
        @Override
        public int getCount() { return allRecords.size(); }
        @Override
        public Object getItem(int position) { return allRecords.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ViewRecordsActivity.this)
                        .inflate(R.layout.list_item_record, parent, false);
            }

            Object item = getItem(position);
            
            TextView tvRecordName = convertView.findViewById(R.id.tvRecordName);
            TextView tvRecordInfo = convertView.findViewById(R.id.tvRecordInfo);
            TextView tvRecordStatus = convertView.findViewById(R.id.tvRecordStatus);
            View statusBar = convertView.findViewById(R.id.statusBar);

            if (item instanceof LeaveRequest) {
                LeaveRequest lr = (LeaveRequest) item;
                tvRecordName.setText(lr.studentName);
                tvRecordInfo.setText("Leave Pass · " + lr.fromDate);
                tvRecordStatus.setText(lr.status);
                setStyle(tvRecordStatus, statusBar, lr.status);
                
            } else if (item instanceof OutPassRequest) {
                OutPassRequest opr = (OutPassRequest) item;
                tvRecordName.setText(opr.studentName);
                tvRecordInfo.setText("Out Pass · " + opr.date);
                tvRecordStatus.setText(opr.status);
                setStyle(tvRecordStatus, statusBar, opr.status);
            }

            return convertView;
        }

        private void setStyle(TextView tv, View bar, String status) {
            if ("APPROVED".equals(status) || "OUT".equals(status)) {
                tv.setTextColor(getResources().getColor(R.color.status_approved));
                bar.setBackgroundColor(getResources().getColor(R.color.status_approved));
            } else if ("REJECTED".equals(status)) {
                tv.setTextColor(getResources().getColor(R.color.status_rejected));
                bar.setBackgroundColor(getResources().getColor(R.color.status_rejected));
            } else {
                tv.setTextColor(getResources().getColor(R.color.status_pending));
                bar.setBackgroundColor(getResources().getColor(R.color.status_pending));
            }
        }
    }
}
