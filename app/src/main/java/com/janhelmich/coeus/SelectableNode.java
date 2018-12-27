package com.janhelmich.coeus;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;

public class SelectableNode extends Node implements Node.OnTapListener {

    private final String nodeName;
    private final Renderable model;

    private Node infoCard;
    private BetterNode infoCard2;
    private Node nodeVisual;
    private final Context context;

    private static final float INFO_CARD_Y_POS_COEFF = 0.55f;

    public SelectableNode(String nodeName, Renderable model, Context context) {
        super();
        this.nodeName = nodeName;
        this.model = model;
        this.context = context;
        // setOnTapListener(this);
    }


    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    public void onActivate() {

        if (getScene() == null) {
            throw new IllegalStateException("Scene is null!");
        }

        if (infoCard == null) {
            infoCard = new Node();
            infoCard.setParent(this);
            infoCard.setEnabled(true);
            infoCard.setLocalPosition(new Vector3(0.0f, INFO_CARD_Y_POS_COEFF, 0.0f));

            ViewRenderable.builder()
                    .setView(context, R.layout.object_name)
                    .build()
                    .thenAccept(
                            (renderable) -> {
                                infoCard.setRenderable(renderable);
                                TextView textView = (TextView) renderable.getView();
                                textView.setText(this.localToWorldPoint(this.getLocalPosition()).toString());
                            })
                    .exceptionally(
                            (throwable) -> {
                                throw new AssertionError("Could not load plane card view.", throwable);
                            });
        }

        if (infoCard2 == null) {
            infoCard2 = new BetterNode();
            infoCard2.setOffset(new Vector3(0.0f, 0.2f, 0.3f));
            infoCard2.setParent(infoCard);
            infoCard2.setEnabled(false);
            infoCard2.setLocalPosition(new Vector3(0.0f, INFO_CARD_Y_POS_COEFF, 0.2f));

            ViewRenderable.builder()
                    .setView(context, R.layout.object_name)
                    .build()
                    .thenAccept(
                            (renderable) -> {
                                infoCard2.setRenderable(renderable);
                                TextView textView = (TextView) renderable.getView();
                                textView.setText("Second InfoCard");
                            })
                    .exceptionally(
                            (throwable) -> {
                                throw new AssertionError("Could not load plane card view.", throwable);
                            });
        }

//        if (addition == null) {
//            addition = new Node();
//            addition.setParent(this);
//            addition.setRenderable(model);
//            addition.setLocalScale(new Vector3(0.5f,0.5f,0.5f));
//            addition.setLocalPosition(new Vector3(-0.4f, 0.0f, 0.0f));
//            addition.setEnabled(true);
//            addition.setOnTapListener(
//                    (hitTestResult, motionEvent) -> {
//                        //addition.setLocalScale(addition.getLocalScale().scaled(1.1f));
//                        this.removeChild(addition);
//                    });
//        }

        if (nodeVisual == null) {
            nodeVisual = new Node();
            nodeVisual.setParent(this);
            nodeVisual.setRenderable(model);
            nodeVisual.setOnTapListener(this);
        }


        // Define all required onTapListeners for the objects.
        infoCard.setOnTapListener((HitTestResult hitTestResult, MotionEvent motionEvent) -> {
            infoCard2.smartSetEnabled(!infoCard2.isEnabled());
        });

        infoCard2.setOnTapListener((HitTestResult hitTestResult, MotionEvent motionEvent) -> {
            infoCard2.smartSetEnabled(!infoCard2.isEnabled());
        });

    }


    @Override
    public void onTap(HitTestResult hitTestResult, MotionEvent motionEvent) {
        Log.i("TAP", "TAPPED OBJECT!");

        if (infoCard == null) {
            return;
        }

        infoCard.setEnabled(!infoCard.isEnabled());
//        addition.setEnabled(!addition.isEnabled());
    }

    @Override
    public void onUpdate(FrameTime frameTime) {
        if (infoCard == null) {
            return;
        }

        // Typically, getScene() will never return null because onUpdate() is only called when the node
        // is in the scene.
        // However, if onUpdate is called explicitly or if the node is removed from the scene on a
        // different thread during onUpdate, then getScene may be null.
        if (getScene() == null) {
            return;
        }


        // The following code will not only calculate the direction of the look,
        // but also achieve that independent of the position of the camera, infoCard2
        // will consistently remain 0.2f in front of infoCard towards the camera.
        Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
        Vector3 cardPosition = infoCard.getWorldPosition();
        Vector3 direction = Vector3.subtract(cameraPosition, cardPosition);
        Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
        infoCard.setWorldRotation(lookRotation);

        // Calculate the xz position change required in global space to make it appear in front of
        // the other object, and normalize & scale for consistent distance.
//        Vector3 xyDirection = new Vector3(direction.x, 0.0f, direction.z).normalized().scaled(0.2f);
//        //infoCard2.setWorldPosition(add(infoCard.getWorldPosition(),xyDirection));

        // For above / beneath, simply change rotation and nothing else

        // For placing it to the left, simply rotate vector by 90 degrees and proceed.
//        Vector3 toLeft = new Vector3(-xyDirection.z, 0.0f, xyDirection.x);
//        infoCard2.setWorldPosition(add(infoCard.getWorldPosition(),toLeft.scaled(5.0f)));

        // FOr placing it to the right, invert the previous vector.
//        Vector3 toRight = toLeft.scaled(-1.0f);
        //infoCard2.setWorldPosition(add(infoCard.getWorldPosition(),toRight.scaled(5.0f)));

//        Vector3 translation = Quaternion.rotateVector(lookRotation, new Vector3(0.2f, 0.3f, -0.3f));
//
//        infoCard2.setWorldPosition(add(infoCard.getWorldPosition(),translation));

    }
}
