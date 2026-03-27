package com.example.sportsbill;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class PreviousBillsActivity extends AppCompatActivity {

    private RecyclerView rvPreviousBills;
    private TextInputEditText etSearch;
    private MaterialButton btnFilterDate;
    private ExtendedFloatingActionButton btnSourceToggle;
    private Chip chipSelectedDate;
    private TextView tvDailyTotal;
    private ProgressBar progressBar;

    private List<BillDisplayModel> allBillsList = new ArrayList<>();
    private List<BillDisplayModel> filteredList = new ArrayList<>();
    private BillHistoryAdapter adapter;

    private String selectedDateStr = "";
    private boolean isCloudSource = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_previous_bills);

        rvPreviousBills = findViewById(R.id.rv_previous_bills);
        etSearch = findViewById(R.id.et_search_customer);
        btnFilterDate = findViewById(R.id.btn_filter_date);
        btnSourceToggle = findViewById(R.id.btn_source_toggle);
        chipSelectedDate = findViewById(R.id.chip_selected_date);
        tvDailyTotal = findViewById(R.id.tv_daily_total);
        progressBar = findViewById(R.id.progress_bar);

        rvPreviousBills.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BillHistoryAdapter(filteredList);
        rvPreviousBills.setAdapter(adapter);

        loadBills();

        btnSourceToggle.setOnClickListener(v -> {
            isCloudSource = !isCloudSource;

            allBillsList.clear();
            filteredList.clear();
            adapter.notifyDataSetChanged();

            if (isCloudSource) {
                btnSourceToggle.setText("Server Cloud");
                btnSourceToggle.setIconResource(android.R.drawable.stat_sys_upload_done);
                btnSourceToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3F51B5")));
            } else {
                btnSourceToggle.setText("Local Storage");
                btnSourceToggle.setIconResource(android.R.drawable.ic_menu_save);
                btnSourceToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF5722")));
            }

            etSearch.setText("");
            selectedDateStr = "";
            chipSelectedDate.setVisibility(View.GONE);

            loadBills();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });

        btnFilterDate.setOnClickListener(v -> showDatePicker());

        chipSelectedDate.setOnCloseIconClickListener(v -> {
            selectedDateStr = "";
            chipSelectedDate.setVisibility(View.GONE);
            filterList(etSearch.getText().toString());
        });
    }

    private void loadBills() {
        if (isCloudSource) loadBillsFromFirestore();
        else loadBillsFromStorage();
    }

    private void loadBillsFromStorage() {
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        allBillsList.clear();

        File billsDir = getExternalFilesDir("Bills");

        if (billsDir != null && billsDir.exists()) {
            File[] files = billsDir.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".pdf") && file.getName().startsWith("Bill_")) {
                        allBillsList.add(new BillDisplayModel(file));
                    }
                }
            }
        }

        filterList("");
    }

    private void loadBillsFromFirestore() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("bills")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allBillsList.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        BillData data = doc.toObject(BillData.class);
                        if (data != null) {
                            allBillsList.add(new BillDisplayModel(data));
                        }
                    }

                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    filterList("");
                })
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Cloud Error", Toast.LENGTH_SHORT).show();
                });
    }

    private void filterList(String query) {
        filteredList.clear();
        double totalRevenue = 0.0;

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        for (BillDisplayModel bill : allBillsList) {

            String name = bill.getCustomerName() != null ? bill.getCustomerName() : "";

            boolean matchesName = name.toLowerCase().contains(query.toLowerCase().trim());

            String fileDate = (bill.getTimestamp() != null)
                    ? sdf.format(bill.getTimestamp())
                    : "N/A";

            boolean matchesDate = selectedDateStr.isEmpty() || fileDate.equals(selectedDateStr);

            if (matchesName && matchesDate) {
                filteredList.add(bill);
                totalRevenue += bill.getAmount();
            }
        }

        tvDailyTotal.setText(String.format(Locale.getDefault(), "Total Sales: ₹ %.2f", totalRevenue));
        adapter.notifyDataSetChanged();
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        picker.show(getSupportFragmentManager(), "DATE");

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(selection);

            selectedDateStr = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(cal.getTime());

            chipSelectedDate.setText(selectedDateStr);
            chipSelectedDate.setVisibility(View.VISIBLE);

            filterList(etSearch.getText().toString());
        });
    }

    // ================= MODEL =================

    // ================= MODEL =================

    public static class BillDisplayModel {
        private String customerName;
        private double amount;
        private Date timestamp;
        private File file;
        private boolean isCloud;

        public BillDisplayModel(File file) {
            this.file = file;
            this.isCloud = false;

            try {
                // Filename Format: Bill_Name_Amount_Timestamp.pdf
                String fileName = file.getName().replace(".pdf", "");
                String[] parts = fileName.split("_");

                if (parts.length >= 4) {
                    // ✅ Extract timestamp (last part)
                    try {
                        long time = Long.parseLong(parts[parts.length - 1]);
                        this.timestamp = new Date(time);
                    } catch (Exception e) {
                        this.timestamp = new Date(file.lastModified());
                    }

                    // ✅ Extract amount (second last part - prevents showing timestamp as amount)
                    try {
                        this.amount = Double.parseDouble(parts[parts.length - 2]);
                    } catch (Exception e) {
                        this.amount = 0.0;
                    }

                    // ✅ Extract name (handles names with spaces/underscores)
                    StringBuilder nameBuilder = new StringBuilder();
                    for (int i = 1; i < parts.length - 2; i++) {
                        nameBuilder.append(parts[i]);
                        if (i < parts.length - 3) nameBuilder.append(" ");
                    }

                    this.customerName = nameBuilder.length() > 0
                            ? nameBuilder.toString()
                            : "Unknown Customer";

                } else if (parts.length == 3) {
                    // Fallback for older format: Bill_Name_Amount
                    this.customerName = parts[1];
                    try {
                        this.amount = Double.parseDouble(parts[2]);
                    } catch (Exception e) {
                        this.amount = 0.0;
                    }
                    this.timestamp = new Date(file.lastModified());
                } else {
                    fallback(file);
                }

            } catch (Exception e) {
                fallback(file);
            }
        }

        private void fallback(File file) {
            this.customerName = "Local Bill";
            this.amount = 0.0;
            this.timestamp = new Date(file.lastModified());
        }

        public BillDisplayModel(BillData data) {
            this.customerName = data.customerName != null ? data.customerName : "Unknown";
            this.amount = data.amount;
            this.timestamp = data.timestamp;
            this.isCloud = true;
        }

        public String getCustomerName() { return customerName; }
        public double getAmount() { return amount; }
        public Date getTimestamp() { return timestamp; }
        public File getFile() { return file; }
        public boolean isCloud() { return isCloud; }
    }

    public static class BillData implements Serializable {
        public String customerName;
        public double amount;
        public Date timestamp;
        public BillData() {}
    }

    // ================= ADAPTER =================

    private class BillHistoryAdapter extends RecyclerView.Adapter<BillHistoryAdapter.ViewHolder> {

        private final List<BillDisplayModel> list;

        public BillHistoryAdapter(List<BillDisplayModel> list) {
            this.list = list;
        }

        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_previous_bill, parent, false);
            return new ViewHolder(v);
        }

        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BillDisplayModel bill = list.get(position);

            holder.tvName.setText(bill.getCustomerName());
            holder.tvAmount.setText(String.format(Locale.getDefault(), "₹ %.2f", bill.getAmount()));

            if (bill.getTimestamp() != null) {
                SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault());
                holder.tvDate.setText(fmt.format(bill.getTimestamp()));
            } else {
                holder.tvDate.setText("Date Unknown");
            }

            int icon = bill.isCloud()
                    ? android.R.drawable.stat_sys_upload_done
                    : android.R.drawable.ic_menu_save;

            holder.tvName.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0);

            holder.itemView.setOnClickListener(v -> {
                if (bill.isCloud()) {
                    Toast.makeText(PreviousBillsActivity.this,
                            "Cloud: " + bill.getCustomerName(),
                            Toast.LENGTH_SHORT).show();
                } else {
                    openPdf(bill.getFile());
                }
            });
        }

        private void openPdf(File file) {
            try {
                Uri uri = FileProvider.getUriForFile(
                        PreviousBillsActivity.this,
                        getPackageName() + ".provider",
                        file
                );

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                startActivity(Intent.createChooser(intent, "Open Bill"));

            } catch (Exception e) {
                Toast.makeText(PreviousBillsActivity.this,
                        "No PDF viewer found",
                        Toast.LENGTH_SHORT).show();
            }
        }

        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDate, tvAmount;

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_history_cust_name);
                tvDate = v.findViewById(R.id.tv_history_date);
                tvAmount = v.findViewById(R.id.tv_history_amount);
            }
        }
    }
}