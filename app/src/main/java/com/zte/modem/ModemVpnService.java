package com.zte.modem;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class ModemVpnService extends VpnService {
    private static final String CHANNEL_ID = "ModemVpnChannel";
    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private volatile boolean running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        String dns1 = intent.getStringExtra("dns1");
        String dns2 = intent.getStringExtra("dns2");

        createNotificationChannel();
        startForeground(1, createNotification());

        startVpn(dns1, dns2);

        return START_STICKY;
    }

    private void startVpn(String dns1, String dns2) {
        if (running) return;

        running = true;

        Builder builder = new Builder();
        builder.setSession("ZTE Modem Connection")
               .addAddress("10.0.0.2", 24)
               .addRoute("0.0.0.0", 0);

        if (dns1 != null && !dns1.isEmpty()) {
            builder.addDnsServer(dns1);
        } else {
            builder.addDnsServer("8.8.8.8");
        }

        if (dns2 != null && !dns2.isEmpty()) {
            builder.addDnsServer(dns2);
        } else {
            builder.addDnsServer("8.8.4.4");
        }

        try {
            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                stopVpn();
                return;
            }

            vpnThread = new Thread(() -> {
                try {
                    FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                    FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
                    ByteBuffer packet = ByteBuffer.allocate(32767);

                    while (running) {
                        int length = in.read(packet.array());
                        if (length > 0) {
                            // Simple pass-through
                            packet.limit(length);
                            out.write(packet.array(), 0, length);
                            packet.clear();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            vpnThread.start();

        } catch (Exception e) {
            e.printStackTrace();
            stopVpn();
        }
    }

    private void stopVpn() {
        running = false;

        if (vpnThread != null) {
            vpnThread.interrupt();
        }

        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "ZTE Modem VPN",
            NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent stopIntent = new Intent(this, ModemVpnService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ZTE Modem Connected")
            .setContentText("Routing traffic through USB modem")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Disconnect", stopPendingIntent)
            .build();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
