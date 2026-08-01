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
import com.shirpur.hostelpassmanagement.models.OutPassRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class OutPassRequestsActivity extends AppCompatActivity {

    private FirebaseHelper fbHelper;
    private ListView listView;
    private View emptyState;
    private TextView tvEmptyMessage;
    private TextView tabPending, tabHistory;
    
    private List<OutPassRequest> requestList = new ArrayList<>();
    private OutPassAdapter adapter;
    private boolean isHistoryTab = false;
    private ListenerRegistration pendingListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outpass_requests);

        fbHelper = new FirebaseHelper();

        listView = findViewById(R.id.listViewOutPass);
        emptyState = findViewById(R.id.emptyState);

        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        
        tabPending = findViewById(R.id.tabPending);
        tabHistory = findViewById(R.id.tabHistory);

        adapter = new OutPassAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        tabPending.setOnClickListener(v -> switchTab(false));
        tabHistory.setOnClickListener(v -> switchTab(true));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            OutPassRequest req = requestList.get(position);
            Intent intent = new Intent(this, RequestDetailActivity.class);
            intent.putExtra("requestId", req.requestId);
            intent.putExtra("type", "OUTPASS");
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
            tvEmptyMessage.setText("No pending outing requests");
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
            fbHelper.getAllOutPassRequests(querySnapshot -> {
                List<OutPassRequest> all = querySnapshot.toObjects(OutPassRequest.class);
                List<OutPassRequest> history = new ArrayList<>();
                for (OutPassRequest r : all) {
                    if (!"PENDING".equals(r.status)) history.add(r);
                }
                displayList(history);
            }, e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            pendingListener = fbHelper.listenPendingOutPassRequests((querySnapshot, e) -> {
                if (e != null) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (querySnapshot != null) {
                    displayList(querySnapshot.toObjects(OutPassRequest.class));
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

    private void displayList(List<OutPassRequest> list) {
        requestList.clear();
        requestList.addAll(list);
        
        // Always sort client-side by timestamp descending to ensure consistent order
        Collections.sort(requestList, (r1, r2) -> Long.compare(r2.timestamp, r1.timestamp));
        
        int count = requestList.size();

        
        emptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private class OutPassAdapter extends BaseAdapter {
        @Override
        public int getCount() { return requestList.size(); }
        @Override
        public OutPassRequest getItem(int position) { return requestList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(OutPassRequestsActivity.this)
                        .inflate(R.layout.list_item_request, parent, false);
            }

            OutPassRequest req = getItem(position);

            TextView tvItemStudentName = convertView.findViewById(R.id.tvItemStudentName);
            TextView tvItemSubInfo = convertView.findViewById(R.id.tvItemSubInfo);
            TextView tvItemType = convertView.findViewById(R.id.tvItemType);
            TextView tvItemStatus = convertView.findViewById(R.id.tvItemStatus);

            tvItemStudentName.setText(req.studentName);
            tvItemSubInfo.setText(req.reason + " · " + req.date + " " + req.timeOut + " → " + req.timeIn);
            
            if (isHistoryTab) {
                if (tvItemStatus != null) tvItemStatus.setVisibility(View.GONE);
                tvItemType.setText(req.status);
                if ("APPROVED".equals(req.status)) {
                    tvItemType.setTextColor(ContextCompat.getColor(OutPassRequestsActivity.this, R.color.status_approved));
                } else {
                    tvItemType.setTextColor(ContextCompat.getColor(OutPassRequestsActivity.this, R.color.status_rejected));
                }
            } else {
                if (tvItemStatus != null) tvItemStatus.setVisibility(View.VISIBLE);
                tvItemType.setText("OUT PASS");
                tvItemType.setTextColor(ContextCompat.getColor(OutPassRequestsActivity.this, R.color.brand_primary));
            }

            return convertView;
        }
    }
}
