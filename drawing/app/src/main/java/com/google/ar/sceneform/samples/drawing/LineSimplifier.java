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

import com.google.ar.sceneform.math.Vector3;
import java.util.ArrayList;
import java.util.List;

/** Smooths a given list of points */
public class LineSimplifier {

  private static final String TAG = LineSimplifier.class.getSimpleName();
  private static final float MAXIMUM_SMOOTHING_DISTANCE = 0.005f;
  private static final int POINT_SMOOTHING_INTERVAL = 10;

  private final ArrayList<Vector3> points = new ArrayList<>();
  private final ArrayList<Vector3> smoothedPoints = new ArrayList<>();

  public LineSimplifier() {}

  public void add(Vector3 point) {
    points.add(point);
    if (points.size() - smoothedPoints.size() > POINT_SMOOTHING_INTERVAL) {
      smoothPoints();
    }
  }

  private void smoothPoints() {
    List<Vector3> pointsToSmooth =
        points.subList(points.size() - POINT_SMOOTHING_INTERVAL - 1, points.size() - 1);
    ArrayList<Vector3> newlySmoothedPoints = smoothPoints(pointsToSmooth);
    points.subList(points.size() - POINT_SMOOTHING_INTERVAL - 1, points.size() - 1).clear();
    points.addAll(points.size() - 1, newlySmoothedPoints);
    smoothedPoints.addAll(newlySmoothedPoints);
  }

  // Line smoothing using the Ramer-Douglas-Peucker algorithm, modified for 3D smoothing.
  private ArrayList<Vector3> smoothPoints(List<Vector3> pointsToSmooth) {
    ArrayList<Vector3> results = new ArrayList<>();
    float maxDistance = 0.0f;
    int index = 0;
    float distance;
    int endIndex = pointsToSmooth.size() - 1;
    for (int i = 0; i < endIndex - 1; i++) {
      distance = getPerpendicularDistance(points.get(0), points.get(endIndex), points.get(i));
      if (distance > maxDistance) {
        index = i;
        maxDistance = distance;
      }
    }
    if (maxDistance > MAXIMUM_SMOOTHING_DISTANCE) {
      ArrayList<Vector3> result1 = smoothPoints(pointsToSmooth.subList(0, index));
      ArrayList<Vector3> result2 = smoothPoints(pointsToSmooth.subList(index + 1, endIndex));
      results.addAll(result1);
      results.addAll(result2);
    } else {
      results.addAll(pointsToSmooth);
    }
    return results;
  }

  private float getPerpendicularDistance(Vector3 start, Vector3 end, Vector3 point) {
    Vector3 crossProduct =
        Vector3.cross(Vector3.subtract(point, start), Vector3.subtract(point, end));
    float result = crossProduct.length() / Vector3.subtract(end, start).length();
    return result;
  }

  public List<Vector3> getPoints() {
    return points;
  }
}
