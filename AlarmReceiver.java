package com.hamza.dawa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Bundle x = intent.getExtras();
        if (action == null || x == null) return;
        int id = x.getInt("id", 1);
        switch (action) {
            case Alarms.ACTION_FIRE:
                Alarms.show(context, x);
                break;
            case Alarms.ACTION_TAKEN:
                Alarms.addPendingMarks(context, x.getString("keys"));
                Alarms.cancel(context, id);
                AlarmActivity.finishIfShowing();
                MainActivity.refreshIfShowing();
                break;
            case Alarms.ACTION_SNOOZE:
                Alarms.cancel(context, id);
                Alarms.snooze(context, x, 10);
                AlarmActivity.finishIfShowing();
                break;
            case Alarms.ACTION_DISMISS:
                Alarms.cancel(context, id);
                break;
            default:
                break;
        }
    }
}
