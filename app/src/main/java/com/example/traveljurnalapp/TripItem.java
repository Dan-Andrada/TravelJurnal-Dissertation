package com.example.traveljurnalapp;

public class TripItem {

    private String tripId;
    private String placeName;

    public TripItem(String tripId, String placeName) {
        this.tripId = tripId;
        this.placeName = placeName;
    }

    public String getTripId() {
        return tripId;
    }

    public String getPlaceName() {
        return placeName;
    }
}