
package com.soham.photolocationshare;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnTakePic, btnShare;
    private ImageView ivPic;
    private TextView tvLocation;

    private Bitmap photo;
    private String locationText = "";

    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<Void> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicturePreview(),
                    bitmap -> {
                        if (bitmap != null) {
                            photo = bitmap;
                            ivPic.setImageBitmap(bitmap);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnTakePic = findViewById(R.id.btnTakePic);
        btnShare = findViewById(R.id.btnShare);
        ivPic = findViewById(R.id.ivPic);
        tvLocation = findViewById(R.id.tvLocation);

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        btnTakePic.setOnClickListener(v ->
                cameraLauncher.launch(null));

        btnShare.setOnClickListener(v -> sharePhoto());

        getLocation();
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    100
            );
            return;
        }

        tvLocation.setText("Fetching location...");

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        showAddress(location);
                    } else {
                        tvLocation.setText(
                                "Location unavailable. Enable GPS and try again."
                        );
                    }
                })
                .addOnFailureListener(e -> {
                    tvLocation.setText("Unable to get location.");
                    Toast.makeText(
                            this,
                            "Location error: " + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void showAddress(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        locationText = "https://maps.google.com/?q="
                + latitude + "," + longitude;

        tvLocation.setText(
                "Latitude: " + latitude
                        + "\nLongitude: " + longitude
                        + "\nGetting address..."
        );

        Geocoder geocoder = new Geocoder(
                this, Locale.getDefault());

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(
                    latitude, longitude, 1,
                    new Geocoder.GeocodeListener() {
                        @Override
                        public void onGeocode(
                                @NonNull List<Address> addresses) {
                            runOnUiThread(() ->
                                    displayAddress(addresses, latitude, longitude));
                        }

                        @Override
                        public void onError(
                                @NonNull String errorMessage) {
                            runOnUiThread(() ->
                                    displayCoordinates(latitude, longitude));
                        }
                    });
        } else {
            new Thread(() -> {
                try {
                    List<Address> addresses =
                            geocoder.getFromLocation(
                                    latitude, longitude, 1);

                    runOnUiThread(() ->
                            displayAddress(addresses, latitude, longitude));

                } catch (IOException e) {
                    runOnUiThread(() ->
                            displayCoordinates(latitude, longitude));
                }
            }).start();
        }
    }

    private void displayAddress(
            List<Address> addresses,
            double latitude,
            double longitude) {

        if (addresses != null && !addresses.isEmpty()) {
            String address = addresses.get(0).getAddressLine(0);

            tvLocation.setText(
                    address
                            + "\nLatitude: " + latitude
                            + "\nLongitude: " + longitude);
        } else {
            displayCoordinates(latitude, longitude);
        }
    }

    private void displayCoordinates(
            double latitude, double longitude) {

        tvLocation.setText(
                "Latitude: " + latitude
                        + "\nLongitude: " + longitude
                        + "\n" + locationText);
    }

    private void sharePhoto() {
        if (photo == null) {
            Toast.makeText(
                    this, "Take a photo first!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (locationText.isEmpty()) {
            Toast.makeText(
                    this, "Location is not available yet.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        File imageFile = new File(getCacheDir(), "photo.png");

        try (FileOutputStream outputStream =
                     new FileOutputStream(imageFile)) {

            photo.compress(
                    Bitmap.CompressFormat.PNG, 100, outputStream);

            Uri imageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    imageFile);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, imageUri);
            intent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Photo Location:\n" + locationText);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(
                    intent, "Share Photo & Location"));

        } catch (IOException e) {
            Toast.makeText(
                    this,
                    "Unable to share photo: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0
                    && (grantResults[0]
                    == PackageManager.PERMISSION_GRANTED
                    || (grantResults.length > 1
                    && grantResults[1]
                    == PackageManager.PERMISSION_GRANTED))) {
                getLocation();
            } else {
                tvLocation.setText("Location permission denied.");
            }
        }
    }
}
