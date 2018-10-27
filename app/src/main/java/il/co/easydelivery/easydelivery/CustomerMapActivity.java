package il.co.easydelivery.easydelivery;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private Button mLogout, mRequest, mSettings;
    private LatLng pickupLocation;
    private Boolean requestBol = false;
    private Marker pickupMarker;
    private String destination;
    private LinearLayout mCourierInfo;
    private ImageView mCourierProfileImage;
    private TextView mCourierName, mCourierPhone, mCourierCar;
    private PlaceAutocompleteFragment autocompleteFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout = (Button) findViewById(R.id.Logout);
        mRequest = (Button) findViewById(R.id.Request);
        mSettings = (Button) findViewById(R.id.Settings);
        mCourierInfo = (LinearLayout) findViewById(R.id.CourierInfo);
        mCourierProfileImage = (ImageView) findViewById(R.id.CourierProfileImage);
        mCourierName = (TextView) findViewById(R.id.CourierName);
        mCourierPhone = (TextView) findViewById(R.id.CourierPhone);
        mCourierCar = (TextView) findViewById(R.id.CourierCar);

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("CustomerRequest");
                GeoFire geoFire = new GeoFire(ref);

                if (requestBol) {
                    requestBol = false;
                    geoQuery.removeAllListeners();
                    courierLocationRef.removeEventListener(courierLocationRefListener);
                    if (courierFoundId != null) {
                        DatabaseReference courierRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Couriers").child(courierFoundId).child("customerRequest");
                        courierRef.removeValue();
                        courierFoundId = null;
                    }
                    courierFound = false;
                    radius = 1;
                    geoFire.removeLocation(userId);

                    if (pickupMarker != null) {
                        pickupMarker.remove();
                    }

                    if (mCourierMarker != null) {
                        mCourierMarker.remove();
                    }

                    mCourierInfo.setVisibility(View.GONE);
                    mCourierName.setText("");
                    mCourierPhone.setText("");
                    mCourierCar.setText("");
                    mCourierProfileImage.setImageResource(R.mipmap.ic_profile_image);

                    autocompleteFragment.setMenuVisibility(true);
                    mRequest.setText("הזמן שליחות");
                } else {
                    requestBol = true;
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                    pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title("איסוף מכאן!").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pickup)));
                    mRequest.setText("מחפש שליח...");

                    getClosestCourier();
                }
            }
        });

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        autocompleteFragment.setMenuVisibility(true);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                destination = place.getName().toString();
            }

            @Override
            public void onError(Status status) {

            }
        });
    }

    private int radius = 1; //in KM
    private Boolean courierFound = false;
    private String courierFoundId;
    private GeoQuery geoQuery;

    private void getClosestCourier() {
        DatabaseReference courierLocation = FirebaseDatabase.getInstance().getReference().child("CourierAvailable");

        GeoFire geoFire = new GeoFire(courierLocation);

        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!courierFound && requestBol) {
                    courierFound = true;
                    courierFoundId = key;

                    DatabaseReference courierRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Couriers").child(courierFoundId).child("customerRequest");
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerId);
                    map.put("destination", destination);
                    courierRef.updateChildren(map);

                    getCourierLocation();
                    getCourierInfo();
                    mRequest.setText("מוודא את מיקומו של השליח...");
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
            }

            @Override
            public void onGeoQueryReady() {
                if(!courierFound)
                {
                    radius++;
                    getClosestCourier();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private Marker mCourierMarker;
    private DatabaseReference courierLocationRef;
    private ValueEventListener courierLocationRefListener;

    private void getCourierLocation() {
        courierLocationRef = FirebaseDatabase.getInstance().getReference().child("CouriersWorking").child(courierFoundId).child("l");
        courierLocationRefListener = courierLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && requestBol)
                {
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    mRequest.setText("מצאנו שליח עבורך!");
                    if(map.get(0) != null) {
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }

                    if(map.get(0) != null) {
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng courierLatLng = new LatLng(locationLat,locationLng);
                    if(mCourierMarker != null) {
                        mCourierMarker.remove();
                    }

                    Location pickupLocation = new Location("");
                    pickupLocation.setLatitude(mLastLocation.getLatitude());
                    pickupLocation.setLongitude(mLastLocation.getLongitude());

                    Location courierLocation = new Location("");
                    courierLocation.setLatitude(courierLatLng.latitude);
                    courierLocation.setLongitude(courierLatLng.longitude);

                    float distance = pickupLocation.distanceTo(courierLocation);

                    if(distance < 100){
                        mRequest.setText("השליח כאן!");
                    }
                    else {
                        if(distance < 1000)
                            mRequest.setText("נמצא שליח במרחק של " + String.valueOf((int)distance) + " מטר");
                        else
                            mRequest.setText("נמצא שליח במרחק של " + String.valueOf((int)distance / 1000) + " ק\"מ");
                    }
                    mCourierMarker = mMap.addMarker(new MarkerOptions().position(courierLatLng).title("השליח שלך").icon(BitmapDescriptorFactory.fromResource(R.mipmap.car_delivery)));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getCourierInfo(){

        DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Couriers").child(courierFoundId);
        mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0){
                    Map<String, Object> map = (Map<String, Object>)dataSnapshot.getValue();
                    if(map.get("name") != null) {
                        mCourierName.setText(map.get("name").toString());
                    }
                    if(map.get("phone") != null) {
                        mCourierPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("car") != null) {
                        mCourierCar.setText(map.get("car").toString());
                    }
                    if(map.get("profileUrl") != null) {
                        Glide.with(getApplication()).load(map.get("profileUrl").toString()).into(mCourierProfileImage);
                    }
                    autocompleteFragment.setMenuVisibility(false);
                    mCourierInfo.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 23) { // Marshmallow

                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1252);
            }
            return;
        }
        BuildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void BuildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        if(getApplicationContext() != null) {
            mLastLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 23) { // Marshmallow

                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1252);
            }
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
