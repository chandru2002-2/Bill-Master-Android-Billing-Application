package com.example.sportsbill;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BillEntry extends AppCompatActivity {

    private RecyclerView rvBillItems;
    private TextView tvTotalAmount, tvShopName;
    private FloatingActionButton btnScanItem;
    private TextView btnManualEntry;
    private MaterialButton btnNext;
    private ImageButton btnClearBill;
    private ImageButton btnViewHistory, btnSettings;
    private ImageView btnManageQuickAdd;
    private FlexboxLayout flProductButtons;

    private List<BillItem> billItemsList;
    private BillItemsAdapter billItemsAdapter;
    private double totalAmount = 0.0;

    private static final String PREF_NAME = "BillAppPrefs";
    private static final String KEY_QUICK_ITEMS = "quick_add_items";

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("SportsBillPrefs", MODE_PRIVATE);
        String shopName = prefs.getString("SHOP_NAME", "BILLING HUB");
        if (tvShopName != null) {
            tvShopName.setText(shopName);
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_bill_entry);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bill_entry_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Views
        rvBillItems = findViewById(R.id.rv_bill_items);
        tvTotalAmount = findViewById(R.id.tv_total_amount);
        btnScanItem = findViewById(R.id.btn_scan_item);
        btnManualEntry = findViewById(R.id.btn_manual_entry);
        btnNext = findViewById(R.id.btn_next);
        btnClearBill = findViewById(R.id.btn_clear_bill_entry);
        flProductButtons = findViewById(R.id.fl_product_buttons);
        tvShopName = findViewById(R.id.tv_shop_name);
        btnViewHistory = findViewById(R.id.btn_view_history);
        btnSettings = findViewById(R.id.btn_settings);
        btnManageQuickAdd = findViewById(R.id.btn_manage_quick_add);

        billItemsList = new ArrayList<>();
        billItemsAdapter = new BillItemsAdapter(billItemsList);
        rvBillItems.setLayoutManager(new LinearLayoutManager(this));
        rvBillItems.setAdapter(billItemsAdapter);

        // Listeners
        btnScanItem.setOnClickListener(v -> startScanner());
        btnManualEntry.setOnClickListener(v -> manualEntryLauncher.launch(new Intent(this, ManualEntry.class)));
        btnViewHistory.setOnClickListener(v -> startActivity(new Intent(this, PreviousBillsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnManageQuickAdd.setOnClickListener(v -> showManageQuickAddDialog());

        btnNext.setOnClickListener(v -> {
            if (billItemsList.isEmpty()) {
                Toast.makeText(this, "Please add items first.", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, CustomerDetailsActivity.class);
                intent.putExtra("BILL_ITEMS", (Serializable) billItemsList);
                intent.putExtra("TOTAL_AMOUNT", totalAmount);
                startActivity(intent);
            }
        });

        btnClearBill.setOnClickListener(v -> {
            billItemsList.clear();
            totalAmount = 0.0;
            updateUI();
            billItemsAdapter.notifyDataSetChanged();
        });

        loadQuickAddItems();
        loadSettings();
    }

    private void loadQuickAddItems() {
        flProductButtons.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_QUICK_ITEMS, "[]");

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.getString("name");
                double price = obj.getDouble("price");

                addQuickActionButton(name, price);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addQuickActionButton(String name, double price) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 16, 8);
        b.setLayoutParams(params);
        b.setText(String.format("%s\n₹%.0f", name, price));
        b.setAllCaps(false);
        b.setOnClickListener(v -> addItem(name, price));
        flProductButtons.addView(b);
    }

    private void showManageQuickAddDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Quick Item");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etName = new EditText(this);
        etName.setHint("Item Name");
        layout.addView(etName);

        final EditText etPrice = new EditText(this);
        etPrice.setHint("Price");
        etPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etPrice);

        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();

            if (!name.isEmpty() && !priceStr.isEmpty()) {
                saveNewQuickItem(name, Double.parseDouble(priceStr));
            }
        });

        builder.setNeutralButton("Clear All", (dialog, which) -> {
            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().remove(KEY_QUICK_ITEMS).apply();
            loadQuickAddItems();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveNewQuickItem(String name, double price) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_QUICK_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("price", price);
            array.put(obj);

            prefs.edit().putString(KEY_QUICK_ITEMS, array.toString()).apply();
            loadQuickAddItems();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a product (Name|Price)");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        barcodeLauncher.launch(options);
    }

    private void updateUI() {
        tvTotalAmount.setText(String.format(Locale.getDefault(), "₹ %.2f", totalAmount));
    }

    private void addItem(String name, double price) {
        for (BillItem item : billItemsList) {
            if (item.getName().equalsIgnoreCase(name)) {
                item.increaseQuantity();
                totalAmount += price;
                updateUI();
                billItemsAdapter.notifyDataSetChanged();
                return;
            }
        }
        billItemsList.add(new BillItem(name, price));
        totalAmount += price;
        updateUI();
        billItemsAdapter.notifyDataSetChanged();
    }

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) parseBarcodeData(result.getContents());
    });

    private final ActivityResultLauncher<Intent> manualEntryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            addItem(result.getData().getStringExtra("ITEM_NAME"), result.getData().getDoubleExtra("ITEM_AMOUNT", 0.0));
        }
    });

    private void parseBarcodeData(String data) {
        try {
            String[] parts = data.split("\\|");
            addItem(parts[0].trim(), Double.parseDouble(parts[1].trim()));
        } catch (Exception e) {
            Toast.makeText(this, "Invalid Barcode Format", Toast.LENGTH_SHORT).show();
        }
    }

    // --- BillData class for Firestore Sync ---
    public static class BillData implements Serializable {
        public String customerName, contact, method, pId;
        public double amount;
        public List<BillItem> items;
        public Date date = new Date();

        public BillData() {}

        public BillData(String n, String c, String m, String p, double a, List<BillItem> i) {
            this.customerName = n;
            this.contact = c;
            this.method = m;
            this.pId = p;
            this.amount = a;
            this.items = i;
        }
    }

    // --- Inner Classes & Adapter ---
    public static class BillItem implements Serializable {
        public String name;
        public double amount;
        public int quantity;

        public BillItem() {}

        public BillItem(String name, double amount) {
            this.name = name;
            this.amount = amount;
            this.quantity = 1;
        }

        public String getName() { return name; }
        public double getAmount() { return amount; }
        public int getQuantity() { return quantity; }
        public void increaseQuantity() { this.quantity++; }
        public void decreaseQuantity() { this.quantity--; }
        public double getTotal() { return amount * quantity; }
    }

    private class BillItemsAdapter extends RecyclerView.Adapter<BillItemsAdapter.BillItemViewHolder> {
        private List<BillItem> items;
        public BillItemsAdapter(List<BillItem> items) { this.items = items; }

        @NonNull
        @Override
        public BillItemViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new BillItemViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_bill_entry, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull BillItemViewHolder h, int pos) {
            BillItem item = items.get(pos);
            h.tvName.setText(item.getName());
            h.tvQty.setText(String.valueOf(item.getQuantity()));
            h.tvPrice.setText(String.format(Locale.getDefault(), "₹ %.2f", item.getTotal()));

            h.btnPlus.setOnClickListener(v -> {
                item.increaseQuantity();
                totalAmount += item.getAmount();
                updateUI();
                notifyItemChanged(pos);
            });

            h.btnMinus.setOnClickListener(v -> {
                totalAmount -= item.getAmount();
                item.decreaseQuantity();
                if (item.getQuantity() <= 0) {
                    items.remove(pos);
                    notifyDataSetChanged();
                } else {
                    notifyItemChanged(pos);
                }
                updateUI();
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class BillItemViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvQty, tvPrice;
            ImageButton btnPlus, btnMinus;
            BillItemViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_item_name_entry);
                tvQty = v.findViewById(R.id.tv_item_count);
                tvPrice = v.findViewById(R.id.tv_item_amount_entry);
                btnPlus = v.findViewById(R.id.btn_increase_quantity);
                btnMinus = v.findViewById(R.id.btn_decrease_quantity);
            }
        }
    }
}