package com.janhelmich.ar.smarthome;

import android.content.Context;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.janhelmich.coeus.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public class SmartSwitchInfoCard extends ARDeviceInfoCard implements Switch.OnCheckedChangeListener {

    private int layout;

    static String authenticator = "Bearer ***REMOVED***";
    static String base_url = "http://HA_HOST:8123/api/services/switch/toggle";
    static String states_url = "http://HA_HOST:8123/api/states/";
    static String identifier = "switch.tplink";

    private TextView deviceId;
    private TextView deviceName;
    private TextView deviceType;

    private Switch deviceSwitch;

    private TextView power;
    private TextView current;
    private TextView voltage;

    private TextView energyToday;
    private TextView energyTotal;

    public SmartSwitchInfoCard(String deviceId, String type, Context context) {
        super(deviceId, type, context);

        layout = R.layout.ar_switch_info;
    }

    @Override
    public void onActivate() {
        if (getScene() == null) {
            throw new IllegalStateException("Scene is null!");
        }

        this.setLocalPosition(new Vector3(0.0f, this.INFO_CARD_Y_POS_COEFF, 0.0f));
        ViewRenderable.builder()
                .setView(context, layout)
                .build()
                .thenAccept(
                        (renderable) -> {
                            this.setRenderable(renderable);

                            deviceId = renderable.getView().findViewById(R.id.device_id);
                            deviceId.setText(super.deviceId);
                            deviceName = renderable.getView().findViewById(R.id.device_name);
                            deviceType = renderable.getView().findViewById(R.id.device_type);
                            deviceType.setText(super.deviceType);

                            deviceSwitch = renderable.getView().findViewById(R.id.device_switch);

                            power = renderable.getView().findViewById(R.id.power);
                            current = renderable.getView().findViewById(R.id.current);
                            voltage = renderable.getView().findViewById(R.id.voltage);

                            energyToday = renderable.getView().findViewById(R.id.energy_today);
                            energyTotal = renderable.getView().findViewById(R.id.energy_total);

                            // Make API call to see if the switch is actually switched on.


                            this.getDeviceInfo();

                            deviceSwitch.setOnCheckedChangeListener(this);

                        })
                .exceptionally(
                        (throwable) -> {
                            throw new AssertionError("Could not load plane card view.", throwable);
                        });
    }



    private void getDeviceInfo() {

        JsonObjectRequest jsonObjectRequest;
        jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, states_url+identifier, null, response -> {
                    try {
                        JSONObject attributes = (JSONObject) response.get("attributes");

                        this.deviceName.setText((String) attributes.get("friendly_name"));
                        this.deviceSwitch.setChecked(response.get("state").equals("on"));
                        this.deviceSwitch.setEnabled(true);
                        this.power.setText((String) attributes.get("current_power_w"));
                        this.current.setText((String) attributes.get("current_a"));
                        this.voltage.setText((String) attributes.get("voltage"));
                        this.energyToday.setText((String) attributes.get("today_energy_kwh"));
                        this.energyTotal.setText((String) attributes.get("total_energy_kwh"));
                    } catch (JSONException e) {
                        //Log.i("JSONException", e.getMessage());
                        this.deviceName.setText("JSON Exception");

                    }
                }, error -> {
                    //Log.i("Request Error", error.getMessage());
                    this.deviceName.setText("Unavailable");
                    this.deviceSwitch.setChecked(false);
                    this.deviceSwitch.setEnabled(false);
                    this.power.setText("-");
                    this.current.setText("-");
                    this.voltage.setText("-");
                    this.energyToday.setText("-");
                    this.energyTotal.setText("-");
                })  {

            /**
             * Passing some request headers
             */
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("Authorization", authenticator);
                headers.put("Content-Type", "application/json");
                return headers;
            }};

        // Access the RequestQueue through your singleton class.
        RequestSingleton.getInstance(this.context).addToRequestQueue(jsonObjectRequest);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Make API call here
        JsonObjectRequest jsonObjectRequest = null;
        jsonObjectRequest = new JsonObjectRequest
                (Request.Method.POST, base_url, null, response -> {
                    return;
                }, error -> {
                    Log.i("Request Error", error.getMessage());
                })  {

            /**
             * Passing some request headers
             */
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                //headers.put("Content-Type", "application/json");
                headers.put("Authorization", authenticator);
                headers.put("Content-Type", "application/json");
                return headers;
            }};

        // Access the RequestQueue through your singleton class.
        RequestSingleton.getInstance(this.context).addToRequestQueue(jsonObjectRequest);
    }
}
