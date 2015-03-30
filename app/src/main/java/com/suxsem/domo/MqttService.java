package com.suxsem.domo;

/**
 * Created by Stefano on 12/02/2015.
 */

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Vector;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
//import android.util.Log;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class MqttService extends Service {

    //private static final String topicAllarme = "Suxsem/Allarme";
    private static final String topicAllarme = "DomoOne/Motivo"; //todo remove

    private static boolean serviceRunning = false;
    private static int mid = 0;
    private static MQTTConnection connection = null;
    private final Messenger clientMessenger = new Messenger(new ClientHandler());
    private MqttService service = this;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    public void onCreate() {
        super.onCreate();
        connection = new MQTTConnection();
        internalConnect();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(receiver, filter);

        // Use instance field for listener
        // It will not be gc'd as long as this instance is kept referenced
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (!key.equals("host") && !key.equals("port") && !key.equals("user") && !key.equals("password") && !key.equals("ssl"))
                    return;
                Message msg = Message.obtain(null, RECONNECT);
                try {
                    clientMessenger.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning()) {
            return START_STICKY;
        }

        super.onStartCommand(intent, flags, startId);
        /*
		 * Start the MQTT Thread.
		 */
        connection.start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        connection.end();
    }

    @Override
    public IBinder onBind(Intent intent) {
		/*
		 * Return a reference to our client handler.
		 */
        return clientMessenger.getBinder();
    }

    private synchronized static boolean isRunning() {
		 /*
		  * Only run one instance of the service.
		  */
        if (serviceRunning == false) {
            serviceRunning = true;
            return false;
        } else {
            return true;
        }
    }

    /*
     * These are the supported messages from bound clients
     */
    public static final int REGISTER = 0;
    public static final int SUBSCRIBE = 1;
    public static final int UNSUBSCRIBE = 2;
    public static final int PUBLISH = 3;
    private static final int CONNECTIVITYCHANGE = 4;
    public static final int CHECKCONNECTIVITY = 5;
    private static final int RECONNECT = 6;

    /*
     * Fixed strings for the supported messages.
     */
    public static final String TOPIC = "topic";
    public static final String MESSAGE = "message";
    public static final String QOS = "qos";
    public static final String RETAIN = "retain";
    public static final String STATUS = "status";
    public static final String CLASSNAME = "classname";
    public static final String INTENTNAME = "intentname";
    public static final String CONNECTEDNAME = "connectedname";
    public static final String DISCONNECTEDNAME = "disconnectedname";
    private static final String CONNECTIVITY = "connectivity";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")){
                internalConnect();
            }
        }
    };

    private void internalConnect() {
        Bundle data = new Bundle();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            //c'è internet (si spera)
            data.putBoolean(CONNECTIVITY, true);
        } else {
            //non c'è internet (sicuro)
            data.putBoolean(CONNECTIVITY, false);
        }
        Message msg = Message.obtain(null, CONNECTIVITYCHANGE);
        msg.setData(data);
        try {
            clientMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /*
     * This class handles messages sent to the service by
     * bound clients.
     */
    class ClientHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            boolean status = false;

            switch (msg.what) {
                case SUBSCRIBE:
                case UNSUBSCRIBE:
                case PUBLISH:
                case CONNECTIVITYCHANGE:
                case CHECKCONNECTIVITY:
                case RECONNECT:
           		 	/*
           		 	 * These requests should be handled by
           		 	 * the connection thread, call makeRequest
           		 	 */
                    connection.makeRequest(msg);
                    break;
                case REGISTER: {
                    Bundle b = msg.getData();
                    if (b != null) {
                        Object target = b.getSerializable(CLASSNAME);
                        if (target != null) {
        				 /*
        				  * This request can be handled in-line
        				  * call the API
        				  */
                            connection.setPushCallback((Class<?>) target);
                            status = true;
                        }
                        CharSequence cs = b.getCharSequence(INTENTNAME);
                        if (cs != null) {
                            String name = cs.toString().trim();
                            if (name.isEmpty() == false) {
            				 /*
            				  * This request can be handled in-line
            				  * call the API
            				  */
                                connection.setIntentName(name);
                                status = true;
                            }
                        }
                        cs = b.getCharSequence(CONNECTEDNAME);
                        if (cs != null) {
                            String name = cs.toString().trim();
                            if (name.isEmpty() == false)
                                connection.setConnectedName(name);
                        }
                        cs = b.getCharSequence(DISCONNECTEDNAME);
                        if (cs != null) {
                            String name = cs.toString().trim();
                            if (name.isEmpty() == false)
                                connection.setDisconnectedName(name);
                        }
                    }
                    ReplytoClient(msg.replyTo, msg.what, status);
                    break;
                }
            }
        }
    }

    private void ReplytoClient(Messenger responseMessenger, int type, boolean status) {
		 /*
		  * A response can be sent back to a requester when
		  * the replyTo field is set in a Message, passed to this
		  * method as the first parameter.
		  */
        if (responseMessenger != null) {
            Bundle data = new Bundle();
            data.putBoolean(STATUS, status);
            Message reply = Message.obtain(null, type);
            reply.setData(data);

            try {
                responseMessenger.send(reply);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private class MQTTConnection extends Thread {
        private Class<?> launchActivity = null;
        private String intentName = null;
        private String connectedName = null;
        private String disconnectedName = null;
        private MsgHandler msgHandler = null;
        private static final int STOP = 1001;
        private static final int CONNECT = 1002;
        private static final int RESETTIMER = 1003;

        MQTTConnection() {
            msgHandler = new MsgHandler();
        }

        public void end() {
            msgHandler.sendMessage(Message.obtain(null, STOP));
        }

        public void makeRequest(Message msg) {
			/*
			 * It is expected that the caller only invokes
			 * this method with valid msg.what.
			 */
            msgHandler.sendMessage(Message.obtain(msg));
        }

        public void setPushCallback(Class<?> activityClass) {
            launchActivity = activityClass;
        }

        public void setIntentName(String name) {
            intentName = name;
        }
        public void setConnectedName(String name) {
            connectedName = name;
        }
        public void setDisconnectedName(String name) {
            disconnectedName = name;
        }

        private class MsgHandler extends Handler implements MqttCallback {
            //private final String HOST = getResources().getString(R.string.broker_host);
            //private final int PORT = 8883;
            //private final String uri = "ssl://" + HOST + ":" + PORT;
            private final int MINTIMEOUT = 2000;
            private final int MAXTIMEOUT = 64000;
            private int timeout = MINTIMEOUT;
            private MqttClient client = null;
            private MqttConnectOptions options = new MqttConnectOptions();
            private Vector<String> topics = new Vector<String>();
            private Vector<Integer> topicsQos = new Vector<Integer>();
            private boolean hasConnectivity = false;
            private SSLSocketFactory socketFactory;
            private MemoryPersistence persistence = new MemoryPersistence();

            MsgHandler() {
                topics.add(topicAllarme);
                topicsQos.add(2);

                options.setCleanSession(true);
                options.setKeepAliveInterval(240);

                try {
                    X509TrustManager stupidTruster = new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs,
                                                       String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs,
                                                       String authType) {
                        }
                    };
                    SSLContext sslContext = SSLContext.getInstance("TLSv1");
                    sslContext.init(null,
                            new TrustManager[]{stupidTruster},
                            null);

                    socketFactory = sslContext.getSocketFactory();
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    e.printStackTrace();
                }

                setConnectionInfo();
            }

            private void setConnectionInfo() {
                try {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
                    final String user = prefs.getString("user", "");
                    final String passwordEnc = prefs.getString("password", "");
                    final String passwordKey = prefs.getString("key", "");
                    String password = "";
                    if (passwordKey.length() > 0 && passwordEnc.length() > 0) {
                        try {
                            AesCbcWithIntegrity.SecretKeys key = AesCbcWithIntegrity.keys(passwordKey);
                            AesCbcWithIntegrity.CipherTextIvMac civ = new AesCbcWithIntegrity.CipherTextIvMac(passwordEnc);
                            password = AesCbcWithIntegrity.decryptString(civ, key);
                        } catch (IllegalArgumentException | GeneralSecurityException | UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }

                    final String host = prefs.getString("host", "");
                    final String port = prefs.getString("port", "");
                    options.setUserName(user.length() > 0 ? user : "null");
                    options.setPassword(password.length() > 0 ? password.toCharArray() : "null".toCharArray());
                    String uri;
                    if (prefs.getBoolean("ssl", false)) {
                        uri = "ssl://" + (host.length() > 0 ? host : "127.0.0.1") + ":" + (port.length() > 0 ? port : "8883");
                        options.setSocketFactory(socketFactory);
                    } else {
                        uri = "tcp://" + (host.length() > 0 ? host : "127.0.0.1") + ":" + (port.length() > 0 ? port : "1883");
                        options.setSocketFactory(null);
                    }
                    client = new MqttClientPing(uri, MqttClient.generateClientId(), persistence, new MqttPingSender(service));
                    client.setCallback(this);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CHECKCONNECTIVITY: {
                        Intent intent = new Intent();
                        if (hasConnectivity && client != null && client.isConnected())
                            intent.setAction(connectedName);
                        else
                            intent.setAction(disconnectedName);
                        sendBroadcast(intent);
                        break;
                    }
                    case CONNECTIVITYCHANGE: {
                        Bundle b = msg.getData();
                        if (b != null) {
                            hasConnectivity = b.getBoolean(CONNECTIVITY);
                            if (hasConnectivity)
                                sendMessage(Message.obtain(null, CONNECT));
                            else
                                removeMessages(CONNECT);
                        }
                        break;
                    }
                    case RECONNECT: {
                        if (client != null && client.isConnected())
                            disconnect();
                        setConnectionInfo();
                        sendMessage(Message.obtain(null, CONNECT));
                        break;
                    }
                    case STOP: {
					/*
					 * Clean up, and terminate.
					 */
                        if (client != null && client.isConnected())
                            disconnect();
                        getLooper().quit();
                        break;
                    }
                    case CONNECT: {
                        if (hasConnectivity && client != null && !client.isConnected()) {
                            try {
                                client.connect(options);
                                //Log.d(getClass().getCanonicalName(), "Connected");
                                /*
                                 * Re-subscribe to previously subscribed topics
                                 */
                                for (int i = 0; i < topics.size(); i++) {
                                    subscribe(topics.get(i), topicsQos.get(i));
                                }
                                if (connectedName != null) {
                                    Intent intent = new Intent();
                                    intent.setAction(connectedName);
                                    sendBroadcast(intent);
                                }
                                timeout = MINTIMEOUT;
                                removeMessages(CONNECT);
                            } catch (MqttException e) {
                                //Log.d(getClass().getCanonicalName(), "Connection attemp failed with reason code = " + e.getReasonCode() + e.getCause());
                                if (timeout < MAXTIMEOUT) {
                                    timeout *= 2;
                                }
                                sendMessageDelayed(Message.obtain(null, CONNECT), timeout);
                            }
                        }
                        break;
                    }
                    case RESETTIMER: {
                        timeout = MINTIMEOUT;
                        break;
                    }
                    case SUBSCRIBE: {
                        Bundle b = msg.getData();
                        if (b != null) {
                            CharSequence csTopic = b.getCharSequence(TOPIC);
                            int qos = b.getInt(QOS, 0);
                            if (csTopic != null) {
                                String topic = csTopic.toString().trim();
                                if (topic.isEmpty() == false) {
                                    if (!topics.contains(topic)) {
                                        /*
	        					        * Save this topic for re-subscription if needed.
	        					        */
                                        topics.add(topic);
                                        topicsQos.add(qos);
                                        if (client != null && client.isConnected())
                                            subscribe(topic, qos);
                                    }
                                }
                            }
                        }
                        break;
                    }
                    case UNSUBSCRIBE: {
                        Bundle b = msg.getData();
                        if (b != null) {
                            CharSequence cs = b.getCharSequence(TOPIC);
                            if (cs != null) {
                                String topic = cs.toString().trim();
                                if (topic.isEmpty() == false) {
                                    int toRemove = topics.indexOf(topic);
                                    if (toRemove >= 0) {
                                        topics.remove(toRemove);
                                        topicsQos.remove(toRemove); //autoboxing
                                    }
                                    if (client != null && client.isConnected())
                                        unsubscribe(topic);
                                }
                            }
                        }
                        break;
                    }
                    case PUBLISH: {
                        boolean status = false;
                        Bundle b = msg.getData();
                        if (b != null && client != null && client.isConnected()) {
                            CharSequence csTopic = b.getCharSequence(TOPIC);
                            CharSequence csMessage = b.getCharSequence(MESSAGE);
                            int qos = b.getInt(QOS, 0);
                            boolean retain = b.getBoolean(RETAIN, false);
                            if (csTopic != null && csMessage != null) {
                                String topic = csTopic.toString().trim();
                                String message = csMessage.toString().trim();
                                if (topic.isEmpty() == false) {
                                    status = publish(topic, message, qos, retain);
                                }
                            }
                        }
                        ReplytoClient(msg.replyTo, msg.what, status);
                        break;
                    }
                }
            }

            private boolean subscribe(String topic, int qos) {
                try {
                    client.subscribe(topic, qos);
                    //Log.d(getClass().getCanonicalName(), "Subscribed to: " + topic);
                } catch (MqttException e) {
                    //Log.d(getClass().getCanonicalName(), "Subscribe failed with reason code = " + e.getReasonCode());
                    return false;
                }
                return true;
            }

            private boolean unsubscribe(String topic) {
                try {
                    client.unsubscribe(topic);
                    //Log.d(getClass().getCanonicalName(), "Unsubscribed from: " + topic);
                } catch (MqttException e) {
                    //Log.d(getClass().getCanonicalName(), "Unsubscribe failed with reason code = " + e.getReasonCode());
                    return false;
                }
                return true;
            }

            private boolean publish(String topic, String msg, int qos, boolean retain) {
                try {
                    MqttMessage message = new MqttMessage();
                    message.setPayload(msg.getBytes());
                    message.setQos(qos);
                    message.setRetained(retain);
                    client.publish(topic, message);
                } catch (MqttException e) {
                    //Log.d(getClass().getCanonicalName(), "Publish failed with reason code = " + e.getReasonCode());
                    return false;
                }
                return true;
            }

            private void disconnect() {
                try {
                    client.disconnect();
                    connectionLost(null);
                    client.close();
                    client = null;
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void connectionLost(Throwable arg0) {
                if (disconnectedName != null) {
                    Intent intent = new Intent();
                    intent.setAction(disconnectedName);
                    sendBroadcast(intent);
                }
                sendMessageDelayed(Message.obtain(null, CONNECT), timeout);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken arg0) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                //Log.d(getClass().getCanonicalName(), topic + ":" + message.toString());

                if (intentName != null) {
                    Intent intent = new Intent();
                    intent.setAction(intentName);
                    intent.putExtra(TOPIC, topic);
                    intent.putExtra(MESSAGE, message.toString());
                    sendBroadcast(intent);
                }


                Context context = getBaseContext();
                PendingIntent pendingIntent = null;

                if (launchActivity != null) {
                    Intent intent = new Intent(context, launchActivity);
                    intent.setAction(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_LAUNCHER);

                    //build the pending intent that will start the appropriate activity
                    pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
                }

                if (topic.equals(topicAllarme)) {
                    //build the notification
                    Builder notificationCompat = new Builder(context);
                    notificationCompat.setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .setContentTitle("Allarme scattato!")
                            .setContentText(message.toString())
                            .setSmallIcon(R.drawable.scattato);
                    Notification notification = notificationCompat.build();
                    notification.defaults |= Notification.DEFAULT_VIBRATE;
                    notification.defaults |= Notification.DEFAULT_SOUND;
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(mid++, notification);
                }
            }
        }
    }
}