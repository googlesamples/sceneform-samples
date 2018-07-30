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
