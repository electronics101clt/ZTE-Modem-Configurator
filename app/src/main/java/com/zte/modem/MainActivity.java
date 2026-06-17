package com.zte.modem;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.io.File;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.zte.modem.USB_PERMISSION";
    private UsbManager usbManager;
    private TextView logView;
    private StringBuilder logBuilder = new StringBuilder();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        Button detectBtn = new Button(this);
        detectBtn.setText("Detect System & Configure Modem");
        detectBtn.setOnClickListener(v -> detectAndConfigure());
        layout.addView(detectBtn);
        
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setPadding(10, 10, 10, 10);
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView);
        layout.addView(scrollView);
        
        setContentView(layout);
        
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
        
        log("ZTE Modem Auto-Configurator Ready");
        detectAndConfigure();
    }
    
    private void detectAndConfigure() {
        log("\n=== System Detection ===");
        
        // Check available CDC drivers
        String[] cdcDrivers = {"cdc_acm", "cdc_ether", "cdc_ncm", "cdc_subset", "cdc_mbim"};
        String availableDriver = null;
        
        for (String driver : cdcDrivers) {
            File driverPath = new File("/sys/bus/usb/drivers/" + driver);
            if (driverPath.exists()) {
                log("✓ Found driver: " + driver);
                if (availableDriver == null && !driver.equals("cdc_subset")) {
                    availableDriver = driver;
                }
            }
        }
        
        if (availableDriver == null) {
            log("✗ No suitable CDC drivers found!");
            return;
        }
        
        log("→ Best driver: " + availableDriver);
        
        // Find ZTE device
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        UsbDevice zteDevice = null;
        
        for (UsbDevice device : deviceList.values()) {
            log("\nDevice: " + device.getProductName());
            log("  VID: 0x" + Integer.toHexString(device.getVendorId()));
            log("  PID: 0x" + Integer.toHexString(device.getProductId()));
            
            if (device.getVendorId() == 0x19d2) {
                zteDevice = device;
                log("  → ZTE Modem Found!");
            }
        }
        
        if (zteDevice == null) {
            log("\n✗ No ZTE device found");
            return;
        }
        
        // Request permission and configure
        if (!usbManager.hasPermission(zteDevice)) {
            log("\nRequesting USB permission...");
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(zteDevice, permissionIntent);
        } else {
            configureModem(zteDevice, availableDriver);
        }
    }
    
    private void configureModem(UsbDevice device, String driver) {
        log("\n=== Configuring Modem ===");
        
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            log("✗ Failed to open device");
            return;
        }
        
        // Claim interface 0 (control interface)
        if (!connection.claimInterface(device.getInterface(0), true)) {
            log("✗ Failed to claim interface");
            connection.close();
            return;
        }
        
        String atCommand = null;
        
        // Select AT command based on available driver
        switch (driver) {
            case "cdc_ncm":
                atCommand = "AT+ZCDRUN=9\r\n";  // NCM mode
                log("→ Switching to CDC-NCM mode");
                break;
            case "cdc_ether":
                atCommand = "AT+ZCDRUN=E\r\n";  // CDC-Ethernet mode
                log("→ Switching to CDC-Ether mode");
                break;
            case "cdc_acm":
                atCommand = "AT+ZCDRUN=F\r\n";  // Serial mode
                log("→ Switching to CDC-ACM mode");
                break;
            default:
                log("✗ No compatible mode found");
                connection.close();
                return;
        }
        
        // Send AT command via control transfer
        byte[] cmd = atCommand.getBytes();
        int result = connection.controlTransfer(
            0x21,  // bmRequestType (host to device, class, interface)
            0x20,  // bRequest (SET_LINE_CODING for CDC)
            0,     // wValue
            0,     // wIndex
            cmd,
            cmd.length,
            5000
        );
        
        log("Control transfer result: " + result + " bytes");
        
        // Try bulk transfer on endpoint 1
        result = connection.bulkTransfer(
            device.getInterface(1).getEndpoint(1),
            cmd,
            cmd.length,
            5000
        );
        
        log("Bulk transfer result: " + result + " bytes");
        log("\n✓ Configuration sent!");
        log("→ Unplug and replug modem to activate");
        
        connection.releaseInterface(device.getInterface(0));
        connection.close();
    }
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            log("✓ USB permission granted");
                            detectAndConfigure();
                        }
                    } else {
                        log("✗ USB permission denied");
                    }
                }
            }
        }
    };
    
    private void log(String msg) {
        logBuilder.append(msg).append("\n");
        runOnUiThread(() -> logView.setText(logBuilder.toString()));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }
}
