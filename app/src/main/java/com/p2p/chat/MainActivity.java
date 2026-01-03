package com.p2p.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int PORT = 8888;
    private String myName = "Користувач_" + (int)(Math.random()*100);
    private TextToSpeech tts;
    private ExecutorService pool = Executors.newCachedThreadPool();
    private ConcurrentHashMap<String, String> onlineUsers = new ConcurrentHashMap<>();
    private VectorGui gui;
    private WifiManager.MulticastLock lock;
    private volatile boolean running = true;
    private String mode = "local";
    private String serverAddress = "127.0.0.1";
    private Socket relaySocket;
    private boolean connected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Дозвіл на UDP пакети
        WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
        lock = wifi.createMulticastLock("p2p_lock");
        lock.acquire();

        tts = new TextToSpeech(this, this);
        gui = new VectorGui(this);
        setContentView(gui);

        startNetworkThreads();
    }

    private void startNetworkThreads() {
        if (mode.equals("local")) {
            // TCP Сервер
            pool.execute(() -> {
                try (ServerSocket ss = new ServerSocket(PORT)) {
                    while (running) {
                        Socket s = ss.accept();
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                            String msg = in.readLine();
                            if (msg != null) {
                                String sender = onlineUsers.getOrDefault(s.getInetAddress().getHostAddress(), "Хтось");
                                tts.speak(sender + " каже " + msg, TextToSpeech.QUEUE_ADD, null, null);
                                runOnUiThread(() -> gui.addMsg(sender + ": " + msg));
                            }
                        }
                    }
                } catch (Exception e) {}
            });

            // UDP Сканер (Listener)
            pool.execute(() -> {
                try (DatagramSocket ds = new DatagramSocket(PORT)) {
                    byte[] buf = new byte[1024];
                    while (running) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        ds.receive(p);
                        String name = new String(p.getData(), 0, p.getLength());
                        onlineUsers.put(p.getAddress().getHostAddress(), name);
                        runOnUiThread(() -> gui.updateOnline(onlineUsers));
                    }
                } catch (Exception e) {}
            });

            // UDP Beacon (Кожну секунду)
            pool.execute(() -> {
                try (DatagramSocket ds = new DatagramSocket()) {
                    ds.setBroadcast(true);
                    while (running) {
                        byte[] b = myName.getBytes();
                        ds.send(new DatagramPacket(b, b.length, InetAddress.getByName("255.255.255.255"), PORT));
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {}
            });
        } else if (mode.equals("server")) {
            connectToRelay();
        }
    }

    private void connectToRelay() {
        pool.execute(() -> {
            try {
                relaySocket = new Socket();
                relaySocket.connect(new InetSocketAddress(serverAddress, 9999), 5000);
                connected = true;
                BufferedReader in = new BufferedReader(new InputStreamReader(relaySocket.getInputStream()));
                while (running) {
                    String msg = in.readLine();
                    if (msg != null) {
                        runOnUiThread(() -> gui.addMsg(msg));
                    }
                }
            } catch (Exception e) {
                connected = false;
            }
        });
    }

    public void sendMessage(String msg) {
        if (connected) {
            pool.execute(() -> {
                try (PrintWriter out = new PrintWriter(relaySocket.getOutputStream(), true)) {
                    out.println(msg);
                } catch (Exception e) {}
            });
        } else {
            for (String ip : onlineUsers.keySet()) {
                pool.execute(() -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(ip, PORT), 500);
                        try (PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                            out.println(msg);
                        }
                    } catch (Exception e) {}
                });
            }
        }
        gui.addMsg("Я: " + msg);
    }

    public void showSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText("Ім'я:");
        EditText nameEt = new EditText(this);
        nameEt.setText(myName);

        TextView modeTv = new TextView(this);
        modeTv.setText("Режим:");
        RadioGroup modeRg = new RadioGroup(this);
        RadioButton localRb = new RadioButton(this);
        localRb.setText("Локальна мережа");
        RadioButton serverRb = new RadioButton(this);
        serverRb.setText("Сервер");
        modeRg.addView(localRb);
        modeRg.addView(serverRb);
        if (mode.equals("local")) localRb.setChecked(true);
        else serverRb.setChecked(true);

        TextView serverTv = new TextView(this);
        serverTv.setText("Адреса сервера:");
        EditText serverEt = new EditText(this);
        serverEt.setText(serverAddress);

        layout.addView(nameTv);
        layout.addView(nameEt);
        layout.addView(modeTv);
        layout.addView(modeRg);
        layout.addView(serverTv);
        layout.addView(serverEt);

        builder.setView(layout);
        builder.setPositiveButton("Застосувати", (d, w) -> {
            myName = nameEt.getText().toString();
            mode = localRb.isChecked() ? "local" : "server";
            serverAddress = serverEt.getText().toString();
            // Restart network
            running = false;
            try { Thread.sleep(100); } catch (Exception e) {}
            onlineUsers.clear();
            running = true;
            startNetworkThreads();
        });
        builder.show();
    }

    @Override public void onInit(int s) { if(s == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("uk")); }
    @Override protected void onDestroy() {
        running = false;
        pool.shutdownNow();
        if (relaySocket != null) try { relaySocket.close(); } catch (Exception e) {}
        if(lock.isHeld()) lock.release();
        tts.shutdown();
        super.onDestroy();
    }
}
