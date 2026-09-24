package com.hamza.dawa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Alarms.ACTION.equals(intent.getAction())) return;
        Alarms.show(context,
                intent.getIntExtra("id", 1),
                intent.getStringExtra("title"),
                intent.getStringExtra("body"),
                intent.getStringExtra("key"),
                intent.getStringExtra("action"));
    }
}
