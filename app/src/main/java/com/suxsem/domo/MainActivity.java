package com.suxsem.domo;

/**
 * Created by Stefano on 12/02/2015.
 */

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chiralcode.colorpicker.ColorPickerDialog;

import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends ActionBarActivity {
    private Messenger service = null;
    private final Messenger serviceHandler = new Messenger(new ServiceHandler());
    private IntentFilter intentFilter = null;
    private PushReceiver pushReceiver;

    private final static String NODE = "DomoOne";
    private final static int TIMEOUT_DISCOVER = 15000;
    private final static int TIMEOUT_COMMAND = 10000;

    private final static String MESSAGE = "com.suxsem.domo.Message";
    private final static String CONENCTED = "com.suxsem.domo.Connected";
    private final static String DISCONNECTED = "com.suxsem.domo.Disconnected";

    private ScrollView container;
    private LinearLayout wait;
    private LinearLayout nobroker;
    private LinearLayout nonode;

    private TextView temperatura;
    private TextView umidita;
    private RelativeLayout ledContainer;
    private Button led;
    private SwitchCompat allarme;
    private TextView distanza;
    private TextView rumore;
    private TextView luce;
    private TextView movimento;

    private int ledColor;
    private final Handler handler = new Handler();
    private AlertDialog colorPickerDialog;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");

    private RunnableSwitch runnableSwitch;
    private RunnableLed runnableLed;

    private CompoundButton.OnCheckedChangeListener allarmeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            allarme.setEnabled(false);
            runnableSwitch = new RunnableSwitch(!allarme.isEnabled());
            handler.postDelayed(runnableSwitch, TIMEOUT_COMMAND);
            Bundle data = new Bundle();
            data.putCharSequence(MqttService.TOPIC, NODE + "/Allarme/c");
            data.putCharSequence(MqttService.MESSAGE, isChecked ? "1" : "0");
            data.putInt(MqttService.QOS, 2);
            Message msg = Message.obtain(null, MqttService.PUBLISH);
            msg.setData(data);
            try {
                service.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ledColor = getResources().getColor(R.color.primary);

        container = (ScrollView) findViewById(R.id.container);
        wait = (LinearLayout) findViewById(R.id.wait);
        nobroker = (LinearLayout) findViewById(R.id.nobroker);
        nonode = (LinearLayout) findViewById(R.id.nonode);

        temperatura = (TextView) findViewById(R.id.temperatura);
        umidita = (TextView) findViewById(R.id.umidita);
        ledContainer = (RelativeLayout) findViewById(R.id.ledContainer);
        led = (Button) findViewById(R.id.led);
        allarme = (SwitchCompat) findViewById(R.id.allarme);
        distanza = (TextView) findViewById(R.id.distanza);
        rumore = (TextView) findViewById(R.id.rumore);
        luce = (TextView) findViewById(R.id.luce);
        movimento = (TextView) findViewById(R.id.movimento);

        led.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorPickerDialog();
            }
        });
        allarme.setOnCheckedChangeListener(allarmeListener);

        intentFilter = new IntentFilter();
        intentFilter.addAction(MESSAGE);
        intentFilter.addAction(CONENCTED);
        intentFilter.addAction(DISCONNECTED);
        pushReceiver = new PushReceiver();
        registerReceiver(pushReceiver, intentFilter, null, null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startService(new Intent(this, MqttService.class));
        bindService(new Intent(this, MqttService.class), serviceConnection, 0);
        registerReceiver(pushReceiver, intentFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        subscribe(false);
        unregisterReceiver(pushReceiver);
        unbindService(serviceConnection);
    }

    private final Runnable nodeTimeout = new Runnable() {
        @Override
        public void run() {
            showView(nonode);
        }
    };

    private final class RunnableSwitch implements Runnable {
        private final boolean prevState;
        public RunnableSwitch(boolean state) {
            prevState = state;
        }
        @Override
        public void run() {
            allarme.setOnCheckedChangeListener(null);
            allarme.setChecked(prevState);
            allarme.setOnCheckedChangeListener(allarmeListener);
            allarme.setEnabled(true);
        }
    };

    private final class RunnableLed implements Runnable {
        private final int prevState;
        public RunnableLed(int state) {
            prevState = state;
        }
        @Override
        public void run() {
            ledColor = prevState;
            led.getBackground().setColorFilter(ledColor, PorterDuff.Mode.SRC_IN);
            ledContainer.setAlpha(1f);
            led.setEnabled(true);
        }
    };

    public class PushReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent i) {
            if (i.getAction().equals(MESSAGE)) {
                String topic = i.getStringExtra(MqttService.TOPIC);
                String message = i.getStringExtra(MqttService.MESSAGE);
                if (topic.equals(NODE + "/status")) {
                    if (message.equals("1"))
                        showView(container);
                    else
                        showView(nonode);
                    handler.removeCallbacks(nodeTimeout);
                } else if (topic.equals(NODE + "/Temperatura")) {
                    temperatura.setText(message + " °C");
                } else if (topic.equals(NODE + "/Umidità")) {
                    umidita.setText(message + " %");
                } else if (topic.equals(NODE + "/Allarme")) {
                    handler.removeCallbacks(runnableSwitch);
                    allarme.setOnCheckedChangeListener(null);
                    allarme.setChecked(message.equals("1"));
                    allarme.setOnCheckedChangeListener(allarmeListener);
                    allarme.setEnabled(true);
                } else if (topic.equals(NODE + "/Led")) {
                    handler.removeCallbacks(runnableLed);
                    ledColor = Color.parseColor("#" + message);
                    led.getBackground().setColorFilter(ledColor, PorterDuff.Mode.SRC_IN);
                    ledContainer.setAlpha(1f);
                    led.setEnabled(true);
                } else if (topic.equals(NODE + "/Distanza")) {
                    distanza.setText(message + " cm");
                } else if (topic.equals(NODE + "/Rumore")) {
                    rumore.setText(message);
                } else if (topic.equals(NODE + "/Luce")) {
                    luce.setText(message);
                } else if (topic.equals(NODE + "/Movimento")) {
                    movimento.setText(sdf.format(new Date(Long.parseLong(message) * 1000)));
                }
            } else if (i.getAction().equals(CONENCTED)) {
                showView(wait);
                subscribe(true);
                handler.postDelayed(nodeTimeout, TIMEOUT_DISCOVER);
            } else if (i.getAction().equals(DISCONNECTED)) {
                showView(nobroker);
                subscribe(false);
            }
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            service = new Messenger(binder);
            Bundle data = new Bundle();
            //data.putSerializable(MqttService.CLASSNAME, MainActivity.class);
            data.putCharSequence(MqttService.INTENTNAME, MESSAGE);
            data.putCharSequence(MqttService.CONNECTEDNAME, CONENCTED);
            data.putCharSequence(MqttService.DISCONNECTEDNAME, DISCONNECTED);
            Message msg = Message.obtain(null, MqttService.REGISTER);
            msg.setData(data);
            msg.replyTo = serviceHandler;
            try {
                service.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    private void subscribe(boolean subscribe) {
        Bundle data;
        Message msg;
        String[] topics = { NODE + "/status",
                            NODE + "/Temperatura",
                            NODE + "/Umidità",
                            NODE + "/Allarme",
                            NODE + "/Led",
                            NODE + "/Distanza",
                            NODE + "/Rumore",
                            NODE + "/Luce",
                            NODE + "/Movimento" };
        try {
            for (int i = 0; i < topics.length; i++) {
                data = new Bundle();
                data.putCharSequence(MqttService.TOPIC, topics[i]);
                data.putInt(MqttService.QOS, 2);
                msg = Message.obtain(null, subscribe ? MqttService.SUBSCRIBE : MqttService.UNSUBSCRIBE);
                msg.setData(data);
                service.send(msg);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MqttService.REGISTER:
                    try {
                        service.send(Message.obtain(null, MqttService.CHECKCONNECTIVITY));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    super.handleMessage(msg);
                    return;
            }
        }
    }

    private void showColorPickerDialog()
    {
        colorPickerDialog = new ColorPickerDialog(this, ledColor, new ColorPickerDialog.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color) {
                led.setEnabled(false);
                ledContainer.setAlpha(0.3f);
                runnableLed = new RunnableLed(ledColor);
                ledColor = color;
                led.getBackground().setColorFilter(ledColor, PorterDuff.Mode.SRC_IN);
                Bundle data = new Bundle();
                data.putCharSequence(MqttService.TOPIC, NODE + "/Led/c");
                data.putCharSequence(MqttService.MESSAGE, String.format("%06X", (0xFFFFFF & ledColor)));
                Log.d(getClass().getCanonicalName(), String.format("%06X", (0xFFFFFF & ledColor)));
                handler.postDelayed(runnableLed, TIMEOUT_COMMAND);
                data.putInt(MqttService.QOS, 2);
                Message msg = Message.obtain(null, MqttService.PUBLISH);
                msg.setData(data);
                try {
                    service.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        colorPickerDialog.show();
    }

    private void showView(View which) {
        if (which.getVisibility() == View.VISIBLE)
            return;

        container.setVisibility(View.GONE);
        wait.setVisibility(View.GONE);
        nonode.setVisibility(View.GONE);
        nobroker.setVisibility(View.GONE);

        which.setVisibility(View.VISIBLE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (colorPickerDialog != null && colorPickerDialog.isShowing()) {
            colorPickerDialog.dismiss();
            showColorPickerDialog();
        }
    }
}