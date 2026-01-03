package com.p2p.chat;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class VectorGui extends LinearLayout {
    private ChatView chatView;
    private TextView onlineView;
    private EditText input;
    private Button sendBtn;

    public VectorGui(Context c) {
        super(c);
        setOrientation(LinearLayout.VERTICAL);

        chatView = new ChatView(c);
        chatView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f));

        onlineView = new TextView(c);
        onlineView.setText("Онлайн: ");
        onlineView.setTextColor(Color.WHITE);
        onlineView.setBackgroundColor(Color.DKGRAY);
        addView(onlineView);

        LinearLayout inputLayout = new LinearLayout(c);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        input = new EditText(c);
        input.setHint("Введіть повідомлення");
        input.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sendBtn = new Button(c);
        sendBtn.setText("Надіслати");
        sendBtn.setOnClickListener(v -> {
            String msg = input.getText().toString().trim();
            if (!msg.isEmpty()) {
                ((MainActivity) getContext()).sendMessage(msg);
                input.setText("");
            }
        });
        Button settingsBtn = new Button(c);
        settingsBtn.setText("Налаштування");
        settingsBtn.setOnClickListener(v -> ((MainActivity) getContext()).showSettings());
        inputLayout.addView(input);
        inputLayout.addView(sendBtn);
        inputLayout.addView(settingsBtn);

        addView(chatView);
        addView(inputLayout);
    }

    public void addMsg(String m) {
        chatView.addMsg(m);
    }

    public void updateOnline(Map<String, String> users) {
        StringBuilder sb = new StringBuilder("Онлайн: ");
        for (String name : users.values()) {
            sb.append(name).append(" ");
        }
        onlineView.setText(sb.toString());
    }
}
