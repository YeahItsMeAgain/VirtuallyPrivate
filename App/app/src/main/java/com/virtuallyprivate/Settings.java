package com.virtuallyprivate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class Settings extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        _init();
    }

    private void _init() {
        final SharedPreferences pref = getSharedPreferences(VirtuallyPrivate.NAME, 0);

        // Location
        final EditText latitudeInput = findViewById(R.id.latitude_input);
        final EditText longitudeInput = findViewById(R.id.longitude_input);
        latitudeInput.setText(pref.getString(SharedPrefs.Location.LATITUDE, ""));
        longitudeInput.setText(pref.getString(SharedPrefs.Location.LONGITUDE, ""));

        // App List
        findViewById(R.id.appListChoose).setOnClickListener(view ->
                startActivity(new Intent(Settings.this, SettingsAppChoose.class))
        );

        // Contacts List
        findViewById(R.id.contactsListChoose).setOnClickListener(view ->
                startActivity(new Intent(Settings.this, SettingsContactsChoose.class))
        );

        // Clipboard
        final EditText clipboardInput = findViewById(R.id.clipboard_input);
        clipboardInput.setText(pref.getString(SharedPrefs.CLIPBOARD, ""));

        // Identifications
        final EditText imeiInput = findViewById(R.id.imei_input);
        imeiInput.setText(pref.getString(SharedPrefs.Identifications.IMEI, ""));
        final EditText phoneNumberInput = findViewById(R.id.phone_number_input);
        phoneNumberInput.setText(pref.getString(SharedPrefs.Identifications.PHONE_NUMBER, ""));
        final EditText meidInput = findViewById(R.id.meid_input);
        meidInput.setText(pref.getString(SharedPrefs.Identifications.MEID, ""));
        final EditText operatorInput = findViewById(R.id.operator_input);
        operatorInput.setText(pref.getString(SharedPrefs.Identifications.OPERATOR, ""));
        final EditText androidIdInput = findViewById(R.id.android_id_input);
        androidIdInput.setText(pref.getString(SharedPrefs.Identifications.ANDROID_ID, ""));

        // Wifi
        final EditText ssidInput = findViewById(R.id.SSID_input);
        ssidInput.setText(pref.getString(SharedPrefs.Wifi.SSID, ""));
        final EditText bssidInput = findViewById(R.id.BSSID_input);
        bssidInput.setText(pref.getString(SharedPrefs.Wifi.BSSID, ""));
        final EditText networkIdInput = findViewById(R.id.networkId_input);
        networkIdInput.setText(String.valueOf(pref.getInt(SharedPrefs.Wifi.NETWORK_ID, 0)));
        final EditText rssiInput = findViewById(R.id.rssi_input);
        rssiInput.setText(String.valueOf(pref.getInt(SharedPrefs.Wifi.RSSI, 0)));
        final EditText frequencyInput = findViewById(R.id.frequency_input);
        frequencyInput.setText(String.valueOf(pref.getInt(SharedPrefs.Wifi.FREQUENCY, 0)));
        final EditText macInput = findViewById(R.id.mac_input);
        macInput.setText(pref.getString(SharedPrefs.Wifi.MAC, ""));

        findViewById(R.id.submitSettingsChangeButtons).setOnClickListener(view -> {
            SharedPreferences.Editor prefEditor = pref.edit();
            prefEditor.putString(SharedPrefs.Location.LATITUDE, latitudeInput.getText().toString());
            prefEditor.putString(SharedPrefs.Location.LONGITUDE, longitudeInput.getText().toString());
            prefEditor.putString(SharedPrefs.CLIPBOARD, clipboardInput.getText().toString());
            prefEditor.putString(SharedPrefs.Identifications.IMEI, imeiInput.getText().toString());
            prefEditor.putString(SharedPrefs.Identifications.PHONE_NUMBER, phoneNumberInput.getText().toString());
            prefEditor.putString(SharedPrefs.Identifications.MEID, meidInput.getText().toString());
            prefEditor.putString(SharedPrefs.Identifications.OPERATOR, operatorInput.getText().toString());
            prefEditor.putString(SharedPrefs.Identifications.ANDROID_ID, androidIdInput.getText().toString());
            prefEditor.putString(SharedPrefs.Wifi.SSID, ssidInput.getText().toString());
            prefEditor.putInt(SharedPrefs.Wifi.NETWORK_ID, Utils.stringToInt(networkIdInput.getText().toString()));
            prefEditor.putInt(SharedPrefs.Wifi.RSSI, Utils.stringToInt(rssiInput.getText().toString()));
            prefEditor.putString(SharedPrefs.Wifi.BSSID, bssidInput.getText().toString());
            prefEditor.putInt(SharedPrefs.Wifi.FREQUENCY, Utils.stringToInt(frequencyInput.getText().toString()));
            prefEditor.putString(SharedPrefs.Wifi.MAC, macInput.getText().toString());
            prefEditor.apply();
            Toast.makeText(Settings.this, R.string.settings_change_string, Toast.LENGTH_SHORT).show();
        });
    }
}
