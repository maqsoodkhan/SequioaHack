/*
 * Copyright (C) 2014 Thalmic Labs Inc.
 * Distributed under the Myo SDK license agreement. See LICENSE.txt for details.
 */

package sequoia.hack;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.gsm.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.RecognitionResult;
import com.clarifai.api.Tag;
import com.example.android.camera2basic.R;
import com.thalmic.myo.AbstractDeviceListener;
import com.thalmic.myo.Arm;
import com.thalmic.myo.DeviceListener;
import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;
import com.thalmic.myo.Pose;
import com.thalmic.myo.Quaternion;
import com.thalmic.myo.XDirection;
import com.thalmic.myo.scanner.ScanActivity;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements View.OnClickListener, TextToSpeech.OnInitListener {

    private TextView mLockStateView;
    private TextView mTextView;
    private Button mBtnScan;
    private ProgressDialog mProgressDialog;
    private EditText mPhoneNumField;
    private EditText mAddressField;
    // Classes that inherit from AbstractDeviceListener can be used to receive events from Myo devices.
    // If you do not override an event, the default behavior is to do nothing.
    private DeviceListener mListener = new AbstractDeviceListener() {

        // onConnect() is called whenever a Myo has been connected.
        @Override
        public void onConnect(Myo myo, long timestamp) {
            // Set the text color of the text view to cyan when a Myo connects.
            mTextView.setTextColor(Color.CYAN);
        }

        // onDisconnect() is called whenever a Myo has been disconnected.
        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            // Set the text color of the text view to red when a Myo disconnects.
            mTextView.setTextColor(Color.RED);
        }

        // onArmSync() is called whenever Myo has recognized a Sync Gesture after someone has put it on their
        // arm. This lets Myo know which arm it's on and which way it's facing.
        @Override
        public void onArmSync(Myo myo, long timestamp, Arm arm, XDirection xDirection) {
            mTextView.setText(myo.getArm() == Arm.LEFT ? R.string.arm_left : R.string.arm_right);
        }

        // onArmUnsync() is called whenever Myo has detected that it was moved from a stable position on a person's arm after
        // it recognized the arm. Typically this happens when someone takes Myo off of their arm, but it can also happen
        // when Myo is moved around on the arm.
        @Override
        public void onArmUnsync(Myo myo, long timestamp) {
            mTextView.setText(R.string.hello_world);
        }

        // onUnlock() is called whenever a synced Myo has been unlocked. Under the standard locking
        // policy, that means poses will now be delivered to the listener.
        @Override
        public void onUnlock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.unlocked);
        }

        // onLock() is called whenever a synced Myo has been locked. Under the standard locking
        // policy, that means poses will no longer be delivered to the listener.
        @Override
        public void onLock(Myo myo, long timestamp) {
            mLockStateView.setText(R.string.locked);
        }

        // onOrientationData() is called whenever a Myo provides its current orientation,
        // represented as a quaternion.
        @Override
        public void onOrientationData(Myo myo, long timestamp, Quaternion rotation) {

            // Calculate Euler angles (roll, pitch, and yaw) from the quaternion.
            /*float roll = (float) Math.toDegrees(Quaternion.roll(rotation));
            float pitch = (float) Math.toDegrees(Quaternion.pitch(rotation));
            float yaw = (float) Math.toDegrees(Quaternion.yaw(rotation));

            // Adjust roll and pitch for the orientation of the Myo on the arm.
            if (myo.getXDirection() == XDirection.TOWARD_ELBOW) {
                roll *= -1;
                pitch *= -1;
            }*/

            // Next, we apply a rotation to the text view using the roll, pitch, and yaw.
            /*mTextView.setRotation(roll);
            mTextView.setRotationX(pitch);
            mTextView.setRotationY(yaw);*/
        }

        // onPose() is called whenever a Myo provides a new pose.
        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            // Handle the cases of the Pose enumeration, and change the text of the text view
            // based on the pose we receive.
            switch (pose) {
                case UNKNOWN:
                    System.out.println("************ UNKNOWN : " + pose.toString());
                    mTextView.setText(getString(R.string.hello_world));
                    break;
                case REST:
                    //System.out.println("************ REST : "+pose.toString());
                case DOUBLE_TAP:
                    System.out.println("************ DOUBLE_TAP : " + pose.toString());
                    int restTextId = R.string.hello_world;
                    switch (myo.getArm()) {
                        case LEFT:
                            System.out.println("************ LEFT : " + pose.toString());
                            restTextId = R.string.arm_left;
                            break;
                        case RIGHT:
                            System.out.println("************ RIGHT : " + pose.toString());
                            restTextId = R.string.arm_right;
                            break;
                    }
                    mTextView.setText(getString(restTextId));
                    break;
                case FIST:
                    System.out.println("************ FIST : " + pose.toString());
                    takePicture();
                    mTextView.setText(getString(R.string.pose_fist));
                    break;
                case WAVE_IN:
                    System.out.println("************ WAVE_IN : " + pose.toString());
                    sendSMS();
                    mTextView.setText(getString(R.string.pose_wavein));
                    break;
                case WAVE_OUT:
                    System.out.println("************ WAVE_OUT : " + pose.toString());
                    mTextView.setText(getString(R.string.pose_waveout));
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        checkCallLocationPermission();
                    }
                    Intent dialIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + mPhoneNumField.getText().toString()));
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    startActivity(dialIntent);
                    break;
                case FINGERS_SPREAD:
                    System.out.println("************ FINGERS_SPREAD : " + pose.toString());
                    speakOut("your uber has been booked from current location to " + mAddressField.getText().toString());
                    mTextView.setText(getString(R.string.pose_fingersspread));
                    break;
            }

            if (pose != Pose.UNKNOWN && pose != Pose.REST) {
                // Tell the Myo to stay unlocked until told otherwise. We do that here so you can
                // hold the poses without the Myo becoming locked.
                myo.unlock(Myo.UnlockType.HOLD);

                // Notify the Myo that the pose has resulted in an action, in this case changing
                // the text on the screen. The Myo will vibrate.
                myo.notifyUserAction();
            } else {
                // Tell the Myo to stay unlocked only for a short period. This allows the Myo to
                // stay unlocked while poses are being performed, but lock after inactivity.
                myo.unlock(Myo.UnlockType.TIMED);
            }
        }
    };

    String CAMERA_FRAGMENT_TAG = "Camera";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
            checkSMSPermission();
        }
        tts = new TextToSpeech(this, this);
        mProgressDialog = new ProgressDialog(this);
        mPhoneNumField = (EditText) findViewById(R.id.et_phone_num);
        mAddressField = (EditText) findViewById(R.id.et_address);
        mBtnScan = (Button) findViewById(R.id.btn_scan);
        mBtnScan.setOnClickListener(this);

        mLockStateView = (TextView) findViewById(R.id.lock_state);
        mTextView = (TextView) findViewById(R.id.lock_state);


        getFragmentManager().beginTransaction()
                .replace(R.id.container_id, CameraViewFragment.newInstance(), CAMERA_FRAGMENT_TAG)
                .commit();

        // First, we initialize the Hub singleton with an application identifier.
        Hub hub = Hub.getInstance();
        if (!hub.init(this, getPackageName())) {
            // We can't do anything with the Myo device if the Hub can't be initialized, so exit.
            Toast.makeText(this, "Couldn't initialize Hub", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Next, register for DeviceListener callbacks.
        hub.addListener(mListener);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tts.shutdown();
        // We don't want any callbacks when the Activity is gone, so unregister the listener.
        Hub.getInstance().removeListener(mListener);

        if (isFinishing()) {
            // The Activity is finishing, so shutdown the Hub. This will disconnect from the Myo.
            Hub.getInstance().shutdown();
        }
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.btn_scan:
                Intent intent = new Intent(this, ScanActivity.class);
                startActivity(intent);
                break;

        }
    }

    private void takePicture() {
        mProgressDialog.show();
        mProgressDialog.setMessage("Please Wait....");
        android.app.FragmentManager fragmentManager = getFragmentManager();
        CameraViewFragment cameraTest = (CameraViewFragment) fragmentManager.findFragmentByTag(CAMERA_FRAGMENT_TAG);
        cameraTest.takePicture();
    }


    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    public boolean checkSMSPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.SEND_SMS)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.SEND_SMS},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.SEND_SMS},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }


    public void onSaveImageSuccess() {

        try{
            final Handler handler = new Handler();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    Toast.makeText(getApplicationContext(), "onSaveImageSuccess", Toast.LENGTH_SHORT).show();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            File file = new File(getExternalFilesDir(null), "pic.jpg");

                            ClarifaiClient clarifai = new ClarifaiClient("ZjKS4N7bl5aVH42OPYVPrc6hR8nwbgyBb2PUHRYG", "hkXfptK0LtD_G6OS4EEt6tJMgLPU4Zbv31azexKB");
                            List<RecognitionResult> results =
                                    clarifai.recognize(new RecognitionRequest(file));

                            String top_5_tags = "These are the tags with respect to the image";

                            int i = 0;
                            for (Tag tag : results.get(0).getTags()) {
                                i++;
                                if (i <= 12) {
                                    top_5_tags = top_5_tags + tag.getName();
                                }

                                System.out.println("************** TAGS :" + tag.getName() + ": " + tag.getProbability());
                            }
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mProgressDialog.dismiss();
                                }
                            });
                            speakOut(top_5_tags);
                        }
                    }).start();
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void postData(final File file) {
        final String url = "http://b8bbcd3e.ngrok.io/vision/upload";
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpConnection httpURLConnection = new HttpConnection();
                try {
                    httpURLConnection.multipartRequest(url, "", file, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public boolean checkCallLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.CALL_PHONE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CALL_PHONE},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.CALL_PHONE},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    private TextToSpeech tts;

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {

            int result = tts.setLanguage(Locale.US);

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            } else {
                //speakOut("Hello");
            }

        } else {
            Log.e("TTS", "Initilization Failed!");
        }
    }


    private void speakOut(String speach) {
        tts.speak(speach, TextToSpeech.QUEUE_FLUSH, null);
    }

    public void sendSMS() {
        SmsManager.getDefault().sendTextMessage(mPhoneNumField.getText().toString(), null, "Please contact me as it's very urgent", null, null);
    }
}
