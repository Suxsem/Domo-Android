package com.suxsem.domo;

/**
 * Created by Stefano on 12/02/2015.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver
{

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.d(getClass().getCanonicalName(), "onReceive");
        context.startService(new Intent(context, MqttService.class));
    }

}