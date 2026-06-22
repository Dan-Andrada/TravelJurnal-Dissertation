package com.example.traveljurnalapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class TripsHistoryActivity extends AppCompatActivity {

    private EditText searchEditText;
    private RecyclerView tripsRecyclerView;
    private FirebaseFirestore db;
    private String userId;

    private TripsHistoryAdapter adapter;
    private final List<TripItem> tripsList = new ArrayList<>();
    private final List<TripItem> filteredTripsList = new ArrayList<>();

    private static final String PERFORMANCE_TAG = "PERFORMANCE_HISTORY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips_history);

        searchEditText = findViewById(R.id.searchEditText);
        tripsRecyclerView = findViewById(R.id.tripsRecyclerView);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        adapter = new TripsHistoryAdapter(this, filteredTripsList);
        tripsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tripsRecyclerView.setAdapter(adapter);

        loadTrips();

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterTrips(s.toString());
            }
        });
    }

    private void loadTrips() {
        long startTime = System.currentTimeMillis();

        Log.d(PERFORMANCE_TAG, "optimized loadTrips STARTED");

        db.collection("users")
                .document(userId)
                .collection("trips")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    tripsList.clear();
                    filteredTripsList.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String placeName = doc.getString("placeName");
                        String tripId = doc.getId();

                        if (placeName != null) {
                            TripItem trip = new TripItem(tripId, placeName);
                            tripsList.add(trip);
                            filteredTripsList.add(trip);
                        }
                    }

                    adapter.updateList(filteredTripsList);

                    long endTime = System.currentTimeMillis();

                    Log.d(
                            PERFORMANCE_TAG,
                            "Optimized trips history loaded. Trips = "
                                    + tripsList.size()
                                    + ", duration = "
                                    + (endTime - startTime)
                                    + " ms"
                    );
                })
                .addOnFailureListener(e -> {
                    long endTime = System.currentTimeMillis();

                    Log.e(
                            PERFORMANCE_TAG,
                            "Optimized trips history loading failed after "
                                    + (endTime - startTime)
                                    + " ms: "
                                    + e.getMessage()
                    );
                });
    }

    private void filterTrips(String query) {
        filteredTripsList.clear();

        String lowerQuery = query.toLowerCase();

        for (TripItem trip : tripsList) {
            if (trip.getPlaceName().toLowerCase().contains(lowerQuery)) {
                filteredTripsList.add(trip);
            }
        }

        adapter.updateList(filteredTripsList);
    }
}