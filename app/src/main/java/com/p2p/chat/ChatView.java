package com.p2p.chat;

import android.content.Context;
import android.graphics.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatView extends android.view.View {
    private List<String> history = new ArrayList<>();
    private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ChatView(Context c) {
        super(c);
        p.setTextSize(40);
    }

    public void addMsg(String m) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        history.add(time + " " + m);
        if (history.size() > 20) history.remove(0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        p.setColor(Color.GREEN);
        float y = 100;
        for (String m : history) {
            c.drawText(m, 50, y, p);
            y += 60;
        }
    }
}
