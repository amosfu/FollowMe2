package com.shuai.followme2.bean;

import android.location.Location;

/**
 * Created by Amos on 2017-03-20.
 */

public class GpsTransfer {

    private double lat;
    private double lng;

    public GpsTransfer() {
    }

    public GpsTransfer(Location lastkLocation) {
        this.lat = lastkLocation.getLatitude();
        this.lng = lastkLocation.getLongitude();
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
