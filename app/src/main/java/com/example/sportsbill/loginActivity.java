package com.example.sportsbill;

import android.content.Intent;
import android.content.SharedPreferences; // Added
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class loginActivity extends AppCompatActivity {

    private EditText etPin;
    private Button btnLogin;
    private FirebaseFirestore db;
    private TextView tvShopName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Handle Window Insets for Edge-to-Edge
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        db = FirebaseFirestore.getInstance();

        etPin = findViewById(R.id.et_pin);
        btnLogin = findViewById(R.id.btn_login);
        tvShopName = findViewById(R.id.tv_login_shop_name);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String enteredPin = etPin.getText().toString().trim();
                if (!enteredPin.isEmpty()) {
                    authenticatePin(enteredPin);
                } else {
                    Toast.makeText(loginActivity.this, "Please enter PIN", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Load the dynamic Shop Name from SharedPreferences
        loadShopName();
    }

    private void loadShopName() {
        SharedPreferences prefs = getSharedPreferences("SportsBillPrefs", MODE_PRIVATE);
        // "SPORTS BILLING HUB" is the default if no name is set yet
        String shopName = prefs.getString("SHOP_NAME", "SPORTS BILLING HUB");

        if (tvShopName != null) {
            tvShopName.setText(shopName);
        }
    }

    private void authenticatePin(String enteredPin) {
        // Reference the document where you stored the PIN
        db.collection("settings").document("app_settings")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String storedPin = documentSnapshot.getString("loginPin");
                        if (enteredPin.equals(storedPin)) {
                            // PIN is correct, now sign in the user.
                            FirebaseAuth.getInstance().signInWithEmailAndPassword("chandru.dude2000@gmail.com", "qwerty")
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            Toast.makeText(loginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                                            Intent intent = new Intent(loginActivity.this, BillEntry.class);
                                            startActivity(intent);
                                            finish();
                                        } else {
                                            Toast.makeText(loginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } else {
                            Toast.makeText(loginActivity.this, "Incorrect PIN. Please try again.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(loginActivity.this, "PIN settings not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(loginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}