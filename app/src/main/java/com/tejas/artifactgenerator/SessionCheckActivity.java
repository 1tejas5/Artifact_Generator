package com.tejas.artifactgenerator;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SessionCheckActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkSessionAndRedirect();
    }

    private void checkSessionAndRedirect() {

        // 1️⃣ Allow offline mode
        if (!isNetworkAvailable()) {
            Toast.makeText(this,
                    "No internet connection. Continuing in offline mode.",
                    Toast.LENGTH_LONG).show();
            goToMain();
            return;
        }

        // 2️⃣ Require login
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            goToLogin();
            return;
        }

        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (email == null) {
            FirebaseAuth.getInstance().signOut();
            goToLogin();
            return;
        }

        String encodedEmail = email.replace(".", ",");
        String currentDeviceId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        DatabaseReference userRef = FirebaseDatabase.getInstance(
                "https://artifact-generator15-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).getReference("users").child(encodedEmail);

        // 3️⃣ Check subscription & device
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {

                String storedDeviceId = task.getResult().child("device_id").getValue(String.class);
                Long expiryTimestamp = task.getResult().child("subscription_expiry").getValue(Long.class);

                // 🛑 Subscription comes first — block app until renewed
                if (expiryTimestamp != null && System.currentTimeMillis() > expiryTimestamp) {
                    // DO NOT send them to Login first — force Renewal
                    Toast.makeText(this,
                            "Your subscription has expired. Please renew to continue.",
                            Toast.LENGTH_LONG).show();
                    goToRenewal();
                    return;
                }

                // 🔒 Device enforcement after subscription check
                if (storedDeviceId == null || !storedDeviceId.equals(currentDeviceId)) {
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(this,
                            "Your session has expired. Please log in again.",
                            Toast.LENGTH_LONG).show();
                    goToLogin();
                } else {
                    goToMain();
                }

            } else {
                // On DB fetch error → allow offline mode
                Toast.makeText(this,
                        "Network error. Continuing in offline mode.",
                        Toast.LENGTH_SHORT).show();
                goToMain();
            }
        });
    }

    // Internet check
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
        return false;
    }

    // Navigation helpers
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void goToRenewal() {
        startActivity(new Intent(this, RenewalActivity.class));
        finish();
    }
}
