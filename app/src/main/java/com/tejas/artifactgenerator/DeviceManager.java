package com.tejas.artifactgenerator;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DeviceManager {

    private final String currentDeviceId;
    private final DatabaseReference usersRef;

    public interface DeviceCheckCallback {
        void onSameDevice();
        void onDifferentDevice(String registeredDeviceId);
        void onError(String errorMessage);
    }

    public DeviceManager(Context context) {
        currentDeviceId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);

        // Use region-specific DB URL
        usersRef = FirebaseDatabase.getInstance(
                "https://artifact-generator15-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).getReference("users");
    }

    private String encodeEmail(String email) {
        return email.replace(".", ",");
    }

    public String getCurrentDeviceId() {
        return currentDeviceId;
    }

    public void checkDeviceForUser(String email, DeviceCheckCallback callback) {
        String encodedEmail = encodeEmail(email);
        Log.d("DeviceManager", "checkDeviceForUser: " + email + " (encoded: " + encodedEmail + ")");
        Log.d("DeviceManager", "Current device ID: " + currentDeviceId);

        usersRef.child(encodedEmail).child("device_id")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Log.d("DeviceManager", "No device ID stored — treating as same device.");
                            callback.onSameDevice();
                        } else {
                            String registeredId = snapshot.getValue(String.class);
                            Log.d("DeviceManager", "Stored device ID: " + registeredId);

                            if (registeredId == null || registeredId.isEmpty()) {
                                Log.d("DeviceManager", "Stored ID empty — treating as same device.");
                                callback.onSameDevice();
                            } else if (registeredId.equals(currentDeviceId)) {
                                Log.d("DeviceManager", "Device matches.");
                                callback.onSameDevice();
                            } else {
                                Log.d("DeviceManager", "Different device detected.");
                                callback.onDifferentDevice(registeredId);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("DeviceManager", "Error checking device: " + error.getMessage());
                        callback.onError(error.getMessage());
                    }
                });
    }

    public void saveDeviceForUser(String email) {
        String encodedEmail = encodeEmail(email);
        Log.d("DeviceManager", "Saving device ID for: " + email);
        usersRef.child(encodedEmail).child("device_id").setValue(currentDeviceId);
        usersRef.child(encodedEmail).child("last_login").setValue(System.currentTimeMillis());
    }
}


