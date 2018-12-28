package com.janhelmich.ar.smarthome;

import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.janhelmich.coeus.R;

public class ThreeAxisController implements View.OnClickListener {
    public final ImageButton top;
    public final ImageButton left;
    public final ImageButton right;
    public final ImageButton bottom;
    public final ImageButton up;
    public final ImageButton down;
    public final RelativeLayout dPad;
    public final RelativeLayout controlPad;

    private Node movableObject = null;

    public ThreeAxisController(ImageButton top, ImageButton left, ImageButton right, ImageButton bottom, ImageButton up, ImageButton down, RelativeLayout dPad, RelativeLayout controlPad) {
        this.top = top;
        this.left = left;
        this.right = right;
        this.bottom = bottom;
        this.up = up;
        this.down = down;
        this.dPad = dPad;
        this.controlPad = controlPad;

        this.up.setOnClickListener(this);
        this.down.setOnClickListener(this);
        this.top.setOnClickListener(this);
        this.bottom.setOnClickListener(this);
        this.left.setOnClickListener(this);
        this.right.setOnClickListener(this);
        // Default configure both pads to be invisible
        this.setInvisible();
    }

    public void setVisible() {
        this.dPad.setVisibility(RelativeLayout.VISIBLE);
        this.controlPad.setVisibility(RelativeLayout.VISIBLE);
    }

    public void setInvisible() {
        this.dPad.setVisibility(RelativeLayout.INVISIBLE);
        this.controlPad.setVisibility(RelativeLayout.INVISIBLE);
    }

    public void setObject(Node object) {
        movableObject = object;
    }


    @Override
    public void onClick(View v) {
        if (this.movableObject != null) {
            switch (v.getId()) {
                case R.id.up:
                    movableObject.setLocalPosition(Vector3.add(movableObject.getLocalPosition(), new Vector3(0.0f, 0.1f, 0.0f)));
                    break;
                case R.id.down:
                    movableObject.setLocalPosition(Vector3.add(movableObject.getLocalPosition(), new Vector3(0.0f, -0.1f, 0.0f)));
                    break;
                case R.id.left:
                    movableObject.setLocalPosition(Vector3.add(movableObject.getLocalPosition(), new Vector3(-0.1f, 0.0f, 0.0f)));
                    break;
                case R.id.right:
                    movableObject.setLocalPosition(Vector3.add(movableObject.getLocalPosition(), new Vector3(0.1f, 0.0f, 0.0f)));
                    break;
                case R.id.top:
                    movableObject.setLocalPosition(Vector3.add(movableObject.getLocalPosition(), new Vector3(0.0f, 0.0f, -0.1f)));
                    break;
                case R.id.bottom:
                    movableObject.setLocalPosition(Vector3.add(movableObject.getLocalPosition(), new Vector3(0.0f, 0.0f, 0.1f)));
                    break;
                default:
                    break;
            }
        }
    }
}
