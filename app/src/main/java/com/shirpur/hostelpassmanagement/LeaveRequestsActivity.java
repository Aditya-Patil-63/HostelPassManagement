package com.shirpur.hostelpassmanagement;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


import com.google.firebase.firestore.ListenerRegistration;
import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.models.LeaveRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class LeaveRequestsActivity extends AppCompatActivity {

    private FirebaseHelper fbHelper;
    private ListView listView;
    private View emptyState;
    private TextView tvEmptyMessage;
    private TextView tabPending, tabHistory;
    
    private List<LeaveRequest> requestList = new ArrayList<>();
    private LeaveAdapter adapter;
    private boolean isHistoryTab = false;
    private ListenerRegistration pendingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_requests);

        fbHelper = new FirebaseHelper();

        listView = findViewById(R.id.listViewLeave);
        emptyState = findViewById(R.id.emptyState);

        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        
        tabPending = findViewById(R.id.tabPending);
        tabHistory = findViewById(R.id.tabHistory);

        adapter = new LeaveAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        tabPending.setOnClickListener(v -> switchTab(false));
        tabHistory.setOnClickListener(v -> switchTab(true));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            LeaveRequest req = requestList.get(position);
            Intent intent = new Intent(this, RequestDetailActivity.class);
            intent.putExtra("requestId", req.requestId);
            intent.putExtra("type", "LEAVE");
            startActivity(intent);
        });
    }

    private void switchTab(boolean history) {
        isHistoryTab = history;
        updateTabStyles();
        loadRequests();
    }

    private void updateTabStyles() {
        if (isHistoryTab) {
            tabHistory.setBackgroundResource(R.drawable.tab_selected);
            tabHistory.setTextColor(ContextCompat.getColor(this, R.color.white));
            tabPending.setBackgroundResource(R.drawable.tab_unselected);
            tabPending.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvEmptyMessage.setText("No history found");
        } else {
            tabPending.setBackgroundResource(R.drawable.tab_selected);
            tabPending.setTextColor(ContextCompat.getColor(this, R.color.white));
            tabHistory.setBackgroundResource(R.drawable.tab_unselected);
            tabHistory.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvEmptyMessage.setText("No pending leave requests");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRequests();
    }

    private void loadRequests() {
        if (pendingListener != null) {
            pendingListener.remove();
            pendingListener = null;
        }

        if (isHistoryTab) {
            fbHelper.getAllLeaveRequests(querySnapshot -> {
                List<LeaveRequest> all = querySnapshot.toObjects(LeaveRequest.class);
                List<LeaveRequest> history = new ArrayList<>();
                for (LeaveRequest r : all) {
                    if (!"PENDING".equals(r.status)) history.add(r);
                }
                displayList(history);
            }, e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            pendingListener = fbHelper.listenPendingLeaveRequests((querySnapshot, e) -> {
                if (e != null) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (querySnapshot != null) {
                    displayList(querySnapshot.toObjects(LeaveRequest.class));
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (pendingListener != null) {
            pendingListener.remove();
            pendingListener = null;
        }
    }

    private void displayList(List<LeaveRequest> list) {
        requestList.clear();
        requestList.addAll(list);
        
        // Always sort client-side by timestamp descending to ensure consistent order
        Collections.sort(requestList, (r1, r2) -> Long.compare(r2.timestamp, r1.timestamp));
        
        int count = requestList.size();

        
        emptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private class LeaveAdapter extends BaseAdapter {
        @Override
        public int getCount() { return requestList.size(); }
        @Override
        public LeaveRequest getItem(int position) { return requestList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(LeaveRequestsActivity.this)
                        .inflate(R.layout.list_item_request, parent, false);
            }

            LeaveRequest req = getItem(position);

            TextView tvItemStudentName = convertView.findViewById(R.id.tvItemStudentName);
            TextView tvItemSubInfo = convertView.findViewById(R.id.tvItemSubInfo);
            TextView tvItemType = convertView.findViewById(R.id.tvItemType);
            TextView tvItemStatus = convertView.findViewById(R.id.tvItemStatus);

            tvItemStudentName.setText(req.studentName);
            tvItemSubInfo.setText(req.reason + " · " + req.fromDate + " → " + req.toDate);
            
            if (isHistoryTab) {
                if (tvItemStatus != null) tvItemStatus.setVisibility(View.GONE);
                tvItemType.setText(req.status);
                if ("APPROVED".equals(req.status)) {
                    tvItemType.setTextColor(ContextCompat.getColor(LeaveRequestsActivity.this, R.color.status_approved));
                } else {
                    tvItemType.setTextColor(ContextCompat.getColor(LeaveRequestsActivity.this, R.color.status_rejected));
                }
            } else {
                if (tvItemStatus != null) tvItemStatus.setVisibility(View.VISIBLE);
                tvItemType.setText("LEAVE");
                tvItemType.setTextColor(ContextCompat.getColor(LeaveRequestsActivity.this, R.color.brand_primary));
            }

            return convertView;
        }
    }
}
