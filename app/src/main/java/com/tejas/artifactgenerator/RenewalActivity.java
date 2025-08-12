package com.tejas.artifactgenerator;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.razorpay.Checkout;
import com.razorpay.ExternalWalletListener;
import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;

import org.json.JSONObject;

public class RenewalActivity extends AppCompatActivity
        implements PaymentResultWithDataListener, ExternalWalletListener {

    private static final String TAG = "RenewalDebug";
    private Button renewButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_renewal);

        renewButton = findViewById(R.id.renewButton);
        mAuth = FirebaseAuth.getInstance();

        Checkout.preload(getApplicationContext());

        renewButton.setOnClickListener(v -> startRenewalPayment());
    }

    private void startRenewalPayment() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String email = mAuth.getCurrentUser().getEmail();
        Log.d(TAG, "Starting renewal payment for: " + email);

        Checkout checkout = new Checkout();
        checkout.setKeyID(Config.RAZORPAY_KEY_ID); // Your Razorpay test/live key

        try {
            JSONObject options = new JSONObject();
            options.put("name", "Artifact Generator Subscription Renewal");
            options.put("description", "Renew for 1 Month");
            options.put("currency", "INR");
            options.put("amount", "500"); // ₹5 in paise
            options.put("prefill.email", email);

            checkout.open(RenewalActivity.this, options);
        } catch (Exception e) {
            Log.e(TAG, "Error starting renewal payment: " + e.getMessage());
            Toast.makeText(this, "Error starting payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPaymentSuccess(String razorpayPaymentID, PaymentData paymentData) {
        Log.i(TAG, "Renewal Payment successful: " + razorpayPaymentID);
        Toast.makeText(this, "Payment successful! Subscription renewed.", Toast.LENGTH_SHORT).show();
        updateSubscriptionExpiry();
    }

    @Override
    public void onPaymentError(int code, String description, PaymentData paymentData) {
        Log.e(TAG, "Payment failed - Code: " + code + ", Desc: " + description);
        Toast.makeText(this, "Payment Failed: " + description, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onExternalWalletSelected(String walletName, PaymentData paymentData) {
        Log.i(TAG, "External wallet selected: " + walletName);
        Toast.makeText(this, "External Wallet Selected: " + walletName, Toast.LENGTH_SHORT).show();
    }

    private void updateSubscriptionExpiry() {
        String email = mAuth.getCurrentUser().getEmail();
        if (email == null) return;

        String encodedEmail = email.replace(".", ",");
        DatabaseReference userRef = FirebaseDatabase.getInstance(
                        "https://artifact-generator15-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users").child(encodedEmail);

        long newExpiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000); // +30 days

        userRef.child("subscription_expiry").setValue(newExpiry);
        userRef.child("last_login").setValue(System.currentTimeMillis());

        // After renewal, send to MainActivity
        startActivity(new Intent(RenewalActivity.this, MainActivity.class));
        finish();
    }

    @SuppressWarnings("MissingSuperCall")
    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Please renew your subscription to continue.", Toast.LENGTH_SHORT).show();
        // Prevent navigating back before renewal
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            Checkout.handleActivityResult(
                    this, requestCode, resultCode, data,
                    this, // PaymentResultWithDataListener
                    this  // ExternalWalletListener
            );
        } catch (Exception e) {
            Log.e(TAG, "Error in handleActivityResult: " + e.getMessage());
        }
    }
}
