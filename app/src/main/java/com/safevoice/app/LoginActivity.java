package com.safevoice.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.safevoice.app.databinding.ActivityLoginBinding;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    // --- ADD THIS LINE ---
    private FirebaseFirestore db;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        firebaseAuthWithGoogle(account); // Pass the whole account
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        Toast.makeText(this, "Google Sign-In Failed.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        // --- ADD THIS LINE ---
        db = FirebaseFirestore.getInstance();

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.signInButton.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    // --- THIS IS THE UPDATED METHOD ---
    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            // --- LOGIC TO CREATE USER PROFILE IN FIRESTORE ---
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // Get the user's document reference using their UID
                                DocumentReference userDocRef = db.collection("users").document(user.getUid());

                                // Check if the document already exists
                                userDocRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<DocumentSnapshot> snapshotTask) {
                                        if (snapshotTask.isSuccessful()) {
                                            DocumentSnapshot document = snapshotTask.getResult();
                                            // If the document does NOT exist, it's a new user. Create their profile.
                                            if (!document.exists()) {
                                                Log.d(TAG, "New user. Creating profile document in Firestore.");
                                                Map<String, Object> userData = new HashMap<>();
                                                userData.put("email", user.getEmail());
                                                userData.put("uid", user.getUid());
                                                // We don't know their verified name or phone yet,
                                                // so we leave those fields out for now.
                                                // They will be added after KYC and profile setup.

                                                userDocRef.set(userData)
                                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "User profile created."))
                                                        .addOnFailureListener(e -> Log.w(TAG, "Error creating user profile.", e));
                                            } else {
                                                Log.d(TAG, "Existing user. Profile already exists.");
                                            }
                                        } else {
                                            Log.w(TAG, "Failed to check for user document.", snapshotTask.getException());
                                        }
                                        // Proceed to finish the activity regardless
                                        Toast.makeText(LoginActivity.this, "Sign-In Successful.", Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                });
                            } else {
                                // Fallback in case user is null after successful sign-in
                                finish();
                            }
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Firebase Authentication Failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}