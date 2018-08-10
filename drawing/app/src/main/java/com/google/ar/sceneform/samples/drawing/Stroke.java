/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.drawing;

import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.RenderableDefinition;
import java.util.List;

/** Collects points to be drawn */
public class Stroke {

  private static final float CYLINDER_RADIUS = 0.005f;
  private static final float MINIMUM_DISTANCE_BETWEEN_POINTS = 0.005f;
  private static final String TAG = Stroke.class.getSimpleName();


  private final Node node = new Node();
  private final Material material;
  private final LineSimplifier lineSimplifier = new LineSimplifier();

  private AnchorNode anchorNode;
  private ModelRenderable shape;

  public Stroke(AnchorNode anchorNode, Material material) {
    this.material = material;
    this.anchorNode = anchorNode;
    node.setParent(anchorNode);
  }

  public void add(Vector3 pointInWorld) {
    Vector3 pointInLocal = anchorNode.worldToLocalPoint(pointInWorld);
    List<Vector3> points = lineSimplifier.getPoints();
    if (getNumOfPoints() < 1) {
      lineSimplifier.add(pointInLocal);
      return;
    }

    Vector3 prev = points.get(points.size() - 1);
    Vector3 diff = Vector3.subtract(prev, pointInLocal);
    if (diff.length() < MINIMUM_DISTANCE_BETWEEN_POINTS) {
      return;
    }

    lineSimplifier.add(pointInLocal);

    RenderableDefinition renderableDefinition =
        ExtrudedCylinder.makeExtrudedCylinder(CYLINDER_RADIUS, points, material);
    if (shape == null) {
      shape = ModelRenderable.builder().setSource(renderableDefinition).build().join();
      node.setRenderable(shape);
    } else {
      shape.updateFromDefinition(renderableDefinition);
    }
  }

  public void clear() {
    lineSimplifier.getPoints().clear();
    node.setParent(null);
  }

  public int getNumOfPoints() {
    return lineSimplifier.getPoints().size();
  }

  @Override
  public String toString() {
    String result = "Vector3[] strokePoints = {";
    for (Vector3 vector3 : lineSimplifier.getPoints()) {
      result += ("new Vector3(" + vector3.x + "f, " + vector3.y + "f, " + vector3.z + "f),\n ");
    }
    return result.substring(0, result.length() - 3) + "};";
  }
}
