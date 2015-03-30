package com.suxsem.domo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.EditText;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

/**
 * Created by Stefano on 28/03/2015.
 */

public class SettingsActivity extends ActionBarActivity {

    private SharedPreferences prefs;
    private Activity activity = this;
    private EditText passwordEdit;
    private boolean passwordChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        ((EditText) findViewById(R.id.host)).setText(prefs.getString("host", ""));
        ((EditText) findViewById(R.id.port)).setText(prefs.getString("port", ""));
        ((EditText) findViewById(R.id.user)).setText(prefs.getString("user", ""));
        passwordEdit = (EditText) findViewById(R.id.password);
        ((SwitchCompat) findViewById(R.id.ssl)).setChecked(prefs.getBoolean("ssl", false));

        final String password = prefs.getString("password", "");
        if (password.length() > 0)
            passwordEdit.setHint("<Invariata>");
        else
            passwordEdit.setHint("Password");

        passwordEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (passwordChanged)
                    return;
                passwordChanged= true;
                passwordEdit.setHint("Password");
            }
        });

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new Encrypt().execute(((EditText) findViewById(R.id.host)).getText().toString(),
                        ((EditText) findViewById(R.id.port)).getText().toString(),
                        ((EditText) findViewById(R.id.user)).getText().toString(),
                        ((EditText) findViewById(R.id.password)).getText().toString(),
                        prefs.getString("key", ""));

            }
        });
    }

    class Encrypt extends AsyncTask<String, String, String> {

        ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progress = ProgressDialog.show(activity, null, "Salvataggio in corso...", true);
        }

        @Override
        protected String doInBackground(String... params) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("host", params[0]);
            editor.putString("port", params[1]);
            editor.putString("user", params[2]);

            if (passwordChanged) {
                try {
                    AesCbcWithIntegrity.SecretKeys key;
                    if (params[4].length() > 0) {
                        key = AesCbcWithIntegrity.keys(params[4]);
                    } else {
                        String salt = AesCbcWithIntegrity.saltString(AesCbcWithIntegrity.generateSalt());
                        key = AesCbcWithIntegrity.generateKeyFromPassword(getResources().getString(R.string.crypto_key), salt);
                        editor.putString("key", AesCbcWithIntegrity.keyString(key));
                    }
                    AesCbcWithIntegrity.CipherTextIvMac civ = AesCbcWithIntegrity.encrypt(params[3], key);
                    editor.putString("password", civ.toString());
                } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            editor.putBoolean("ssl", ((SwitchCompat) findViewById(R.id.ssl)).isChecked());

            editor.apply();
            finish();

            return null;
        }

        @Override
        protected void onPostExecute(String file_url) {
            progress.dismiss();
        }
    }

}