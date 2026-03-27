package com.example.sportsbill;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ManualEntry extends AppCompatActivity {

    private EditText etItemName;
    private EditText etItemAmount;
    private Button btnAddItem;
    private Button btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_entry);

        etItemName = findViewById(R.id.et_item_name);
        etItemAmount = findViewById(R.id.et_item_amount);
        btnAddItem = findViewById(R.id.btn_add_item);
        btnCancel = findViewById(R.id.btn_cancel);

        btnAddItem.setOnClickListener(v -> {
            String itemName = etItemName.getText().toString().trim();
            String itemAmountText = etItemAmount.getText().toString().trim();

            if (itemName.isEmpty() || itemAmountText.isEmpty()) {
                Toast.makeText(this, "Please enter both item name and amount.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double itemAmount = Double.parseDouble(itemAmountText);
                // Create an intent to send the data back to the calling activity
                Intent resultIntent = new Intent();
                resultIntent.putExtra("ITEM_NAME", itemName);
                resultIntent.putExtra("ITEM_AMOUNT", itemAmount);
                setResult(RESULT_OK, resultIntent);
                finish(); // Close this activity
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount format.", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish(); // Close without sending data
        });
    }
}