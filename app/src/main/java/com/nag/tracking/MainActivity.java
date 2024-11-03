package com.nag.tracking;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Location selectedLocation;
    private LocationCallback locationCallback;
    private LatLng selectedLatLng; // Set this when the user selects a location
    private GeofencingClient geofencingClient;
    private static final int REQUEST_FINE_LOCATION = 1;
    private static final int REQUEST_BACKGROUND_LOCATION = 2;
    private static final int REQUEST_POST_NOTIFICATIONS = 3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        checkPermissions();
    }

    private void checkPermissions() {
        // Request POST_NOTIFICATIONS permission first for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_POST_NOTIFICATIONS);
                return; // Wait for this permission to be granted before proceeding
            }
        }

        // Check FINE_LOCATION permission
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FINE_LOCATION);
            return; // Wait for this permission to be granted before proceeding
        }

        // For Android 10+ (Q and above), check for BACKGROUND_LOCATION permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION);
        }
    }

  /*  private void setMapClickListener() {
        mMap.setOnMapClickListener(latLng -> {
            if (selectedLocation != null) {
                mMap.clear(); // Clear previous markers
            }
            selectedLocation = new Location("");
            selectedLocation.setLatitude(latLng.latitude);
            selectedLocation.setLongitude(latLng.longitude);

            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));

            // Create a geofence around the selected location
            createGeofence();
        });
    }*/

    private void setMapClickListener() {
        mMap.setOnMapClickListener(latLng -> {
            if (selectedLocation != null) {
                mMap.clear(); // Clear previous markers
            }
            selectedLocation = new Location("");
            selectedLocation.setLatitude(latLng.latitude);
            selectedLocation.setLongitude(latLng.longitude);
            selectedLatLng = latLng;  // Set selectedLatLng here

            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));

            // Create a geofence around the selected location
            createGeofence();
        });
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        enableUserLocation();
        setMapClickListener();
    }

    private void enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                        }
                    });
        }
    }

    private void addMarkerOnMap(LatLng latLng) {
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
    }



    private void enableLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        // Request location updates
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000)          // 10 seconds
                .setFastestInterval(5000);    // 5 seconds

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    updateLocationOnMap(new LatLng(location.getLatitude(), location.getLongitude()));
                    checkProximityToSelectedLocation(location); // Optional: Check proximity to selected location
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateLocationOnMap(LatLng latLng) {
        mMap.clear();  // Clear existing markers
        mMap.addMarker(new MarkerOptions().position(latLng).title("Current Location"));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
    }

    @Override
    protected void onPause() {
        super.onPause();
      //  stopLocationTracking();
    }

    private void stopLocationTracking() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            switch (requestCode) {
                case REQUEST_POST_NOTIFICATIONS:
                    // Proceed to check FINE_LOCATION permission
                    checkPermissions();
                    break;

                case REQUEST_FINE_LOCATION:
                    // Proceed to check BACKGROUND_LOCATION if on Android 10+ (Q and above)
                    checkPermissions();
                    break;

                case REQUEST_BACKGROUND_LOCATION:
                    // All necessary permissions are granted
                    enableLocationTracking();
                    break;
            }
        } else {
            // Handle the case where the permission is denied
            Toast.makeText(this, "Required permissions are not granted.", Toast.LENGTH_SHORT).show();
        }
    }



    private void checkProximityToSelectedLocation(Location currentLocation) {
        if (selectedLatLng == null) return; // If no location is selected, do nothing

        // Calculate distance to selected location
        float[] distance = new float[1];
        Location.distanceBetween(
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                selectedLatLng.latitude,
                selectedLatLng.longitude,
                distance
        );

        // Check if the distance exceeds 30 meters
        if (distance[0] > 100) {
            //Log.d("MainActivity", "User has moved out of the 30-meter radius");
Toast.makeText(MainActivity.this,"User has moved out of the 3-meter radius",Toast.LENGTH_LONG).show();
            sendProximityNotification();
        }
    }

    @SuppressLint("MissingPermission")
    private void sendProximityNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "CHANNEL_ID")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Proximity Alert")
                .setContentText("You have crossed the 30-meter radius of the selected location.")
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    private void createNotificationChannel() {
        CharSequence name = "Proximity Alerts";
        String description = "Channel for proximity alerts";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel("CHANNEL_ID", name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void createGeofence() {
        if (selectedLocation != null) {
            GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(new Geofence.Builder()
                            .setRequestId("SelectedLocation")
                            .setCircularRegion(
                                    selectedLocation.getLatitude(),
                                    selectedLocation.getLongitude(),
                                    100
                            )
                            .setExpirationDuration(Geofence.NEVER_EXPIRE)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                            .build())
                    .build();

            PendingIntent geofencePendingIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(this, GeofenceBroadcastReceiver.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this,"Geofence added",Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> Log.e("MapsActivity", "Geofence failed", e));
        }
    }

}