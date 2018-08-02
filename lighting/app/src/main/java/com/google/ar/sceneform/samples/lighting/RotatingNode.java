/*
 * Copyright 2018 Google LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.lighting;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

/** Node that rotates at a designated speed about its y-axis */
public class RotatingNode extends Node {

  private float degreesPerSecond = 90f;
  private float speedMultiplier = 1f;

  @Override
  public void onUpdate(FrameTime frameTime) {
    float speed = (degreesPerSecond * frameTime.getDeltaSeconds());
    Quaternion deltaRot = Quaternion.axisAngle(Vector3.up(), speedMultiplier * speed);
    setLocalRotation(Quaternion.multiply(getLocalRotation(), deltaRot));
  }

  public void setDegreesPerSecond(float degreesPerSecond) {
    this.degreesPerSecond = degreesPerSecond;
  }

  public void setSpeedMultiplier(float speedMultiplier) {
    this.speedMultiplier = speedMultiplier;
  }
}
