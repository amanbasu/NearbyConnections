package com.apposite.nearbyconnections;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AppIdentifier;
import com.google.android.gms.nearby.connection.AppMetadata;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener,
        Connections.ConnectionRequestListener,
        Connections.MessageListener,
        Connections.EndpointDiscoveryListener{

    private boolean mIsHost = false;
    GoogleApiClient mGoogleApiClient;
    private static int[] NETWORK_TYPES = {ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET};
    EditText etMessage;
    TextView tvMessage;
    String mOtherEndpointId, mOtherEndpointName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.CONNECTIONS_API)
                .build();

        findViewById(R.id.btnStartAd).setOnClickListener(this);
        findViewById(R.id.btnStartDis).setOnClickListener(this);
        findViewById(R.id.btnSend).setOnClickListener(this);
        findViewById(R.id.btnGallery).setOnClickListener(this);

        etMessage = (EditText) findViewById(R.id.etMessage);
        tvMessage = (TextView) findViewById(R.id.tvMessage);
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    private boolean isConnectedToNetwork() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        for (int networkType : NETWORK_TYPES) {
            NetworkInfo info = connManager.getNetworkInfo(networkType);
            if (info != null && info.isConnectedOrConnecting()) {
                return true;
            }
        }
        return false;
    }

    private void startAdvertising() {
        if (!isConnectedToNetwork()) {
            Toast.makeText(this, "Connect to a wifi network", Toast.LENGTH_SHORT).show();
            return;
        }

        mIsHost = true;

        List<AppIdentifier> appIdentifierList = new ArrayList<>();
        appIdentifierList.add(new AppIdentifier(getPackageName()));
        AppMetadata appMetadata = new AppMetadata(appIdentifierList);

        long NO_TIMEOUT = 0L;

        Nearby.Connections.startAdvertising(mGoogleApiClient, null, appMetadata, NO_TIMEOUT, this)
                .setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
            @Override
            public void onResult(@NonNull Connections.StartAdvertisingResult result) {
                if (result.getStatus().isSuccess()) {
                    Toast.makeText(MainActivity.this, "Device is advertising", Toast.LENGTH_SHORT).show();
                } else {
                    int statusCode = result.getStatus().getStatusCode();
                    if (statusCode != ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING)
                        Toast.makeText(MainActivity.this, "Advertising failed: " + statusCode, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void startDiscovery() {
        if (!isConnectedToNetwork()) {
            Toast.makeText(this, "Connect to a wifi network", Toast.LENGTH_SHORT).show();
            return;
        }

        String serviceId = getString(R.string.service_id);

        long DISCOVER_TIMEOUT = 5000L;

        Nearby.Connections.startDiscovery(mGoogleApiClient, serviceId, DISCOVER_TIMEOUT, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Toast.makeText(MainActivity.this, "Device is discovering", Toast.LENGTH_SHORT).show();
                        } else {
                            int statusCode = status.getStatusCode();
                            if (statusCode != ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING)
                                Toast.makeText(MainActivity.this, "Discovering failed: " + statusCode, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    //Invoked in host's device
    @Override
    public void onConnectionRequest(final String endpointId, String deviceId, final String endpointName, byte[] bytes) {
        Toast.makeText(this, "Device " + endpointName + " Found.", Toast.LENGTH_SHORT).show();
        AlertDialog mConnectionRequestDialog = new AlertDialog.Builder(this)
                .setTitle("Connection Request")
                .setMessage("Do you want to connect to " + endpointName + "?")
                .setCancelable(false)
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Nearby.Connections.acceptConnectionRequest(mGoogleApiClient, endpointId, null, MainActivity.this)
                                .setResultCallback(new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(@NonNull Status status) {
                                        if (status.isSuccess()) {
                                            mOtherEndpointId = endpointId;
                                            mOtherEndpointName = endpointName;
                                            Toast.makeText(MainActivity.this, "Connected to " + endpointName, Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(MainActivity.this, "Can't Connect to " + endpointName, Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Nearby.Connections.rejectConnectionRequest(mGoogleApiClient, endpointId);
                    }
                }).create();
        mConnectionRequestDialog.show();
    }

    //Invoked in client's device
    @Override
    public void onEndpointFound(String endpointId, final String deviceId, final String serviceId, final String endpointName) {
        Toast.makeText(this, "Device " + endpointName + " Found.", Toast.LENGTH_SHORT).show();
        Nearby.Connections.sendConnectionRequest(mGoogleApiClient, null, endpointId, null, new Connections.ConnectionResponseCallback() {
            @Override
            public void onConnectionResponse(String endpointId, Status status, byte[] bytes) {
                if (status.isSuccess()) {
                    Toast.makeText(MainActivity.this, "Connected to " + endpointName, Toast.LENGTH_SHORT).show();
                    mOtherEndpointId = endpointId;
                    mOtherEndpointName = endpointName;
                } else {
                    Toast.makeText(MainActivity.this, "Can't connect to " + endpointName, Toast.LENGTH_SHORT).show();
                }
            }
        }, this);
    }

    private void sendMessage(byte[] payload) {
        //Toast.makeText(MainActivity.this, "Sending message to " + mOtherEndpointName, Toast.LENGTH_SHORT).show();
        //String msg = etMessage.getText().toString();
        Nearby.Connections.sendReliableMessage(mGoogleApiClient, mOtherEndpointId, payload);
        etMessage.setText(null);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.btnStartAd:
                startAdvertising();
                break;
            case R.id.btnStartDis:
                startDiscovery();
                break;
            case R.id.btnSend:
                sendMessage(etMessage.getText().toString().getBytes());
                break;
            case R.id.btnGallery:
                openGallery();
                break;
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, 20);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            Uri uri = data.getData();
            final byte [] arrayStream = new byte [2048];

            ContentResolver cr = this.getContentResolver();
            InputStream file = cr.openInputStream(uri);
            int bytesRead = file.read(arrayStream);
            while (bytesRead!=-1) {
                sendMessage(arrayStream);
//                new Thread(new Runnable() {
//                    public void run() {
//                        sendMessage(arrayStream);
//                    }
//                }).start();
                System.out.println(new String(arrayStream));
                bytesRead = file.read(arrayStream);
            }
            System.out.println("File sent successfully.");
        } catch(Exception e){
            Toast.makeText(this, "Exception",Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //Toast.makeText(this, "Connected.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.reconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection Failed.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEndpointLost(String s) {
        Toast.makeText(this, "Endpoint Lost.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMessageReceived(String endpoint, byte[] payload, boolean b) {
        String message = new String(payload);
        Toast.makeText(this, "Receiving message...", Toast.LENGTH_SHORT).show();
        if(message.length()<20)
            tvMessage.setText(message);
        else
            System.out.println(message);
    }

    @Override
    public void onDisconnected(String s) {
        Toast.makeText(this, "Disconnected. " + s, Toast.LENGTH_SHORT).show();
    }
}