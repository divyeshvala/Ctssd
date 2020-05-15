package com.example.ctssd.Utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.example.ctssd.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static android.content.Context.LOCATION_SERVICE;

public class GetLocation
{
    private Context mContext;
    private FusedLocationProviderClient fusedLocationProviderClient;

    public GetLocation(Context context, FusedLocationProviderClient fusedLocationProviderClient) {
        this.mContext = context;
        this.fusedLocationProviderClient = fusedLocationProviderClient;
    }

    public void findLocation()
    {
        Log.i("Location", "inside get location");
        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(mContext), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.i("Location", "permission is there");
            fusedLocationProviderClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Location location = task.getResult();
                    Log.i("Location", "inside listener");
                    if (location != null)
                    {
                        Log.i("Location", "location is not null");
                        getAddress(location);
                    } else {
                        Log.i("Loc", "location is null");
                        requestNewLocationData();
                    }
                }
            });
        } else {
            Log.i("Location", "permission is not there");
            Intent intent = new Intent("GET_LOCATION_PERMISSION");
            mContext.sendBroadcast(intent);
        }
    }

    private void requestNewLocationData()
    {
        if (!isLocationEnabled_Network() && !isLocationEnabled_GPS())
        {
            Intent intent = new Intent("GPS_PERMISSION");
            mContext.sendBroadcast(intent);
        }
        else {
            LocationRequest mLocationRequest = new LocationRequest();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            //mLocationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
            mLocationRequest.setInterval(0);
            mLocationRequest.setFastestInterval(0);
            mLocationRequest.setNumUpdates(1);
            Log.i("Loc", "looper");
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(Objects.requireNonNull(mContext));
            fusedLocationProviderClient.requestLocationUpdates(
                    mLocationRequest, mLocationCallback,
                    Looper.myLooper()
            );
        }
    }

    private LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Log.i("Loc", "onLocResult");
            Location mLastLocation = locationResult.getLastLocation();
            if (mLastLocation!=null)
            {
                getAddress(mLastLocation);
            }
            else
            {
                requestNewLocationData();
            }
        }
    };

    private void getAddress(Location location)
    {
        try {
            Geocoder geocoder = new Geocoder(mContext,
                    Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(),
                    1
            );

            final String cityName = addresses.get(0).getLocality();

            if(cityName==null)
            {
                Intent intent = new Intent("ENABLE_GPS");
                mContext.sendBroadcast(intent);
            }
            final String area = addresses.get(0).getAdminArea();

            // check if any of this is in hotspot list
            DatabaseReference dataRef = FirebaseDatabase.getInstance().getReference().child("hotspots");
            dataRef.child("lists").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                {
                    int zoneVar = 0;
                    if(dataSnapshot.exists())
                    {
                        String red = dataSnapshot.child("red").getValue(String.class);
                        String orange = dataSnapshot.child("orange").getValue(String.class);
                        if(red!=null && (red.contains(cityName) || red.contains(area)))
                        {
                            zoneVar = 2;
                        }
                        else if(orange!=null && (orange.contains(cityName) || orange.contains(area)))
                        {
                            zoneVar = 1;
                        }
                    }
                    Intent intent = new Intent("LOCATION_FOUND");
                    intent.putExtra("zoneVar", zoneVar);
                    intent.putExtra("cityName", cityName);
                    mContext.sendBroadcast(intent);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            });
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private boolean isLocationEnabled_Network() {
        LocationManager locationManager = (LocationManager) Objects.requireNonNull(mContext).getSystemService(LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
    private boolean isLocationEnabled_GPS() {
        LocationManager locationManager = (LocationManager) Objects.requireNonNull(mContext).getSystemService(LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
}
