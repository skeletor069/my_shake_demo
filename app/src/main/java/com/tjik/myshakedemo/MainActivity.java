package com.tjik.myshakedemo;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import static java.lang.Math.sqrt;
import static android.location.LocationManager.GPS_PROVIDER;


public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    FirebaseDatabase database;
    DatabaseReference usersRef;
    DatabaseReference shakeOriginRef;
    DatabaseReference shakeSubscriberRef;
    SharedPreferences defaultPref;

    TextView childCountText;
    Button shakeButton;

    String myId = "";
    long demoLongitude = 100;
    long demoLatitude = 100;
    long maximumRadius = 100;


    LocationManager lm;
    private double xAxis, yAxis, zAxis,magnitude;
    private boolean mInitialized;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private final float NOISE = (float) 2.0;

    ArrayList<AccelerometerViewData> xyzData = new ArrayList<AccelerometerViewData>();
    CustomView accelerometerView;
    FFTView fftView;

    //example variables
    private double[] freqCounts;
    private double[] magnituedFFT;
    private int magnitudeCounter = 0;


    SeekBar sampleRateChanger;
    private int sampleRate = 2000;

    SeekBar windowSizeChanger;
    private int wsize = 32;

    private float locationSpeed = 0.0f;


    private TextView sampleSizeText;
    private TextView windowSizeText;

    private float globalPeak = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accelerometerView = (CustomView) findViewById(R.id.xyzView);
        //fftView = (FFTView) findViewById(R.id.fftView);
        magnituedFFT = new double[wsize];

        // https://stackoverflow.com/questions/40740933/setting-timer-with-seek-bar
        //sampleRateChanger = (SeekBar) findViewById(R.id.seekBarSampleData);
        //sampleRateChanger.setMax(80000);
        //sampleRateChanger.setProgress(2000);


        //windowSizeChanger = (SeekBar) findViewById(R.id.seekBarWindowSize);
        //windowSizeChanger.setMax(2048);
        //windowSizeChanger.setProgress(wsize);

        mInitialized = false;
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mSensorManager.registerListener(this, mAccelerometer, sampleRate);

        //sampleSizeText = (TextView) findViewById(R.id.sampleSize);
        //windowSizeText = (TextView) findViewById(R.id.windowSize);
        //sampleSizeText.setText("Sample Size: " + sampleRate);
        //windowSizeText.setText("Window Size: " + wsize);

        childCountText = (TextView) findViewById(R.id.numChildren);
        shakeButton = (Button) findViewById(R.id.shakingBtn);
        shakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // check if there is any entry in primary shake
                //
                // if not found
                //     entry in the primary shake with origin m
                //
                // entry in the secondary shake with the origin m id of primary shake
                // start checking secondary shakes for origin m to reach count n

                shakeOriginRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if(dataSnapshot.getChildrenCount() > 0){
                            // somewhere in the world, earthquake is happening. Check which one is close to me and am I in the zone
                            long nearestDistance = 999999999;
                            ShakeOrigin shakeOrigin = null;
                            for(DataSnapshot originSnapshot:dataSnapshot.getChildren()){
                                ShakeOrigin temp = originSnapshot.getValue(ShakeOrigin.class);
                                long tempDist = temp.distanceFromOrigin(demoLongitude, demoLatitude);
                                if(tempDist < nearestDistance){
                                    shakeOrigin = temp;
                                    nearestDistance = tempDist;
                                }
                            }

                            if(nearestDistance < maximumRadius){
                                // we found an origin which is within the zone
                            }else{
                                // the origins are too far. so create new shake origin
                            }

                        }else{
                            // there are no origins right now. Create new shake origin
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        });

        database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("users");
        shakeOriginRef = database.getReference("shakeOrigin");
        shakeSubscriberRef = database.getReference("shakeSubscriber");
        defaultPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if(defaultPref.contains("myId")) {
            myId = defaultPref.getString("myId", "");
            childCountText.setText("Welcome " + myId);
        }
        else{
            usersRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    long count = dataSnapshot.getChildrenCount();
                    myId = "user" + count;
                    childCountText.setText(myId);
                    usersRef.removeEventListener(this);
                    usersRef.child(myId).setValue(myId);
                    SharedPreferences.Editor editor = defaultPref.edit();
                    editor.putString("myId", myId);
                    editor.commit();
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }

        lm = (LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        lm.requestLocationUpdates(GPS_PROVIDER,0,0,this);
        //this.onLocationChanged(null);
        this.onLocationChanged(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for(String permission: permissions){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, permission)){
                //denied
                Toast.makeText(this,"Location Access permission denied. Can't read the location speed.", Toast.LENGTH_LONG).show();

            }else{
                if(ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED){
                    //allowed
                    //startupScreen.setVisibility(View.GONE);
                    Toast.makeText(this,"Location Access permission granted.", Toast.LENGTH_SHORT).show();

                } else{
                    //set to never ask again
                    Toast.makeText(this,"Go to settings and accept location access permission.", Toast.LENGTH_LONG).show();
                    //do something here.
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        // unregister listener
        super.onPause();
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            getAccelerometer(sensorEvent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }


    private void getAccelerometer(SensorEvent sensorEvent) {

        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];
        long timestamp = System.currentTimeMillis();
        if (!mInitialized) {
            xAxis = x;
            yAxis = y;
            zAxis = z;
            magnitude = (double) Math.sqrt(xAxis*xAxis + yAxis*yAxis + zAxis*zAxis);
            mInitialized = true;

        } else {
            double deltaX = Math.abs(xAxis - x);
            double deltaY = Math.abs(yAxis - y);
            double deltaZ = Math.abs(zAxis - z);

            xAxis = x;
            yAxis = y;
            zAxis = z;
            magnitude = (double) Math.sqrt(xAxis*xAxis + yAxis*yAxis + zAxis*zAxis);
        }

        xyzData.add(new AccelerometerViewData((float) xAxis, (float) yAxis, (float) zAxis));
        if (xyzData.size() > 100){
            xyzData.remove(0);
        }
        accelerometerView.SetAccelerometerData(xyzData);


        magnituedFFT[magnitudeCounter] = magnitude;
        magnitudeCounter++;

        if(magnitudeCounter == wsize) {
            new FFTAsynctask(wsize).execute(magnituedFFT);
            magnitudeCounter = 0;
            magnituedFFT = new double[wsize];
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if(location == null){
            locationSpeed = 0.0f;
        }else {
            locationSpeed = location.getSpeed();
        }

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /**
     * Implements the fft functionality as an async task
     * FFT(int n): constructor with fft length
     * fft(double[] x, double[] y)
     */

    private class FFTAsynctask extends AsyncTask<double[], Void, double[]> {

        private int wsize; /* window size must be power of 2 */

        // constructor to set window size
        FFTAsynctask(int wsize) {
            this.wsize = wsize;
        }

        @Override
        protected double[] doInBackground(double[]... values) {


            double[] realPart = values[0].clone(); // actual acceleration values
            double[] imagPart = new double[wsize]; // init empty

            /**
             * Init the FFT class with given window size and run it with your input.
             * The fft() function overrides the realPart and imagPart arrays!
             */
            FFT fft = new FFT(wsize);
            fft.fft(realPart, imagPart);
            //init new double array for magnitude (e.g. frequency count)
            double[] magnitude = new double[wsize];

            //fill array with magnitude values of the distribution
            for (int i = 0; i < wsize ; i++) {
                magnitude[i] = sqrt(Math.pow(realPart[i], 2) + Math.pow(imagPart[i], 2));
                // Log.d("fft", magnitude[i] + "");

            }

            return magnitude;

        }

        @Override
        protected void onPostExecute(double[] values) {
            //hand over values to global variable after background task is finished

            freqCounts = values;
            //fftView.SetFFTData(values);


            double peakValue = 0;

            for (int i = 0; i< wsize; i++){
                if(freqCounts[i] > peakValue){
                    peakValue = freqCounts[i];
                }
            }
            sendShakeData(peakValue);
        }
    }

    public void sendShakeData(double peakValue){

    }
}
