package com.example.sportsbill;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class BillReviewActivity extends AppCompatActivity {

    private List<BillEntry.BillItem> billItemsList;
    private double totalAmount;
    private String customerName, customerContact, paymentMethod;
    private String paymentId;

    private TextView tvShopName, tvCustomerDetails, tvPaymentMethod, tvTotalAmount, tvPaymentStatus;
    private RecyclerView rvReviewItems;
    private MaterialButton btnGeneratePDF, btnSaveBill, btnSendBill, btnBackToHome;

    private FirebaseFirestore db;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_review);

        db = FirebaseFirestore.getInstance();

        Intent intent = getIntent();
        if (intent != null) {
            billItemsList = (List<BillEntry.BillItem>) intent.getSerializableExtra("BILL_ITEMS");
            totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0);
            customerName = intent.getStringExtra("CUSTOMER_NAME");
            customerContact = intent.getStringExtra("CUSTOMER_CONTACT");
            paymentMethod = intent.getStringExtra("PAYMENT_METHOD");
        }

        initViews();
        updateUI();
        handlePayment();

        btnGeneratePDF.setOnClickListener(v -> generatePDF());
        btnSaveBill.setOnClickListener(v -> saveBillToFirestore());
        btnSendBill.setOnClickListener(v -> {
            File pdfFile = generatePDF();
            if (pdfFile != null) sendBill(pdfFile);
        });

        btnBackToHome.setOnClickListener(v -> {
            Intent homeIntent = new Intent(this, BillEntry.class);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            finish();
        });
    }

    private void initViews() {
        tvShopName = findViewById(R.id.tv_shop_name);
        tvCustomerDetails = findViewById(R.id.tv_customer_details);
        tvPaymentMethod = findViewById(R.id.tv_payment_method);
        tvTotalAmount = findViewById(R.id.tv_total_amount);
        tvPaymentStatus = findViewById(R.id.tv_payment_status);
        rvReviewItems = findViewById(R.id.rv_review_items);
        btnGeneratePDF = findViewById(R.id.btn_generate_pdf);
        btnSaveBill = findViewById(R.id.btn_save_bill);
        btnSendBill = findViewById(R.id.btn_send_bill);
        btnBackToHome = findViewById(R.id.back_tohome);

        rvReviewItems.setLayoutManager(new LinearLayoutManager(this));
        rvReviewItems.setAdapter(new BillItemsAdapter(billItemsList));
    }

    private void updateUI() {
        SharedPreferences prefs = getSharedPreferences("SportsBillPrefs", MODE_PRIVATE);
        tvShopName.setText(prefs.getString("SHOP_NAME", "BILLING HUB"));
        tvCustomerDetails.setText("Name: " + customerName + "\nContact: " + customerContact);
        tvPaymentMethod.setText("Method: " + paymentMethod);
        tvTotalAmount.setText(String.format(Locale.getDefault(), "TOTAL: ₹ %.2f", totalAmount));
    }

    private void handlePayment() {
        if ("Card".equals(paymentMethod)) handleCardPayment();
        else if ("UPI".equals(paymentMethod)) handleUpiPayment();
        else if ("NFC".equals(paymentMethod)) handleNfcPayment();
        else handleCashPayment();
    }

    private void handleCashPayment() {
        paymentId = "CASH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        tvPaymentStatus.setText("Status: Paid (Cash)");
        enableButtons();
    }

    private void handleCardPayment() {
        tvPaymentStatus.setText("Processing Card...");
        disableButtons();
        handler.postDelayed(() -> {
            paymentId = "CARD_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            tvPaymentStatus.setText("Paid (Card)");
            enableButtons();
        }, 2000);
    }

    private void handleUpiPayment() {
        tvPaymentStatus.setText("Waiting for UPI...");
        disableButtons();
        showUpiQrCode();
    }

    private void handleNfcPayment() {
        tvPaymentStatus.setText("Tap for NFC...");
        disableButtons();
        handler.postDelayed(() -> {
            paymentId = "NFC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            tvPaymentStatus.setText("Paid (NFC)");
            enableButtons();
        }, 2000);
    }

    private void showUpiQrCode() {
        Dialog dialog = new Dialog(this);
        // Updated to your specific layout filename
        dialog.setContentView(R.layout.btn_qr_cancel);

        ImageView ivQr = dialog.findViewById(R.id.iv_qr_code);
        TextView tvQrAmount = dialog.findViewById(R.id.tv_qr_amount);
        MaterialButton btnPaid = dialog.findViewById(R.id.btn_qr_paid);
        MaterialButton btnCancel = dialog.findViewById(R.id.btn_qr_cancel);

        SharedPreferences prefs = getSharedPreferences("SportsBillPrefs", MODE_PRIVATE);
        String merchantUpi = prefs.getString("SHOP_UPI", "merchant@upi");
        String shopName = prefs.getString("SHOP_NAME", "Shop");

        try {
            // FIX: Strict formatting ensures amount is never 0.00 if totalAmount is present
            String formattedAmount = String.format(Locale.US, "%.2f", totalAmount);

            if (tvQrAmount != null) {
                tvQrAmount.setText("Amount: ₹ " + formattedAmount);
            }

            String shopNameEncoded = shopName.replace(" ", "%20");
            String upi = "upi://pay?pa=" + merchantUpi + "&pn=" + shopNameEncoded + "&am=" + formattedAmount + "&cu=INR";

            ivQr.setImageBitmap(generateQRCode(upi, 512, 512));

        } catch (Exception e) {
            e.printStackTrace();
        }

        btnPaid.setOnClickListener(v -> {
            paymentId = "UPI_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            tvPaymentStatus.setText("Paid (UPI)");
            dialog.dismiss();
            enableButtons();
        });

        // Handle Cancel Button Logic
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private File generatePDF() {
        File dir = getExternalFilesDir("Bills");
        if (dir == null || (!dir.exists() && !dir.mkdirs())) return null;

        String cleanName = (customerName != null && !customerName.trim().isEmpty())
                ? customerName.trim().replaceAll("\\s+", "_") : "Unknown";

        String formattedAmount = String.format(Locale.US, "%.2f", totalAmount);
        String fileName = "Bill_" + cleanName + "_" + formattedAmount + "_" + System.currentTimeMillis() + ".pdf";
        File file = new File(dir, fileName);

        SharedPreferences prefs = getSharedPreferences("SportsBillPrefs", MODE_PRIVATE);

        try {
            PdfWriter writer = new PdfWriter(new FileOutputStream(file));
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            String shop = prefs.getString("SHOP_NAME", "BILLING HUB");
            String gst = prefs.getString("SHOP_GST", "N/A");

            document.add(new Paragraph(shop).setBold().setFontSize(20).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("GSTIN: " + gst).setFontSize(10).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Tax Invoice").setBold().setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Date: " + new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(new Date())).setFontSize(10).setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\nCustomer: " + customerName).setBold());
            document.add(new Paragraph("Contact: " + customerContact).setFontSize(10));

            Table table = new Table(UnitValue.createPercentArray(new float[]{1, 5, 2, 2})).useAllAvailableWidth();
            table.addHeaderCell(new Cell().add(new Paragraph("S.No").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Item").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Qty").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Total").setBold()));

            int i = 1;
            for (BillEntry.BillItem item : billItemsList) {
                table.addCell(new Cell().add(new Paragraph(String.valueOf(i++))));
                table.addCell(new Cell().add(new Paragraph(item.getName())));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(item.getQuantity()))));
                table.addCell(new Cell().add(new Paragraph(String.format("%.2f", item.getTotal()))));
            }
            document.add(table);

            document.add(new Paragraph("\nGrand Total: ₹ " + formattedAmount).setBold().setFontSize(14).setTextAlignment(TextAlignment.RIGHT));
            document.add(new Paragraph("Payment: " + paymentMethod + " | ID: " + paymentId).setFontSize(9));

            document.close();
            return file;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveBillToFirestore() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        BillData cloudData = new BillData(customerName, totalAmount, billItemsList);
        db.collection("users").document(uid).collection("bills")
                .add(cloudData)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Synced to Cloud", Toast.LENGTH_SHORT).show();
                    btnSaveBill.setEnabled(false);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Sync Failed", Toast.LENGTH_SHORT).show());
    }

    private void sendBill(File file) {
        if (customerContact == null || customerContact.trim().isEmpty()) {
            shareGeneric(file);
            return;
        }

        String cleanNumber = customerContact.replaceAll("[^0-9]", "");
        if (cleanNumber.length() == 10) cleanNumber = "91" + cleanNumber;

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra("jid", cleanNumber + "@s.whatsapp.net");
            intent.setPackage("com.whatsapp"); // Try standard WhatsApp
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            try {
                // Try WhatsApp Business fallback
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                Intent w4b = new Intent(Intent.ACTION_SEND);
                w4b.setType("application/pdf");
                w4b.putExtra(Intent.EXTRA_STREAM, uri);
                w4b.setPackage("com.whatsapp.w4b");
                w4b.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(w4b);
            } catch (Exception e2) {
                shareGeneric(file);
            }
        }
    }

    private void shareGeneric(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Bill"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing file", Toast.LENGTH_SHORT).show();
        }
    }

    private void disableButtons() {
        btnGeneratePDF.setEnabled(false); btnSaveBill.setEnabled(false); btnSendBill.setEnabled(false);
    }

    private void enableButtons() {
        btnGeneratePDF.setEnabled(true); btnSaveBill.setEnabled(true); btnSendBill.setEnabled(true);
    }

    private Bitmap generateQRCode(String text, int w, int h) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, w, h);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
        return bitmap;
    }

    private static class BillItemsAdapter extends RecyclerView.Adapter<BillItemsAdapter.ViewHolder> {
        private List<BillEntry.BillItem> list;
        BillItemsAdapter(List<BillEntry.BillItem> list) { this.list = list; }
        @NonNull public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_bill_review, p, false));
        }
        public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            BillEntry.BillItem item = list.get(pos);
            h.name.setText(item.getName());
            h.qty.setText("Qty: " + item.getQuantity());
            h.price.setText(String.format("₹ %.2f", item.getTotal()));
        }
        public int getItemCount() { return list != null ? list.size() : 0; }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, qty, price;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.tv_item_name_entry);
                qty = v.findViewById(R.id.tv_item_count);
                price = v.findViewById(R.id.tv_item_amount_entry);
            }
        }
    }

    public static class BillData implements Serializable {
        public String customerName;
        public double amount;
        public Date timestamp;
        public List<BillEntry.BillItem> items;
        public BillData() {}
        public BillData(String name, double amt, List<BillEntry.BillItem> items) {
            this.customerName = name;
            this.amount = amt;
            this.items = items;
            this.timestamp = new Date();
        }
    }
}