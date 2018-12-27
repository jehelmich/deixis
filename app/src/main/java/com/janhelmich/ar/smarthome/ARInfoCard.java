package com.janhelmich.ar.smarthome;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Switch;

import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.janhelmich.sceneformlibraryexample.R;

public class ARInfoCard extends Node implements Node.OnTapListener {

    private static final float INFO_CARD_Y_POS_COEFF = 0.55f;

    private float heightCoefficient = 0.0f;
    private float rotateCoefficient = 5.0f;
    private boolean movable = false;

    private final Context context;


    public ARInfoCard(Context context) {
        super();
        this.context = context;
        setOnTapListener(this);
    }

    @Override
    public void onActivate() {
        if (getScene() == null) {
            throw new IllegalStateException("Scene is null!");
        }

        this.setLocalPosition(new Vector3(0.0f, INFO_CARD_Y_POS_COEFF, 0.0f));


        ViewRenderable.builder()
                .setView(context, R.layout.node_controls)
                .build()
                .thenAccept(
                        (renderable) -> {
                            this.setRenderable(renderable);
                            SeekBar heightSeekBar = renderable.getView().findViewById(R.id.heightSeekBar);
                            heightSeekBar.setProgress((int) (this.heightCoefficient * 10.0f));
                            heightSeekBar.setOnSeekBarChangeListener(
                                    new SeekBar.OnSeekBarChangeListener() {
                                        @Override
                                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                            float ratio = (float) progress / (float) heightSeekBar.getMax();
                                            heightCoefficient = (ratio * 10.0f);
                                            getParentDevice().changeHeight(heightCoefficient);
                                        }

                                        @Override
                                        public void onStartTrackingTouch(SeekBar seekBar) {
                                        }

                                        @Override
                                        public void onStopTrackingTouch(SeekBar seekBar) {
                                        }
                                    });

                            SeekBar rotationSeekBar = renderable.getView().findViewById(R.id.rotationSeekBar);
                            rotationSeekBar.setProgress((int) (this.rotateCoefficient * 10.0f));
                            rotationSeekBar.setOnSeekBarChangeListener(
                                    new SeekBar.OnSeekBarChangeListener() {
                                        @Override
                                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                            float ratio = (float) progress / (float) rotationSeekBar.getMax();
                                            rotateCoefficient = (ratio * 10.0f);
                                            getParentDevice().changeRotation(rotateCoefficient);
                                        }

                                        @Override
                                        public void onStartTrackingTouch(SeekBar seekBar) {
                                        }

                                        @Override
                                        public void onStopTrackingTouch(SeekBar seekBar) {
                                        }
                                    });

                            CheckBox movableCheckBox = renderable.getView().findViewById(R.id.movableCheckBox);
                            movableCheckBox.setChecked(this.getParentDevice().getMovable());
                            movableCheckBox.setOnCheckedChangeListener(
                                    (buttonView, isChecked) -> {
                                        movable = isChecked;
                                        getParentDevice().setMovable(isChecked);
                                    }
                            );

                            Switch movableSwitch = renderable.getView().findViewById(R.id.movableSwitch);
                            movableSwitch.setChecked(this.getParentDevice().getMovable());
                            movableSwitch.setOnCheckedChangeListener(
                                    (buttonView, isChecked) -> {
                                        getParentDevice().setMovable(isChecked);
                                        Log.i("SWITCH", "Switch changed to " + isChecked);

                                    });
                        })
                .exceptionally(
                        (throwable) -> {
                            throw new AssertionError("Could not load plane card view.", throwable);
                        });
    }


    /**
     * Due to final implementation of getParent, must always take this as a child of
     * an ARDevice node.
     *
     * @return Parent ARDevice object
     */
    public ARDevice getParentDevice() {
        return (ARDevice) this.getParent();
    }

    @Override
    public void onTap(HitTestResult hitTestResult, MotionEvent motionEvent) {

    }
}
