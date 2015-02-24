package com.suxsem.domo;

/**
 * Created by Stefano on 23/02/2015.
 */

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/**
 * Default ping sender implementation on Android. It is based on AlarmManager.
 *
 * <p>This class implements the {@link org.eclipse.paho.client.mqttv3.MqttPingSender} pinger interface
 * allowing applications to send ping packet to server every keep alive interval.
 * </p>
 *
 * @see org.eclipse.paho.client.mqttv3.MqttPingSender
 */
class MqttPingSender implements org.eclipse.paho.client.mqttv3.MqttPingSender {
    // Identifier for Intents, log messages, etc..
    static final String TAG = "MqttPingSender";

    private ClientComms comms;
    private MqttService service;
    private BroadcastReceiver alarmReceiver;
    private MqttPingSender that;
    private PendingIntent pendingIntent;
    private volatile boolean hasStarted = false;

    public MqttPingSender(MqttService service) {
        if (service == null) {
            throw new IllegalArgumentException(
                    "Neither service nor client can be null.");
        }
        this.service = service;
        that = this;
    }

    @Override
    public void init(ClientComms comms) {
        this.comms = comms;
        this.alarmReceiver = new AlarmReceiver();
    }

    @Override
    public void start() {
        String action = "com.suxsem.domo.PingSender";

        Log.d(TAG, "Register alarmreceiver to MqttService"+ action);
        service.registerReceiver(alarmReceiver, new IntentFilter(action));

        pendingIntent = PendingIntent.getBroadcast(service, 0, new Intent(
                action), PendingIntent.FLAG_UPDATE_CURRENT);

        schedule(comms.getKeepAlive());
        hasStarted = true;
    }

    @Override
    public void stop() {
        // Cancel Alarm.
        AlarmManager alarmManager = (AlarmManager) service
                .getSystemService(Service.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);

        Log.d(TAG, "Unregister alarmreceiver to MqttService"+comms.getClient().getClientId());
        if(hasStarted){
            hasStarted = false;
            try{
                service.unregisterReceiver(alarmReceiver);
            }catch(IllegalArgumentException e){
                //Ignore unregister errors.
            }
        }
    }

    @Override
    public void schedule(long delayInMilliseconds) {
        long nextAlarmInMilliseconds = System.currentTimeMillis()
                + delayInMilliseconds;
        Log.d(TAG, "Schedule next alarm at " + nextAlarmInMilliseconds);
        AlarmManager alarmManager = (AlarmManager) service
                .getSystemService(Service.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, nextAlarmInMilliseconds,
                pendingIntent);
    }

    /*
     * This class sends PingReq packet to MQTT broker
     */
    class AlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // According to the docs, "Alarm Manager holds a CPU wake lock as
            // long as the alarm receiver's onReceive() method is executing.
            // This guarantees that the phone will not sleep until you have
            // finished handling the broadcast."

            int count = intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, -1);
            Log.d(TAG, "Ping " + count + " times.");

            Log.d(TAG, "Check time :" + System.currentTimeMillis());
            IMqttToken token = comms.checkForActivity();

            // No ping has been sent.
            if (token == null) {
                return;
            }

        }
    }
}