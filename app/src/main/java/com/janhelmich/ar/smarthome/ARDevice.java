package com.janhelmich.ar.smarthome;

import android.content.Context;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;

public class ARDevice extends Node implements Node.OnTapListener {

    private String id;
    private boolean selected;

    private Renderable model;
    private Context context;
    private Node nodeVisual;
    private Node infoCard;

    private boolean movable;
    private Quaternion lastCameraDirection;
    private Vector3 lastCameraPosition;

    private FloatingActionButton up;
    private FloatingActionButton down;

    public ARDevice(String id, Renderable model, Context context, FloatingActionButton up, FloatingActionButton down) {
        super();
        this.model = model;
        this.context = context;

        // Set Device ID to provided ID String
        this.id = id;

        // Initialize node to be unselected and unmovable on start
        selected = false;
        movable = false;

        // Set floating action buttons
        this.up = up;
        this.down = down;
    }

    public void changeHeight(float height) {
        this.setLocalPosition(new Vector3(this.getLocalPosition().x, height / 5, this.getLocalPosition().z));
    }

    public void changeRotation(float degree) {
        degree *= 36;
        degree += 180;
        Quaternion localRotation = Quaternion.axisAngle(new Vector3(0.0f, 1.0f, 0.0f), degree);

        this.nodeVisual.setLocalRotation(localRotation);
    }

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    public void onActivate() {

        if (getScene() == null) {
            throw new IllegalStateException("Scene is null!");
        }

        if (nodeVisual == null) {
            nodeVisual = new Node();
            nodeVisual.setParent(this);
            nodeVisual.setRenderable(model);
            nodeVisual.setOnTapListener(this);
        }

        if (infoCard == null) {
            infoCard = new ARDeviceInfoCard("TestDeviceName", "TestDeviceType", this.context);
            infoCard.setParent(this);
            infoCard.setEnabled(false);
        }
    }

    @Override
    public void onTap(HitTestResult hitTestResult, MotionEvent motionEvent) {
        Log.i("onTap", "Tapped on ARDevice " + this.id);

        infoCard.setEnabled(!infoCard.isEnabled());

        if (infoCard.isEnabled()) {
            up.setOnClickListener(v -> {
                this.setLocalPosition(Vector3.add(this.getLocalPosition(), new Vector3(0.0f, 0.2f, 0.0f)));
            });
            up.show();
            down.setOnClickListener(v -> {
                this.setLocalPosition(Vector3.add(this.getLocalPosition(), new Vector3(0.0f, -0.2f, 0.0f)));
            });
            down.show();
        } else {
            up.hide();
            down.hide();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    /**
     * Triggers the node mode to being movable.
     * Once activated, will follow the movements of the camera at constant distance
     * vector around. If deactivated, will go back to a stable position.
     */
    public void setMovable(boolean movable) {
        this.movable = movable;
    }

    public boolean getMovable() {
        return movable;
    }


    // TODO: Integrate a suitable way to move the node around.
    // Can be done via explicit changes to the position of the node, or in the
    // onUpdate function following the movement of the camera.
    @Override
    public void onUpdate(FrameTime frameTime) {
        if (getScene() == null) {
            return;
        }

        // If movable, keep consistent distance to camera and mirror its movement.
        if (movable) {
            if (lastCameraDirection != null) {
                //Vector3 cameraPosition = getScene().getCamera().getWorldPosition();

                //Quaternion cameraRotation = getScene().getCamera().getWorldRotation();
                //Vector3 adjustedToCamera = Quaternion.rotateVector(cameraRotation, toCamera);

                // this.setWorldPosition(Vector3.add(cameraPosition, toCamera));
                this.setWorldPosition(Vector3.add(
                        this.getWorldPosition(),
                        Vector3.subtract(getScene().getCamera().getWorldPosition(),
                                this.lastCameraPosition)));
                }
        }
        this.lastCameraDirection = getScene().getCamera().getWorldRotation();
        this.lastCameraPosition = getScene().getCamera().getWorldPosition();



    }
}
