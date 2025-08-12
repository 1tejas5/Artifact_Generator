package com.tejas.artifactgenerator;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public class ContactActivity extends AppCompatActivity {
    EditText nameField, emailField, messageField;
    Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact);

        nameField = findViewById(R.id.nameField);
        emailField = findViewById(R.id.emailFieldContact);
        messageField = findViewById(R.id.messageField);
        sendButton = findViewById(R.id.sendButton);

        // Prefill email if user is logged in
        String currentEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "";
        emailField.setText(currentEmail);

        sendButton.setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            String email = emailField.getText().toString().trim();
            String msg = messageField.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseDatabase.getInstance()
                    .getReference("contact_requests")
                    .push()
                    .setValue(new ContactRequest(name, email, msg, System.currentTimeMillis()))
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Thank you for contacting us!", Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    // ContactRequest model class (nested or separate file)
    public static class ContactRequest {
        public String name, email, message;
        public long timestamp;
        public ContactRequest() {}
        public ContactRequest(String n, String e, String m, long t) {
            name = n; email = e; message = m; timestamp = t;
        }
    }
}

