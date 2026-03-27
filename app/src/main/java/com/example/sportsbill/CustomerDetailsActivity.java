package com.example.sportsbill;

import android.content.Intent;
import android.nfc.NfcAdapter; // Import for NFC check
import android.os.Bundle;
import android.view.View; // Import for visibility control
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.Serializable;
import java.util.List;

public class CustomerDetailsActivity extends AppCompatActivity {

    private TextInputEditText etCustomerName, etCustomerContact;
    private RadioGroup rgPaymentMethods;
    private MaterialButton btnNext, btnClearCustomer;
    private MaterialRadioButton rbNfc; // Reference for the NFC button

    private List<BillEntry.BillItem> billItemsList;
    private double totalAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_details);

        // Retrieve data passed from the previous activity
        Intent intent = getIntent();
        if (intent != null) {
            billItemsList = (List<BillEntry.BillItem>) intent.getSerializableExtra("BILL_ITEMS");
            totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0);
        }

        // Initialize UI components
        etCustomerName = findViewById(R.id.et_customer_name);
        etCustomerContact = findViewById(R.id.et_customer_contact);
        rgPaymentMethods = findViewById(R.id.rg_payment_methods);
        rbNfc = findViewById(R.id.rb_nfc); // Ensure this ID matches your XML
        btnNext = findViewById(R.id.btn_next);
        btnClearCustomer = findViewById(R.id.btn_clear_customer);

        // --- NFC DYNAMIC VISIBILITY CHECK ---
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            // Device does not support NFC - Hide the button
            rbNfc.setVisibility(View.GONE);
        } else {
            // Device supports NFC - Show the button
            rbNfc.setVisibility(View.VISIBLE);
        }

        btnNext.setOnClickListener(v -> {
            String customerName = etCustomerName.getText().toString().trim();
            String customerContact = etCustomerContact.getText().toString().trim();
            String paymentMethod = getSelectedPaymentMethod();

            if (customerName.isEmpty() || customerContact.isEmpty()) {
                Toast.makeText(this, "Please enter customer details.", Toast.LENGTH_SHORT).show();
            } else {
                Intent reviewIntent = new Intent(CustomerDetailsActivity.this, BillReviewActivity.class);
                reviewIntent.putExtra("BILL_ITEMS", (Serializable) billItemsList);
                reviewIntent.putExtra("TOTAL_AMOUNT", totalAmount);
                reviewIntent.putExtra("CUSTOMER_NAME", customerName);
                reviewIntent.putExtra("CUSTOMER_CONTACT", customerContact);
                reviewIntent.putExtra("PAYMENT_METHOD", paymentMethod);
                startActivity(reviewIntent);
            }
        });

        btnClearCustomer.setOnClickListener(v -> {
            etCustomerName.setText("");
            etCustomerContact.setText("");
            rgPaymentMethods.check(R.id.rb_cash);
            Toast.makeText(this, "Customer details cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private String getSelectedPaymentMethod() {
        int checkedId = rgPaymentMethods.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_cash) {
            return "Cash";
        } else if (checkedId == R.id.rb_card) {
            return "Card";
        } else if (checkedId == R.id.rb_upi) {
            return "UPI";
        } else if (checkedId == R.id.rb_nfc) {
            return "NFC";
        }
        return "Not Specified";
    }
}