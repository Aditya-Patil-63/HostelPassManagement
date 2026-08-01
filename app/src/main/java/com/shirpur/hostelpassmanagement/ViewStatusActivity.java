package com.shirpur.hostelpassmanagement;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import androidx.appcompat.app.AppCompatActivity;

import com.shirpur.hostelpassmanagement.helpers.FirebaseHelper;
import com.shirpur.hostelpassmanagement.helpers.SessionManager;
import com.shirpur.hostelpassmanagement.models.LeaveRequest;
import com.shirpur.hostelpassmanagement.models.OutPassRequest;

import java.util.ArrayList;
import java.util.List;

public class ViewStatusActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseHelper fbHelper;
    private ListView listView;
    private View emptyState;
    
    private TextView tabAll, tabLeave, tabOutPass;

    private List<Object> allRequests = new ArrayList<>();
    private List<Object> displayedRequests = new ArrayList<>();
    private RequestAdapter adapter;

    private enum Filter { ALL, LEAVE, OUTPASS }
    private Filter currentFilter = Filter.ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_status);

        sessionManager = new SessionManager(this);
        fbHelper = new FirebaseHelper();

        listView = findViewById(R.id.listViewRequests);
        emptyState = findViewById(R.id.emptyState);

        initTabs();
        
        adapter = new RequestAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadMyRequests();
    }

    private void initTabs() {
        tabAll = findViewById(R.id.tabAll);
        tabLeave = findViewById(R.id.tabLeave);
        tabOutPass = findViewById(R.id.tabOutPass);

        tabAll.setOnClickListener(v -> setFilter(Filter.ALL));
        tabLeave.setOnClickListener(v -> setFilter(Filter.LEAVE));
        tabOutPass.setOnClickListener(v -> setFilter(Filter.OUTPASS));
    }

    private void setFilter(Filter filter) {
        currentFilter = filter;
        
        // Update tab styles
        tabAll.setBackgroundColor(getResources().getColor(R.color.transparent));
        tabLeave.setBackgroundColor(getResources().getColor(R.color.transparent));
        tabOutPass.setBackgroundColor(getResources().getColor(R.color.transparent));
        
        tabAll.setTextColor(getResources().getColor(R.color.text_secondary));
        tabLeave.setTextColor(getResources().getColor(R.color.text_secondary));
        tabOutPass.setTextColor(getResources().getColor(R.color.text_secondary));

        TextView activeTab = tabAll;
        if (filter == Filter.LEAVE) activeTab = tabLeave;
        if (filter == Filter.OUTPASS) activeTab = tabOutPass;

        activeTab.setBackgroundResource(R.drawable.btn_primary);
        activeTab.setTextColor(getResources().getColor(R.color.white));

        applyFilter();
    }

    private void applyFilter() {
        displayedRequests.clear();
        for (Object req : allRequests) {
            if (currentFilter == Filter.ALL) {
                displayedRequests.add(req);
            } else if (currentFilter == Filter.LEAVE && req instanceof LeaveRequest) {
                displayedRequests.add(req);
            } else if (currentFilter == Filter.OUTPASS && req instanceof OutPassRequest) {
                displayedRequests.add(req);
            }
        }
        
        emptyState.setVisibility(displayedRequests.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void loadMyRequests() {
        String uid = sessionManager.getUid();
        
        fbHelper.getLeaveRequestsByStudent(uid, querySnapshot -> {
            List<LeaveRequest> leaves = querySnapshot.toObjects(LeaveRequest.class);
            allRequests.addAll(leaves);
            
            fbHelper.getOutPassRequestsByStudent(uid, querySnapshot2 -> {
                List<OutPassRequest> outPasses = querySnapshot2.toObjects(OutPassRequest.class);
                allRequests.addAll(outPasses);
                
                // Sort by timestamp if needed, but Firebase handles it mostly. Here we just append.
                applyFilter();
            }, e -> Toast.makeText(this, "E: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            
        }, e -> Toast.makeText(this, "E: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // Custom BaseAdapter to handle both Object types
    private class RequestAdapter extends BaseAdapter {

        @Override
        public int getCount() { return displayedRequests.size(); }

        @Override
        public Object getItem(int position) { return displayedRequests.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ViewStatusActivity.this)
                        .inflate(R.layout.list_item_pass, parent, false);
            }

            TextView tvPassType = convertView.findViewById(R.id.tvPassType);
            TextView tvPassStudentName = convertView.findViewById(R.id.tvPassStudentName);
            TextView tvPassValidity = convertView.findViewById(R.id.tvPassValidity);
            TextView tvPassStatus = convertView.findViewById(R.id.tvPassStatus);
            TextView tvRejectionReason = convertView.findViewById(R.id.tvRejectionReason);
            ImageView ivQrCode = convertView.findViewById(R.id.ivQrCode);

            Object item = getItem(position);

            if (item instanceof LeaveRequest) {
                LeaveRequest lr = (LeaveRequest) item;
                tvPassType.setText("LEAVE PASS");
                tvPassStudentName.setText(lr.reason);
                tvPassValidity.setText("Valid: " + lr.fromDate + " to " + lr.toDate);
                setStatusStyle(tvPassStatus, lr.status);

                if ("REJECTED".equals(lr.status) && lr.rejectionReason != null) {
                    tvRejectionReason.setVisibility(View.VISIBLE);
                    tvRejectionReason.setText("Reason: " + lr.rejectionReason);
                } else {
                    tvRejectionReason.setVisibility(View.GONE);
                }
                
                if ("APPROVED".equals(lr.status)) {
                    ivQrCode.setVisibility(View.VISIBLE);
                    try {
                        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                        Bitmap bitmap = barcodeEncoder.encodeBitmap(lr.studentId, BarcodeFormat.QR_CODE, 400, 400);
                        ivQrCode.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    ivQrCode.setVisibility(View.GONE);
                }
            } else if (item instanceof OutPassRequest) {
                OutPassRequest opr = (OutPassRequest) item;
                tvPassType.setText("OUT PASS");
                tvPassType.setTextColor(getResources().getColor(R.color.color_student));
                tvPassStudentName.setText(opr.reason);
                tvPassValidity.setText("Time: " + opr.timeOut + " to " + opr.timeIn);
                setStatusStyle(tvPassStatus, opr.status);

                if ("REJECTED".equals(opr.status) && opr.rejectionReason != null) {
                    tvRejectionReason.setVisibility(View.VISIBLE);
                    tvRejectionReason.setText("Reason: " + opr.rejectionReason);
                } else {
                    tvRejectionReason.setVisibility(View.GONE);
                }
                
                if ("APPROVED".equals(opr.status)) {
                    ivQrCode.setVisibility(View.VISIBLE);
                    try {
                        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                        Bitmap bitmap = barcodeEncoder.encodeBitmap(opr.studentId, BarcodeFormat.QR_CODE, 400, 400);
                        ivQrCode.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    ivQrCode.setVisibility(View.GONE);
                }
            }

            return convertView;
        }
        
        private void setStatusStyle(TextView tv, String status) {
            tv.setText(status);
            if ("APPROVED".equals(status)) {
                tv.setTextColor(getResources().getColor(R.color.status_approved));
            } else if ("REJECTED".equals(status)) {
                tv.setTextColor(getResources().getColor(R.color.status_rejected));
            } else {
                tv.setTextColor(getResources().getColor(R.color.status_pending));
            }
        }
    }
}
