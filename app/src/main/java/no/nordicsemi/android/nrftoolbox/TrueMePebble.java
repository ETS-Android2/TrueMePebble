package no.nordicsemi.android.nrftoolbox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;

import no.nordicsemi.android.ble.BleManagerCallbacks;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LocalLogSession;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.nrftoolbox.bpm.BPMManager;
import no.nordicsemi.android.nrftoolbox.hr.HRManager;
import no.nordicsemi.android.nrftoolbox.profile.BleProfileActivity;
import no.nordicsemi.android.nrftoolbox.profile.BleProfileExpandableListActivity;
import no.nordicsemi.android.nrftoolbox.profile.LoggableBleManager;
import no.nordicsemi.android.nrftoolbox.uart.UARTActivity;
import no.nordicsemi.android.nrftoolbox.uart.UARTInterface;
import no.nordicsemi.android.nrftoolbox.uart.UARTManager;
import no.nordicsemi.android.nrftoolbox.uart.UARTManagerCallbacks;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

public class TrueMePebble extends AppCompatActivity implements UARTManagerCallbacks {
    BluetoothLeScannerCompat scanner;
    private ILogSession logSession;
    private LoggableBleManager<? extends BleManagerCallbacks> bleManager;

    int pebble=2;
    UARTManager manager;

    boolean connected=false;
    TextView tv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_true_me_pebble);


        tv=findViewById(R.id.display);
        tv.setMovementMethod(new ScrollingMovementMethod());

        bleManager = initializeManager();

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter.isEnabled()) {
            permission();
        }else
        {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),1);
        }

    }


    protected LoggableBleManager<? extends BleManagerCallbacks> initializeManager() {
        manager=new UARTManager(this);
        manager.setGattCallbacks(this);
        return manager;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==1)
        {
            permission();
        }
    }

    @Override
    public void onBackPressed() {
        bleManager.disconnect().enqueue();
        super.onBackPressed();
    }
    private void permission() {

        final String[] permissions = { Manifest.permission.ACCESS_FINE_LOCATION};
        Permissions.check(this/*context*/, permissions, null/*rationale*/, null/*options*/, new PermissionHandler() {
            @Override
            public void onGranted() {
            }
            @Override
            public void onDenied(Context context, ArrayList<String> deniedPermissions) {
                permission();
            }
        });
    }
    void startScan() {
        if (pebble==1)
        {
            tv.append("Scanning...");
            tv.append("\nFinding Pebble 1...");
        }else if (pebble==2)
        {
            tv.append("Scanning...");
            tv.append("\nFinding Pebble 2...");
        }
        scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setLegacy(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(1000).setUseHardwareBatchingIfSupported(false).build();
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(null).build());
        scanner.startScan(filters, settings, scanCallback);
    }
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, @NonNull final ScanResult result) {
            // do nothing
        }

        @Override
        public void onBatchScanResults(@NonNull final List<ScanResult> results) {
            try {

                for (int i=0;i<results.size();i++)
                {

                    if (!results.get(i).getDevice().getName().isEmpty() && pebble==1)
                    {
                        if (results.get(i).getDevice().getName().startsWith("SR01_"))
                        {
                            tv.append("\nFound "+results.get(i).getDevice().getName());
                            connectDevice(results.get(i).getDevice());
                            scanner.stopScan(scanCallback);
                            break;

                        }
                    }else if (!results.get(i).getDevice().getName().isEmpty() && pebble==2)
                    {
                        if (results.get(i).getDevice().getName().startsWith("SR02_"))
                        {
                            tv.append("\nFound "+results.get(i).getDevice().getName());
                            connectDevice(results.get(i).getDevice());
                            scanner.stopScan(scanCallback);
                            break;

                        }
                    }
                }

            }catch (Exception e)
            {

            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            // should never be called
        }
    };


    void connectDevice(BluetoothDevice device)
    {

        final int titleId = 0;
        if (titleId > 0) {
            logSession = Logger.newSession(getApplicationContext(), getString(titleId), device.getAddress(), device.getName()+"");
            // If nRF Logger is not installed we may want to use local logger
            if (logSession == null && getLocalAuthorityLogger() != null) {
                logSession = LocalLogSession.newSession(getApplicationContext(), getLocalAuthorityLogger(), device.getAddress(), device.getName()+"");
            }
        }
        try {
            bleManager.setLogger(logSession);
            bleManager.connect(device)
                    .useAutoConnect(false)
                    .retry(3, 100)
                    .enqueue();
        }catch (Exception e)
        {
            Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }

    }
    protected Uri getLocalAuthorityLogger() {
        return null;
    }

    @Override
    public void onDeviceConnecting(@NonNull BluetoothDevice device) {
        tv.append("\nConnecting to "+device.getName());
    }

    @Override
    public void onDeviceConnected(@NonNull BluetoothDevice device) {
        tv.append("\nConnected to "+device.getName());

        connected=true;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                manager.send("COM 090002550003000");
                tv.append("\nSent: "+"COM 090002550003000");

            }
        },1000);
    }

    @Override
    public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnected(@NonNull BluetoothDevice device) {
        tv.append("\n"+device.getName()+" Disconnected");
        connected=false;
    }

    @Override
    public void onLinkLossOccurred(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onServicesDiscovered(@NonNull BluetoothDevice device, boolean optionalServicesFound) {

    }

    @Override
    public void onDeviceReady(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onBondingRequired(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onBonded(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onBondingFailed(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onError(@NonNull BluetoothDevice device, @NonNull String message, int errorCode) {

    }

    @Override
    public void onDeviceNotSupported(@NonNull BluetoothDevice device) {

    }

    public void start(View view) {
        startScan();
    }
    public void stop(View view) {
        if (connected)
        {
            manager.send("SLE");
            tv.append("\nScanning Ended for pebbele"+pebble);
        }else
        {
            tv.append("\nNot Connected");

        }
    }

    @Override
    public void onDataReceived(@NonNull BluetoothDevice device, String data) {
        tv.append("\nReceived: "+data);

        if (data.contains("ACK1"))
        {
            manager.send("DEL 05000");
            tv.append("\nSent: "+"DEL 05000");

        }else if (data.contains("ACK2"))
        {
            manager.send("WRD ROB_");
            tv.append("\nSent: "+"WRD ROB_");

        }else if (data.contains("ACK3"))
        {
            tv.append("\n");
            if (pebble==1)
            {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //startScan();
                    }
                },1000);
                //pebble=2;
                //bleManager.disconnect().enqueue();

            }else
            {
                tv.append("\nSession Ended");
            }
        }

    }

    @Override
    public void onDataSent(@NonNull BluetoothDevice device, String data) {
        tv.append("\nSent: "+data);
    }
}