package com.tejas.artifactgenerator;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivityDebug";
    private static final String ACCENTURE_DEFAULT_PASSWORD = "123456";

    private EditText emailField, passwordField;
    private Button loginButton, signupButton;
    private FirebaseAuth mAuth;
    private DatabaseReference allowedEmailsRef;
    private DeviceManager deviceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Log.d(TAG, "onCreate: LoginActivity started");

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        loginButton = findViewById(R.id.loginButton);
        signupButton = findViewById(R.id.signupButton);

        signupButton.setVisibility(View.GONE);

        mAuth = FirebaseAuth.getInstance();
        allowedEmailsRef = FirebaseDatabase.getInstance(
                        "https://artifact-generator15-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("allowed_emails");
        deviceManager = new DeviceManager(this);

        if (mAuth.getCurrentUser() != null) {
            Log.d(TAG, "User already logged in: " + mAuth.getCurrentUser().getEmail());
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }

        loginButton.setOnClickListener(v -> {
            Toast.makeText(this, "Login button clicked", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Login button clicked");
            checkLogin();
        });

        signupButton.setOnClickListener(v -> {
            Log.d(TAG, "Signup button clicked");
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }

    private void checkLogin() {
        Log.d(TAG, "checkLogin() called");

        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email & password required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Special handling for Accenture accounts
        if (email.endsWith("@accenture.com")) {
            Toast.makeText(this, "Accenture user — use password " + ACCENTURE_DEFAULT_PASSWORD, Toast.LENGTH_LONG).show();
            Log.d(TAG, "Accenture user detected");

            if (password.equals(ACCENTURE_DEFAULT_PASSWORD)) {
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            Log.i(TAG, "Accenture login successful for: " + email);
                            deviceManager.saveDeviceForUser(email);
                            checkSubscriptionAndRedirect(email);
                        })
                        .addOnFailureListener(err -> {
                            Log.w(TAG, "Accenture account does not exist, creating...");
                            mAuth.createUserWithEmailAndPassword(email, password)
                                    .addOnSuccessListener(createResult -> {
                                        allowedEmailsRef.child(encodeEmail(email)).setValue(true);
                                        deviceManager.saveDeviceForUser(email);
                                        Toast.makeText(LoginActivity.this,
                                                "Welcome Accenture user!", Toast.LENGTH_SHORT).show();
                                        checkSubscriptionAndRedirect(email);
                                    })
                                    .addOnFailureListener(createErr -> {
                                        Toast.makeText(LoginActivity.this,
                                                "Cannot create account: " + createErr.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                        Log.e(TAG, "Account creation failed: " + createErr.getMessage());
                                    });
                        });
            } else {
                Toast.makeText(this, "Invalid password for Accenture user", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Normal flow for non-Accenture users
        String encodedEmail = encodeEmail(email);
        allowedEmailsRef.child(encodedEmail).get().addOnCompleteListener(task -> {
            Log.d(TAG, "Firebase allowed_emails check complete");

            if (!task.isSuccessful()) {
                String msg = (task.getException() != null) ? task.getException().getMessage() : "Unknown error";
                Toast.makeText(this, "Error checking account: " + msg, Toast.LENGTH_LONG).show();
                return;
            }

            if (task.getResult().exists()) {
                Log.d(TAG, "Email found in allowed_emails: " + email);
                deviceManager.checkDeviceForUser(email, new DeviceManager.DeviceCheckCallback() {
                    @Override
                    public void onSameDevice() {
                        loginFirebase(email, password, true);
                    }

                    @Override
                    public void onDifferentDevice(String registeredDeviceId) {
                        showOverrideDialog(email, password);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(LoginActivity.this, "Device check error: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Log.w(TAG, "Email NOT found in allowed_emails: " + email);
                Toast.makeText(LoginActivity.this, "Account does not exist. Please sign up.", Toast.LENGTH_LONG).show();
                signupButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showOverrideDialog(String email, String password) {
        new AlertDialog.Builder(this)
                .setTitle("Device change detected")
                .setMessage("This account is logged in on another device. Remove old device and continue here?")
                .setPositiveButton("Yes", (dialog, which) -> loginFirebase(email, password, true))
                .setNegativeButton("No", (dialog, which) ->
                        Toast.makeText(LoginActivity.this, "Login cancelled", Toast.LENGTH_SHORT).show())
                .show();
    }

    /**
     * Modified to check subscription expiry immediately after login
     */
    private void loginFirebase(String email, String password, boolean saveDevice) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (saveDevice) {
                        deviceManager.saveDeviceForUser(email);
                    }
                    checkSubscriptionAndRedirect(email);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LoginActivity.this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Checks if subscription is expired, and redirects accordingly
     */
    private void checkSubscriptionAndRedirect(String email) {
        DatabaseReference userRef = FirebaseDatabase.getInstance(
                        "https://artifact-generator15-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users").child(encodeEmail(email));

        userRef.child("subscription_expiry").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Long expiryTimestamp = task.getResult().getValue(Long.class);
                if (expiryTimestamp != null && System.currentTimeMillis() > expiryTimestamp) {
                    // Expired — go to Renewal screen
                    startActivity(new Intent(LoginActivity.this, RenewalActivity.class));
                    finish();
                } else {
                    // Valid subscription
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                }
            } else {
                // On error, allow entry
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }
        });
    }

    private String encodeEmail(String email) {
        return email.replace(".", ",");
    }
}
