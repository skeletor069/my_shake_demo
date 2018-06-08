package com.tjik.myshakedemo;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
    }


}
