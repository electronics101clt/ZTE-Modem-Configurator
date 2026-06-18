package com.zte.modem;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.graphics.Color;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.zte.modem.USB_PERMISSION";
    private static final String PREFS_NAME = "ZTEModemPrefs";
    private static final String PREF_MODE = "selected_mode";

    private UsbManager usbManager;
    private TextView logView;
    private TextView statusView;
    private RadioGroup modeGroup;
    private EditText serialInput;
    private Button serialSendBtn;
    private TextView serialOutput;
    private EditText apnInput;
    private Button connectBtn;
    private Button disconnectBtn;
    private TextView connectionStatus;
    private StringBuilder logBuilder = new StringBuilder();
    private StringBuilder serialBuilder = new StringBuilder();
    private ArrayList<String> availableDrivers = new ArrayList<>();
    private String selectedMode = "auto";
    private UsbSerialPort serialPort = null;
    private SerialInputOutputManager ioManager = null;
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);

        // Title
        TextView title = new TextView(this);
        title.setText("ZTE Modem Configurator");
        title.setTextSize(18);
        title.setPadding(0, 0, 0, 20);
        mainLayout.addView(title);

        // Status section
        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setPadding(10, 10, 10, 10);
        statusView.setBackgroundColor(Color.parseColor("#F0F0F0"));
        mainLayout.addView(statusView);

        // Configuration section
        TextView configLabel = new TextView(this);
        configLabel.setText("Configuration Mode:");
        configLabel.setTextSize(14);
        configLabel.setPadding(0, 20, 0, 10);
        mainLayout.addView(configLabel);

        modeGroup = new RadioGroup(this);
        modeGroup.setPadding(20, 0, 0, 0);

        RadioButton autoMode = new RadioButton(this);
        autoMode.setText("Auto-Detect (Recommended)");
        autoMode.setId(1);
        autoMode.setChecked(true);
        modeGroup.addView(autoMode);

        RadioButton acmMode = new RadioButton(this);
        acmMode.setText("CDC-ACM (Serial Mode)");
        acmMode.setId(2);
        modeGroup.addView(acmMode);

        RadioButton ncmMode = new RadioButton(this);
        ncmMode.setText("CDC-NCM (Network Control Model)");
        ncmMode.setId(3);
        modeGroup.addView(ncmMode);

        RadioButton etherMode = new RadioButton(this);
        etherMode.setText("CDC-Ether (Ethernet Mode)");
        etherMode.setId(4);
        modeGroup.addView(etherMode);

        RadioButton rndisMode = new RadioButton(this);
        rndisMode.setText("RNDIS (Windows Mode)");
        rndisMode.setId(5);
        modeGroup.addView(rndisMode);

        RadioButton serialMode = new RadioButton(this);
        serialMode.setText("USB Serial (Diagnostic Mode)");
        serialMode.setId(6);
        modeGroup.addView(serialMode);

        mainLayout.addView(modeGroup);

        // Buttons
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 20, 0, 20);

        Button detectBtn = new Button(this);
        detectBtn.setText("Detect System");
        detectBtn.setOnClickListener(v -> detectSystem());
        buttonLayout.addView(detectBtn);

        Button configureBtn = new Button(this);
        configureBtn.setText("Configure Modem");
        configureBtn.setOnClickListener(v -> configureModem());
        buttonLayout.addView(configureBtn);

        mainLayout.addView(buttonLayout);

        // Connection section
        TextView connectionLabel = new TextView(this);
        connectionLabel.setText("Modem Connection:");
        connectionLabel.setTextSize(14);
        connectionLabel.setPadding(0, 20, 0, 10);
        mainLayout.addView(connectionLabel);

        apnInput = new EditText(this);
        apnInput.setHint("APN (e.g. internet, fast.t-mobile.com)");
        apnInput.setText("internet");
        apnInput.setTextSize(12);
        mainLayout.addView(apnInput);

        LinearLayout connectionBtnLayout = new LinearLayout(this);
        connectionBtnLayout.setOrientation(LinearLayout.HORIZONTAL);
        connectionBtnLayout.setPadding(0, 10, 0, 10);

        connectBtn = new Button(this);
        connectBtn.setText("Connect to Internet");
        connectBtn.setOnClickListener(v -> connectModem());
        connectionBtnLayout.addView(connectBtn);

        disconnectBtn = new Button(this);
        disconnectBtn.setText("Disconnect");
        disconnectBtn.setEnabled(false);
        disconnectBtn.setOnClickListener(v -> disconnectModem());
        connectionBtnLayout.addView(disconnectBtn);

        mainLayout.addView(connectionBtnLayout);

        connectionStatus = new TextView(this);
        connectionStatus.setText("Status: Not connected");
        connectionStatus.setTextSize(12);
        connectionStatus.setPadding(10, 10, 10, 10);
        connectionStatus.setBackgroundColor(Color.parseColor("#FFCCCC"));
        mainLayout.addView(connectionStatus);

        // Serial Terminal (initially hidden)
        TextView serialLabel = new TextView(this);
        serialLabel.setText("Serial Terminal:");
        serialLabel.setTextSize(12);
        serialLabel.setPadding(0, 10, 0, 5);
        serialLabel.setId(9001);
        serialLabel.setVisibility(TextView.GONE);
        mainLayout.addView(serialLabel);

        LinearLayout serialInputLayout = new LinearLayout(this);
        serialInputLayout.setOrientation(LinearLayout.HORIZONTAL);
        serialInputLayout.setId(9002);
        serialInputLayout.setVisibility(LinearLayout.GONE);

        serialInput = new EditText(this);
        serialInput.setHint("Enter AT command");
        serialInput.setTextSize(12);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.weight = 1;
        serialInput.setLayoutParams(inputParams);
        serialInputLayout.addView(serialInput);

        serialSendBtn = new Button(this);
        serialSendBtn.setText("Send");
        serialSendBtn.setOnClickListener(v -> sendSerialCommand());
        serialInputLayout.addView(serialSendBtn);

        mainLayout.addView(serialInputLayout);

        serialOutput = new TextView(this);
        serialOutput.setTextSize(10);
        serialOutput.setPadding(10, 10, 10, 10);
        serialOutput.setBackgroundColor(Color.parseColor("#001100"));
        serialOutput.setTextColor(Color.parseColor("#00FF00"));
        serialOutput.setId(9003);
        serialOutput.setVisibility(TextView.GONE);
        ScrollView serialScroll = new ScrollView(this);
        serialScroll.addView(serialOutput);
        LinearLayout.LayoutParams serialScrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200);
        serialScroll.setLayoutParams(serialScrollParams);
        serialScroll.setId(9004);
        serialScroll.setVisibility(ScrollView.GONE);
        mainLayout.addView(serialScroll);

        // Log view
        TextView logLabel = new TextView(this);
        logLabel.setText("Log:");
        logLabel.setTextSize(12);
        logLabel.setPadding(0, 10, 0, 5);
        mainLayout.addView(logLabel);

        logView = new TextView(this);
        logView.setTextSize(10);
        logView.setPadding(10, 10, 10, 10);
        logView.setBackgroundColor(Color.parseColor("#000000"));
        logView.setTextColor(Color.parseColor("#00FF00"));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1;
        scrollView.setLayoutParams(scrollParams);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter usbFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, usbFilter);

        IntentFilter adbFilter = new IntentFilter();
        adbFilter.addAction("com.zte.modem.DETECT");
        adbFilter.addAction("com.zte.modem.CONFIGURE");
        adbFilter.addAction("com.zte.modem.CONNECT");
        adbFilter.addAction("com.zte.modem.DISCONNECT");
        adbFilter.addAction("com.zte.modem.GET_STATUS");
        adbFilter.addAction("com.zte.modem.GET_LOG");
        registerReceiver(adbControlReceiver, adbFilter);

        loadPreferences();
        log("ZTE Modem Configurator Ready");
        log("ADB Control: Enabled");
        detectSystem();
    }
    
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedMode = prefs.getString(PREF_MODE, "auto");

        switch (selectedMode) {
            case "cdc_acm": modeGroup.check(2); break;
            case "cdc_ncm": modeGroup.check(3); break;
            case "cdc_ether": modeGroup.check(4); break;
            case "rndis": modeGroup.check(5); break;
            case "serial": modeGroup.check(6); break;
            default: modeGroup.check(1); break;
        }
    }

    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_MODE, selectedMode);
        editor.apply();
    }

    private void detectSystem() {
        log("\n=== System Detection ===");
        availableDrivers.clear();

        String[] cdcDrivers = {"cdc_acm", "cdc_ether", "cdc_ncm", "cdc_subset", "cdc_mbim", "rndis_host"};

        for (String driver : cdcDrivers) {
            File driverPath = new File("/sys/bus/usb/drivers/" + driver);
            if (driverPath.exists()) {
                log("✓ Found driver: " + driver);
                availableDrivers.add(driver);
            }
        }

        updateRadioButtons();
        updateStatus();

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
        }
    }

    private void updateRadioButtons() {
        for (int i = 0; i < modeGroup.getChildCount(); i++) {
            RadioButton rb = (RadioButton) modeGroup.getChildAt(i);
            String mode = getRadioButtonMode(rb.getId());

            if (mode.equals("auto")) {
                rb.setEnabled(true);
            } else {
                boolean available = availableDrivers.contains(mode) ||
                    (mode.equals("rndis") && availableDrivers.contains("rndis_host"));
                rb.setEnabled(available);
                if (!available) {
                    rb.setText(rb.getText() + " [Not Available]");
                }
            }
        }
    }

    private void updateStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Available Drivers: ");
        if (availableDrivers.isEmpty()) {
            status.append("None");
        } else {
            status.append(String.join(", ", availableDrivers));
        }
        status.append("\n\nSelected Mode: ").append(selectedMode);
        statusView.setText(status.toString());
    }

    private String getRadioButtonMode(int id) {
        switch (id) {
            case 2: return "cdc_acm";
            case 3: return "cdc_ncm";
            case 4: return "cdc_ether";
            case 5: return "rndis";
            case 6: return "serial";
            default: return "auto";
        }
    }

    private void configureModem() {
        int checkedId = modeGroup.getCheckedRadioButtonId();
        selectedMode = getRadioButtonMode(checkedId);
        savePreferences();

        log("\n=== Configuration Mode: " + selectedMode + " ===");

        String driverToUse;
        if (selectedMode.equals("auto")) {
            driverToUse = selectBestDriver();
            if (driverToUse == null) {
                log("✗ No suitable CDC drivers found!");
                return;
            }
        } else {
            driverToUse = selectedMode;
        }

        // Find ZTE device
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        UsbDevice zteDevice = null;

        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == 0x19d2) {
                zteDevice = device;
                break;
            }
        }

        if (zteDevice == null) {
            log("\n✗ No ZTE device found");
            return;
        }

        if (!usbManager.hasPermission(zteDevice)) {
            log("\nRequesting USB permission...");
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(zteDevice, permissionIntent);
        } else {
            if (selectedMode.equals("serial")) {
                configureAndOpenSerial(zteDevice);
            } else {
                configureModemDevice(zteDevice, driverToUse);
            }
        }
    }

    private String selectBestDriver() {
        if (availableDrivers.contains("cdc_acm")) return "cdc_acm";
        if (availableDrivers.contains("cdc_ncm")) return "cdc_ncm";
        if (availableDrivers.contains("cdc_ether")) return "cdc_ether";
        if (availableDrivers.contains("rndis_host")) return "rndis";
        return null;
    }
    
    private void configureModemDevice(UsbDevice device, String driver) {
        log("\n=== Configuring Modem ===");

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            log("✗ Failed to open device");
            return;
        }

        if (!connection.claimInterface(device.getInterface(0), true)) {
            log("✗ Failed to claim interface");
            connection.close();
            return;
        }

        String atCommand = null;

        switch (driver) {
            case "cdc_ncm":
                atCommand = "AT+ZCDRUN=9\r\n";
                log("→ Switching to CDC-NCM mode");
                break;
            case "cdc_ether":
                atCommand = "AT+ZCDRUN=E\r\n";
                log("→ Switching to CDC-Ether mode");
                break;
            case "cdc_acm":
                atCommand = "AT+ZCDRUN=F\r\n";
                log("→ Switching to CDC-ACM mode");
                break;
            case "rndis":
                atCommand = "AT+ZCDRUN=8\r\n";
                log("→ Switching to RNDIS mode");
                break;
            default:
                log("✗ No compatible mode found");
                connection.close();
                return;
        }

        byte[] cmd = atCommand.getBytes();
        int result = connection.controlTransfer(
            0x21, 0x20, 0, 0, cmd, cmd.length, 5000);

        log("Control transfer result: " + result + " bytes");

        result = connection.bulkTransfer(
            device.getInterface(1).getEndpoint(1),
            cmd, cmd.length, 5000);

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
                            configureModem();
                        }
                    } else {
                        log("✗ USB permission denied");
                    }
                }
            }
        }
    };

    private final BroadcastReceiver adbControlReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("[ADB] Received: " + action);

            switch (action) {
                case "com.zte.modem.DETECT":
                    detectSystem();
                    break;

                case "com.zte.modem.CONFIGURE":
                    String mode = intent.getStringExtra("mode");
                    if (mode != null) {
                        log("[ADB] Mode override: " + mode);
                        setModeFromString(mode);
                    }
                    configureModem();
                    break;

                case "com.zte.modem.CONNECT":
                    String apn = intent.getStringExtra("apn");
                    if (apn != null && !apn.isEmpty()) {
                        apnInput.setText(apn);
                    }
                    connectModem();
                    break;

                case "com.zte.modem.DISCONNECT":
                    disconnectModem();
                    break;

                case "com.zte.modem.GET_STATUS":
                    sendDiagnostics();
                    break;

                case "com.zte.modem.GET_LOG":
                    sendLog();
                    break;
            }
        }
    };

    private void setModeFromString(String mode) {
        switch (mode.toLowerCase()) {
            case "auto": modeGroup.check(1); selectedMode = "auto"; break;
            case "cdc_acm": case "acm": modeGroup.check(2); selectedMode = "cdc_acm"; break;
            case "cdc_ncm": case "ncm": modeGroup.check(3); selectedMode = "cdc_ncm"; break;
            case "cdc_ether": case "ether": modeGroup.check(4); selectedMode = "cdc_ether"; break;
            case "rndis": modeGroup.check(5); selectedMode = "rndis"; break;
            case "serial": case "usb_serial": modeGroup.check(6); selectedMode = "serial"; break;
        }
    }

    private void sendDiagnostics() {
        StringBuilder diag = new StringBuilder();
        diag.append("=== ZTE Modem Diagnostics ===\n");
        diag.append("Selected Mode: ").append(selectedMode).append("\n");
        diag.append("Available Drivers: ").append(String.join(", ", availableDrivers)).append("\n\n");

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        diag.append("USB Devices: ").append(deviceList.size()).append("\n");
        for (UsbDevice device : deviceList.values()) {
            diag.append("  - ").append(device.getProductName())
                .append(" (VID: 0x").append(Integer.toHexString(device.getVendorId()))
                .append(" PID: 0x").append(Integer.toHexString(device.getProductId()))
                .append(")\n");
        }

        log(diag.toString());
        System.out.println(diag.toString());
    }

    private void sendLog() {
        System.out.println("=== ZTE Modem Log ===");
        System.out.println(logBuilder.toString());
    }

    private void configureAndOpenSerial(UsbDevice device) {
        log("\n=== Configuring USB Serial Mode ===");

        // First, send configuration command
        configureModemDevice(device, "cdc_acm");

        log("\nWaiting 2 seconds for modem to reconfigure...");

        // Wait for modem to process command
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                runOnUiThread(() -> openSerialConnection(device));
            } catch (InterruptedException e) {
                log("✗ Error: " + e.getMessage());
            }
        }).start();
    }

    private void openSerialConnection(UsbDevice device) {
        log("Opening serial connection...");

        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (drivers.isEmpty()) {
            log("✗ No USB serial drivers found");
            log("→ Unplug and replug modem, then try again");
            return;
        }

        UsbSerialDriver driver = drivers.get(0);
        UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());

        if (connection == null) {
            log("✗ Failed to open device");
            return;
        }

        serialPort = driver.getPorts().get(0);

        try {
            serialPort.open(connection);
            serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            log("✓ Serial connection established!");
            log("→ Baud: 115200, 8N1");

            showSerialTerminal();

            // Start I/O manager
            ioManager = new SerialInputOutputManager(serialPort, new SerialInputOutputManager.Listener() {
                @Override
                public void onNewData(byte[] data) {
                    runOnUiThread(() -> {
                        String response = new String(data);
                        serialBuilder.append(response);
                        serialOutput.setText(serialBuilder.toString());
                        log("[MODEM] " + response.trim());
                    });
                }

                @Override
                public void onRunError(Exception e) {
                    runOnUiThread(() -> {
                        log("✗ Serial error: " + e.getMessage());
                    });
                }
            });

            new Thread(ioManager).start();

            // Send initial AT command to test
            sendSerialData("AT\r\n");

        } catch (IOException e) {
            log("✗ Error opening serial port: " + e.getMessage());
        }
    }

    private void showSerialTerminal() {
        runOnUiThread(() -> {
            findViewById(9001).setVisibility(TextView.VISIBLE);
            findViewById(9002).setVisibility(LinearLayout.VISIBLE);
            findViewById(9003).setVisibility(TextView.VISIBLE);
            findViewById(9004).setVisibility(ScrollView.VISIBLE);
        });
    }

    private void sendSerialCommand() {
        String command = serialInput.getText().toString().trim();
        if (command.isEmpty()) {
            return;
        }

        if (!command.endsWith("\r\n")) {
            command += "\r\n";
        }

        serialInput.setText("");
        log("[SEND] " + command.trim());
        sendSerialData(command);
    }

    private void sendSerialData(String data) {
        if (serialPort == null) {
            log("✗ Serial port not open");
            return;
        }

        try {
            serialPort.write(data.getBytes(), 1000);
        } catch (IOException e) {
            log("✗ Write error: " + e.getMessage());
        }
    }

    private void connectModem() {
        String apn = apnInput.getText().toString().trim();
        if (apn.isEmpty()) {
            log("✗ Please enter an APN");
            return;
        }

        log("\n=== Connecting to Internet ===");
        log("APN: " + apn);

        // Check if we have serial connection
        if (serialPort == null) {
            log("✗ No serial connection. Please configure Serial mode first.");
            return;
        }

        connectBtn.setEnabled(false);
        updateConnectionStatus("Configuring...", Color.parseColor("#FFFFCC"));

        new Thread(() -> {
            try {
                // AT command sequence to connect
                Thread.sleep(500);
                sendSerialData("AT\r\n");
                Thread.sleep(500);

                sendSerialData("AT+CGDCONT=1,\"IP\",\"" + apn + "\"\r\n");
                log("[SEND] AT+CGDCONT=1,\"IP\",\"" + apn + "\"");
                Thread.sleep(500);

                sendSerialData("AT+CGACT=1,1\r\n");
                log("[SEND] AT+CGACT=1,1");
                Thread.sleep(1000);

                sendSerialData("ATD*99#\r\n");
                log("[SEND] ATD*99#");
                Thread.sleep(2000);

                // Request VPN permission
                runOnUiThread(() -> {
                    Intent vpnIntent = VpnService.prepare(MainActivity.this);
                    if (vpnIntent != null) {
                        startActivityForResult(vpnIntent, 100);
                    } else {
                        startVpnService();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    log("✗ Connection error: " + e.getMessage());
                    connectBtn.setEnabled(true);
                    updateConnectionStatus("Connection failed", Color.parseColor("#FFCCCC"));
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startVpnService();
        } else {
            log("✗ VPN permission denied");
            connectBtn.setEnabled(true);
            updateConnectionStatus("VPN permission denied", Color.parseColor("#FFCCCC"));
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(this, ModemVpnService.class);
        intent.putExtra("dns1", "8.8.8.8");
        intent.putExtra("dns2", "8.8.4.4");
        startService(intent);

        isConnected = true;
        updateConnectionStatus("Connected", Color.parseColor("#CCFFCC"));
        disconnectBtn.setEnabled(true);
        log("✓ VPN service started - You're online!");
    }

    private void disconnectModem() {
        log("\n=== Disconnecting ===");

        // Stop VPN service
        Intent intent = new Intent(this, ModemVpnService.class);
        intent.setAction("STOP");
        startService(intent);

        // Send hangup command
        if (serialPort != null) {
            sendSerialData("ATH\r\n");
            log("[SEND] ATH (Hangup)");
        }

        isConnected = false;
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        updateConnectionStatus("Disconnected", Color.parseColor("#FFCCCC"));
        log("✓ Disconnected");
    }

    private void updateConnectionStatus(String status, int color) {
        runOnUiThread(() -> {
            connectionStatus.setText("Status: " + status);
            connectionStatus.setBackgroundColor(color);
        });
    }

    private void closeSerialConnection() {
        if (isConnected) {
            disconnectModem();
        }

        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }

        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                log("Error closing serial port: " + e.getMessage());
            }
            serialPort = null;
        }
    }

    private void log(String msg) {
        logBuilder.append(msg).append("\n");
        runOnUiThread(() -> logView.setText(logBuilder.toString()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeSerialConnection();
        unregisterReceiver(usbReceiver);
        unregisterReceiver(adbControlReceiver);
    }
}
