package com.tejas.artifactgenerator;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.razorpay.Checkout;
import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;
import com.razorpay.ExternalWalletListener;

import org.json.JSONObject;

public class SignupActivity extends AppCompatActivity
        implements PaymentResultWithDataListener, ExternalWalletListener {

    private static final String TAG = "SignupDebug";

    private EditText emailField, passwordField;
    private Button payButton;
    private FirebaseAuth mAuth;
    private DatabaseReference allowedEmailsRef;
    private DeviceManager deviceManager;

    private String email, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        Log.d(TAG, "SignupActivity started");

        // Preload Razorpay Checkout for faster loading
        Checkout.preload(getApplicationContext());

        // Link UI components
        emailField = findViewById(R.id.emailFieldSignup);
        passwordField = findViewById(R.id.passwordFieldSignup);
        payButton = findViewById(R.id.payButton);

        mAuth = FirebaseAuth.getInstance();
        // Region-specific DB URL to avoid "connection killed" errors
        allowedEmailsRef = FirebaseDatabase
                .getInstance("https://artifact-generator15-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("allowed_emails");

        deviceManager = new DeviceManager(this);

        payButton.setOnClickListener(v -> {
            email = emailField.getText().toString().trim();
            password = passwordField.getText().toString().trim();

            if (!validateInputs()) return;
            startPayment();
        });
    }

    private boolean validateInputs() {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email & password required", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 4) {
            Toast.makeText(this, "Password too short", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void startPayment() {
        Log.d(TAG, "Opening Razorpay checkout");

        Checkout checkout = new Checkout();
        checkout.setKeyID(Config.RAZORPAY_KEY_ID); // Test/live key from Config.java

        try {
            JSONObject options = new JSONObject();
            options.put("name", "Artifact Generator Subscription");
            options.put("description", "1 Month Access for ₹5");
            options.put("currency", "INR");
            options.put("amount", "500"); // ₹5 in paise
            options.put("prefill.email", email);

            // Open Razorpay Checkout Activity
            checkout.open(SignupActivity.this, options);

        } catch (Exception e) {
            Log.e(TAG, "Error starting payment: " + e.getMessage());
            Toast.makeText(this,
                    "Error in starting payment: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // Razorpay success callback with payment data
    @Override
    public void onPaymentSuccess(String razorpayPaymentID, PaymentData paymentData) {
        Log.i(TAG, "Payment successful: " + razorpayPaymentID);
        Toast.makeText(this,
                "Payment Successful! ID: " + razorpayPaymentID,
                Toast.LENGTH_SHORT).show();
        createAccountAndAllow();
    }

    // Razorpay error callback with payment data
    @Override
    public void onPaymentError(int code, String description, PaymentData paymentData) {
        Log.e(TAG, "Payment failed - Code: " + code + ", Desc: " + description);
        Toast.makeText(this,
                "Payment Failed: " + description,
                Toast.LENGTH_LONG).show();
    }

    // External wallet selection callback
    @Override
    public void onExternalWalletSelected(String walletName, PaymentData paymentData) {
        Log.i(TAG, "External wallet selected: " + walletName);
        Toast.makeText(this,
                "External Wallet Selected: " + walletName,
                Toast.LENGTH_SHORT).show();
    }

    private void createAccountAndAllow() {
        Log.d(TAG, "Creating user account");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "User created, adding to allowed_emails");

                    allowedEmailsRef.child(encodeEmail(email)).setValue(true);

                    // Save device ID
                    deviceManager.saveDeviceForUser(email);

                    // Store subscription expiry timestamp (30 days from now)
                    long oneMonthExpiry = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000);

                    DatabaseReference userRef = FirebaseDatabase.getInstance(
                                    "https://artifact-generator15-default-rtdb.asia-southeast1.firebasedatabase.app")
                            .getReference("users")
                            .child(encodeEmail(email));

                    userRef.child("subscription_expiry").setValue(oneMonthExpiry);
                    userRef.child("last_login").setValue(System.currentTimeMillis());

                    Toast.makeText(SignupActivity.this,
                            "Account created & payment successful",
                            Toast.LENGTH_LONG).show();

                    startActivity(new Intent(SignupActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Sign-up failed: " + e.getMessage());
                    Toast.makeText(SignupActivity.this,
                            "Sign-up failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String encodeEmail(String email) {
        return email.replace(".", ",");
    }

    // Correct handleActivityResult() for Razorpay 1.6.33+
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            Checkout.handleActivityResult(
                    this,        // Activity
                    requestCode,
                    resultCode,
                    data,
                    this,        // PaymentResultWithDataListener
                    this         // ExternalWalletListener
            );
        } catch (Exception e) {
            Log.e(TAG, "Error in handleActivityResult: " + e.getMessage());
        }
    }
}
