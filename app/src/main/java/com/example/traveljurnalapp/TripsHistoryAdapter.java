package com.example.traveljurnalapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TripsHistoryAdapter extends RecyclerView.Adapter<TripsHistoryAdapter.TripViewHolder> {

    private final Context context;
    private List<TripItem> trips;

    public TripsHistoryAdapter(Context context, List<TripItem> trips) {
        this.context = context;
        this.trips = trips;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView textView = new TextView(context);
        textView.setTextSize(18);
        textView.setTextColor(context.getResources().getColor(android.R.color.black));
        textView.setPadding(20, 30, 20, 30);
        return new TripViewHolder(textView);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        TripItem trip = trips.get(position);
        holder.textView.setText("📍 " + trip.getPlaceName());

        holder.textView.setOnClickListener(v -> {
            Intent intent = new Intent(context, TripDetailsActivity.class);
            intent.putExtra("tripId", trip.getTripId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    public void updateList(List<TripItem> filteredTrips) {
        this.trips = filteredTrips;
        notifyDataSetChanged();
    }

    public static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }
}