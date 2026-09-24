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
                if (Alarms.wantsVoice(x) && Alarms.canNotify(context)) {
                    try {
                        AlarmService.start(context, x);   // tone + spoken reminder
                    } catch (Exception e) {
                        Alarms.show(context, x);          // fallback: ringing notification
                    }
                } else {
                    Alarms.show(context, x);
                }
                DoseWidget.updateAll(context);
                break;
            case Alarms.ACTION_TAKEN:
                AlarmService.stop(context);
                Alarms.addPendingMarks(context, x.getString("keys"));
                Alarms.cancel(context, id);
                DoseWidget.markTaken(context, x.getString("keys"));
                AlarmActivity.finishIfShowing();
                MainActivity.refreshIfShowing();
                break;
            case Alarms.ACTION_SNOOZE:
                AlarmService.stop(context);
                Alarms.cancel(context, id);
                Alarms.snooze(context, x, 10);
                AlarmActivity.finishIfShowing();
                break;
            case Alarms.ACTION_DISMISS:
                AlarmService.stop(context);
                Alarms.cancel(context, id);
                break;
            default:
                break;
        }
    }
}
