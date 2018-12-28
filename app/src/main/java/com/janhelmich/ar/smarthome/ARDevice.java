package com.janhelmich.ar.smarthome;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.janhelmich.coeus.MainActivity;

import static com.janhelmich.coeus.MainActivity.MODE_USE;

public class ARDevice extends Node implements Node.OnTapListener {

    private String id;
    private boolean selected;

    private Renderable model;
    private Context context;
    private Node nodeVisual;
    private Node infoCard;

    private Node useInfoCard;
    private Node editInfoCard;
    
    private boolean movable;
    private Quaternion lastCameraDirection;
    private Vector3 lastCameraPosition;

    private ThreeAxisController axisController;

    public ARDevice(String id, Renderable model, Context context, ThreeAxisController axisController) {
        super();
        this.model = model;
        this.context = context;

        // Set Device ID to provided ID String
        this.id = id;

        // Initialize node to be unselected and unmovable on start
        selected = false;
        movable = false;

        this.axisController = axisController;
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

        if (useInfoCard == null) {
            useInfoCard = new ARDeviceInfoCard("TestDeviceName", "USE CASE", this.context);
            useInfoCard.setParent(this);
            useInfoCard.setEnabled(false);
        }

        if (editInfoCard == null) {
            editInfoCard = new ARDeviceInfoCard("TestDeviceName", "EDIT CASE", this.context);
            editInfoCard.setParent(this);
            editInfoCard.setEnabled(false);
        }

        infoCard = useInfoCard;
    }

    @Override
    public void onTap(HitTestResult hitTestResult, MotionEvent motionEvent) {
        Log.i("onTap", "Tapped on ARDevice " + this.id);

        infoCard.setEnabled(!infoCard.isEnabled());

        if (infoCard.isEnabled()) {
            this.axisController.setObject(this);
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

        if (MainActivity.getMode().equals(MODE_USE)) {
            infoCard = useInfoCard;
            editInfoCard.setEnabled(false);
        } else {
            infoCard = editInfoCard;
            useInfoCard.setEnabled(false);
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
