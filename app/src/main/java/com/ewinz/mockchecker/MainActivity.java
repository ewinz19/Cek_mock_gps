package com.ewinz.mockchecker;


import android.view.View;
import android.widget.Button;

import java.lang.reflect.Methodfake;
import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    TextView textView;
    Location lastGps = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
//#####
        Button refreshButton = findViewById(R.id.refreshButton);
refreshButton.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Location lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastKnown != null) {
            onLocationChangedManual(lastKnown);
        } else {
            textView.setText("Tidak ada lokasi tersedia. Coba lagi.");
        }
    }
});
//#####

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                boolean isMock = detectMockLocation(location);
                boolean isSuspicious = detectSuspiciousLocation(location);
                List<String> mockApps = getMockApps(MainActivity.this);

                StringBuilder status = new StringBuilder();
                status.append("Latitude: ").append(location.getLatitude())
                        .append("\nLongitude: ").append(location.getLongitude())
                        .append("\nAccuracy: ").append(location.getAccuracy()).append(" m")
                        .append("\nSpeed: ").append(location.getSpeed()).append(" m/s")
                        .append("\nMock Detected: ").append(isMock || isSuspicious ? "⚠️ YES" : "✅ NO");

                if (isSuspicious) {
                    status.append("\n⚠️ Pergerakan/lokasi yang mencurigakan");
                }

                if (!mockApps.isEmpty()) {
                    status.append("\n\nApps with Mock Permission:");
                    for (String app : mockApps) {
                        status.append("\n- ").append(app);
                    }
                } else {
                    status.append("\n\nNo mock-enabled apps found.");
                }

                textView.setText(status.toString());

                if (isMock || isSuspicious) {
                    textView.setBackgroundResource(R.drawable.out_border_red);
                    textView.setTextColor(0xFFFF4444); // merah
                } else {
                    textView.setBackgroundResource(R.drawable.out_border_green);
                    textView.setTextColor(0xFF00FF66); // hijau
                }

                lastGps = location;
            }

            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        });
    }
//########
private void onLocationChangedManual(Location location) {
    boolean isMock = detectMockLocation(location);
    boolean isSuspicious = detectSuspiciousLocation(location);
    List<String> mockApps = getMockApps(MainActivity.this);

    StringBuilder status = new StringBuilder();
    status.append("Latitude: ").append(location.getLatitude())
            .append("\nLongitude: ").append(location.getLongitude())
            .append("\nAccuracy: ").append(location.getAccuracy()).append(" m")
            .append("\nSpeed: ").append(location.getSpeed()).append(" m/s")
            .append("\nMock Detected: ").append(isMock || isSuspicious ? "⚠️ YES" : "✅ NO");

    if (isSuspicious) {
        status.append("\n⚠️ Suspicious movement/location");
    }

    if (!mockApps.isEmpty()) {
        status.append("\n\nApps with Mock Permission:");
        for (String app : mockApps) {
            status.append("\n- ").append(app);
        }
    } else {
        status.append("\n\nNo mock-enabled apps found.");
    }

    textView.setText(status.toString());

    if (isMock || isSuspicious) {
        textView.setBackgroundResource(R.drawable.out_border_red);
        textView.setTextColor(0xFFFF4444); // merah
    } else {
        textView.setBackgroundResource(R.drawable.out_border_green);
        textView.setTextColor(0xFF00FF66); // hijau
    }

    lastGps = location;
}
//########
    private boolean detectMockLocation(Location location) {
        boolean isMock = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Method method = location.getClass().getDeclaredMethod("isMock");
                isMock = (Boolean) method.invoke(location);
            } catch (Exception e) {
                AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,
                        android.os.Process.myUid(), getPackageName());
                boolean hasMockPermission = (mode == AppOpsManager.MODE_ALLOWED);
                boolean hasOtherMockApp = !getAppsWithMockAppOps(this).isEmpty();
                isMock = hasMockPermission || hasOtherMockApp;
            }
        } else {
            isMock = location.isFromMockProvider();
        }

        return isMock;
    }

    private boolean detectSuspiciousLocation(Location location) {
        if (location == null) return false;

        if (lastGps != null) {
            float distance = lastGps.distanceTo(location);
            long timeDiff = (location.getTime() - lastGps.getTime()) / 1000;

            if (timeDiff > 0) {
                float speed = distance / timeDiff;
                if (speed > 100) return true;
            }
        }

        return location.getAccuracy() < 1.0f;
    }

    private List<String> getMockApps(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getAppsWithMockAppOps(context);
        } else {
            List<String> mockApps = new ArrayList<>();
            PackageManager pm = context.getPackageManager();

            for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                try {
                    if (!app.packageName.equals(context.getPackageName())) {
                        int perm = pm.checkPermission("android.permission.ACCESS_MOCK_LOCATION", app.packageName);
                        if (perm == PackageManager.PERMISSION_GRANTED) {
                            CharSequence label = pm.getApplicationLabel(app);
                            mockApps.add((label != null ? label.toString() : app.packageName) + " (" + app.packageName + ")");
                        }
                    }
                } catch (Exception ignored) {}
            }
            return mockApps;
        }
    }

    private List<String> getAppsWithMockAppOps(Context context) {
        List<String> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            try {
                if (!app.packageName.equals(context.getPackageName())) {
                    int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,
                            app.uid, app.packageName);
                    if (mode == AppOpsManager.MODE_ALLOWED) {
                        CharSequence label = pm.getApplicationLabel(app);
                        result.add((label != null ? label.toString() : app.packageName) + " (" + app.packageName + ")");
                    }
                }
            } catch (Exception ignored) {}
        }

        return result;
    }
}
