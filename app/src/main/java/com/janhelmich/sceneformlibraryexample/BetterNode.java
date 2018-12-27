package com.janhelmich.sceneformlibraryexample;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import static com.google.ar.sceneform.math.Vector3.add;

public class BetterNode extends Node {
    private Vector3 offset = new Vector3(0.0f, 0.0f, 0.0f);

    public void setOffset(Vector3 offset) {
        this.offset = offset;
    }

    public void smartSetEnabled(boolean b) {
        this.setEnabled(b);

        if (this.getParent() != null) {
            Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
            Vector3 parentPosition = this.getParent().getWorldPosition();
            Vector3 direction = Vector3.subtract(cameraPosition, parentPosition);

            Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
            this.setWorldRotation(lookRotation);

            Vector3 translation = Quaternion.rotateVector(lookRotation, offset.scaled(-1));
            this.setWorldPosition(add(this.getParent().getWorldPosition(),translation));
        }
    }

}
