package com.janhelmich.ar.smarthome;

import android.content.Context;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.SeekBar;
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


public class HueWhiteLightInfoCard extends ARDeviceInfoCard implements Switch.OnCheckedChangeListener {

    private int layout;

    static String authenticator = "Bearer ***REMOVED***";
    static String base_url = "http://HA_HOST:8123/api/services/light/toggle";
    static String states_url = "http://HA_HOST:8123/api/states/";
    static String identifier = "light.hue_white_lamp_1";

    private TextView deviceId;
    private TextView deviceName;
    private TextView deviceType;

    private Switch deviceSwitch;

    private TextView brightnessTextView;
    private SeekBar brightnessSeekBar;

    public HueWhiteLightInfoCard(String deviceId, String type, Context context) {
        super(deviceId, type, context);

        layout = R.layout.ar_hue_white_light_info;
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

                            brightnessTextView = renderable.getView().findViewById(R.id.brightness);
                            brightnessSeekBar = renderable.getView().findViewById(R.id.brightness_seek_bar);

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
                (Request.Method.GET, states_url + identifier, null, response -> {
                    // TODO: Adjust accordingly to the json response.
                    // Issue could be the fact that the response is a JSONArray rather than a
                    // JSONObject.
                }, error -> Log.i("Request Error", error.getMessage()));
        }

        @Override
        public void onCheckedChanged (CompoundButton buttonView,boolean isChecked){
            // Make API call here
            HashMap<String, String> params = new HashMap<String, String>();
            params.put("entity_id", identifier);

            JsonObjectRequest jsonObjectRequest;
            jsonObjectRequest = new JsonObjectRequest
                    (Request.Method.POST, base_url, new JSONObject(params), response -> {
                        // Success
                    }, error -> {
                        // Error accessing the api call
                        Log.e("light.toggle", error.getMessage());
                    }) {

                /**
                 * Passing some request headers
                 */
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> headers = new HashMap<String, String>();
                    headers.put("Authorization", authenticator);
                    headers.put("Content-Type", "application/json");
                    return headers;
                }
            };

            // Access the RequestQueue through your singleton class.
            RequestSingleton.getInstance(this.context).addToRequestQueue(jsonObjectRequest);
        }
    }
