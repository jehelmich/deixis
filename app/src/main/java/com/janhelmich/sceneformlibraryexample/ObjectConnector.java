package com.janhelmich.sceneformlibraryexample;

import android.content.Context;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;

public class ObjectConnector {

    /**
     * A method to draw a line between two Nodes and attaches the line to the second one.
     * as well.
     * @param n1 Node at which the line ends
     * @param n2 Node at which the line starts and gets attached as a child
     * @param context Context required for the use in MaterialFactory
     */
    public void addLine(Node n1, Node n2, Context context) {

        Vector3 point1, point2;
        point1 = n1.getWorldPosition();
        point2 = n2.getWorldPosition();
        Node line = new Node();

        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

        final Color c = new Color(0.0f, 0.0f, 1.0f);

        final Renderable[] lineRenderable = new Renderable[1];
        MaterialFactory.makeOpaqueWithColor(context, c)
                .thenAccept(
                        material -> {
                            lineRenderable[0] = ShapeFactory.makeCube(new Vector3(.01f, .01f, difference.length()),
                                    Vector3.zero(), material);
                        });

        line.setParent(n2);
        line.setRenderable(lineRenderable[0]);
        line.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
        line.setWorldRotation(rotationFromAToB);
    }
}
