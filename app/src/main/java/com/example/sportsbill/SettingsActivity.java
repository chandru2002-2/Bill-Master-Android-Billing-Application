package com.example.sportsbill;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private AutoCompleteTextView dropdownGst, dropdownCurrency;
    private MaterialButton btnEditShop, btnClearHistory, btnSupport, btnLogout, btnViewTransactionHistory;
    private TextInputEditText etSettingsUpi;
    private SwitchMaterial switchDarkMode, switchAutoBackup;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("SportsBillPrefs", MODE_PRIVATE);

        initializeViews();
        setupDropdowns();
        setupClickListeners();
        setupUpiSync();
    }

    private void initializeViews() {
        dropdownGst = findViewById(R.id.dropdown_gst);
        dropdownCurrency = findViewById(R.id.dropdown_currency);
        btnEditShop = findViewById(R.id.btn_edit_shop_info);
        btnClearHistory = findViewById(R.id.btn_clear_history);
        btnSupport = findViewById(R.id.btn_contact_support);
        btnLogout = findViewById(R.id.btn_logout);
        btnViewTransactionHistory = findViewById(R.id.btn_view_transaction_history);
        etSettingsUpi = findViewById(R.id.et_settings_upi);

        // Load UPI from prefs
        etSettingsUpi.setText(prefs.getString("SHOP_UPI", "merchant@upi"));

        switchDarkMode = findViewById(R.id.switch_dark_mode);
        switchAutoBackup = findViewById(R.id.switch_auto_backup);
        switchDarkMode.setChecked(prefs.getBoolean("dark_mode", false));
    }

    private void setupDropdowns() {
        String[] gstOptions = {"0% (Exempt)", "5% (GST)", "12% (GST)", "18% (Standard)", "28% (Luxury)"};
        ArrayAdapter<String> gstAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, gstOptions);
        dropdownGst.setAdapter(gstAdapter);
        dropdownGst.setOnItemClickListener((parent, view, position, id) -> {
            prefs.edit().putString("DEFAULT_GST", gstOptions[position]).apply();
        });

        String[] currencyOptions = {"INR (₹)", "USD ($)", "EUR (€)", "GBP (£)"};
        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, currencyOptions);
        dropdownCurrency.setAdapter(currencyAdapter);
        dropdownCurrency.setOnItemClickListener((parent, view, position, id) -> {
            prefs.edit().putString("DEFAULT_CURRENCY", currencyOptions[position]).apply();
        });
    }

    private void setupUpiSync() {
        etSettingsUpi.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString("SHOP_UPI", s.toString().trim()).apply();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupClickListeners() {
        // 1. Updated Edit Shop Info (Handles Name & GSTIN)
        btnEditShop.setOnClickListener(v -> {
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 20, 50, 0);

            final EditText etName = new EditText(this);
            etName.setHint("Shop Name");
            etName.setText(prefs.getString("SHOP_NAME", "SPORTS BILLING HUB"));
            layout.addView(etName);

            final EditText etGst = new EditText(this);
            etGst.setHint("GSTIN (e.g. 29ABCDE1234F1Z5)");
            etGst.setText(prefs.getString("SHOP_GST", ""));
            layout.addView(etGst);

            new android.app.AlertDialog.Builder(this)
                    .setTitle("Edit Business Info")
                    .setView(layout)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String name = etName.getText().toString().trim();
                        String gstin = etGst.getText().toString().trim();

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("SHOP_NAME", name);
                        editor.putString("SHOP_GST", gstin);
                        editor.apply();

                        Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // 2. Transaction History
        btnViewTransactionHistory.setOnClickListener(v ->
                startActivity(new Intent(this, PreviousBillsActivity.class)));

        // 3. Dark Mode
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ?
                    AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        // 4. Clear Storage
        btnClearHistory.setOnClickListener(v -> {
            File billsDir = getExternalFilesDir("Bills");
            if (billsDir != null && billsDir.exists()) {
                File[] files = billsDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                    Toast.makeText(this, "Local storage cleared", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 5. Support
        btnSupport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@sportsbill.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support: SportsBill");
            try {
                startActivity(Intent.createChooser(intent, "Contact via..."));
            } catch (Exception e) {
                Toast.makeText(this, "No email client found", Toast.LENGTH_SHORT).show();
            }
        });

        // 6. Logout
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, loginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}