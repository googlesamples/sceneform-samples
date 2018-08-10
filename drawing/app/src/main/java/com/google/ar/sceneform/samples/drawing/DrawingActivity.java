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

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.rendering.Texture.Sampler;
import com.google.ar.sceneform.rendering.Texture.Sampler.WrapMode;
import com.google.ar.sceneform.ux.ArFragment;
import java.util.ArrayList;
import java.util.concurrent.CompletionException;

/** Implements an AR drawing experience using Sceneform. */
public class DrawingActivity extends AppCompatActivity
    implements Scene.OnUpdateListener, Scene.OnPeekTouchListener {

  private static final String TAG = DrawingActivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;
  private static final float DRAW_DISTANCE = 0.13f;
  private static final Color WHITE = new Color(android.graphics.Color.WHITE);
  private static final Color RED = new Color(android.graphics.Color.RED);
  private static final Color GREEN = new Color(android.graphics.Color.GREEN);
  private static final Color BLUE = new Color(android.graphics.Color.BLUE);
  private static final Color BLACK = new Color(android.graphics.Color.BLACK);

  private ArFragment fragment;
  private AnchorNode anchorNode;
  private final ArrayList<Stroke> strokes = new ArrayList<>();
  private Material material;
  private Stroke currentStroke;

  LinearLayout colorPanel;
  LinearLayout controlPanel;

  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }
    setContentView(R.layout.activity_drawing);
    colorPanel = (LinearLayout) findViewById(R.id.colorPanel);
    controlPanel = (LinearLayout) findViewById(R.id.controlsPanel);

    MaterialFactory.makeOpaqueWithColor(this, WHITE)
        .thenAccept(material1 -> material = material1.makeCopy())
        .exceptionally(
            throwable -> {
              displayError(throwable);
              throw new CompletionException(throwable);
            });

    fragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
    fragment.getArSceneView().getPlaneRenderer().setEnabled(false);
    fragment.getArSceneView().getScene().addOnUpdateListener(this);
    fragment.getArSceneView().getScene().addOnPeekTouchListener(this);

    ImageView clearButton = (ImageView) findViewById(R.id.clearButton);
    clearButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            for (Stroke stroke : strokes) {
              stroke.clear();
            }
            strokes.clear();
          }
        });
    ImageView undoButton = (ImageView) findViewById(R.id.undoButton);
    undoButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            if (strokes.size() < 1) {
              return;
            }
            int lastIndex = strokes.size() - 1;
            strokes.get(lastIndex).clear();
            strokes.remove(lastIndex);
          }
        });

    setUpColorPickerUi();
  }

  private void setUpColorPickerUi() {
    ImageView colorPickerIcon = (ImageView) findViewById(R.id.colorPickerIcon);
    colorPanel.setVisibility(View.GONE);
    colorPickerIcon.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            if (controlPanel.getVisibility() == View.VISIBLE) {
              controlPanel.setVisibility(View.GONE);
              colorPanel.setVisibility(View.VISIBLE);
            }
          }
        });

    ImageView whiteCircle = (ImageView) findViewById(R.id.whiteCircle);
    whiteCircle.setOnClickListener(
        (onClick) -> {
          setColor(WHITE);
          colorPickerIcon.setImageResource(R.drawable.ic_selected_white);
        });
    ImageView redCircle = (ImageView) findViewById(R.id.redCircle);
    redCircle.setOnClickListener(
        (onClick) -> {
          setColor(RED);
          colorPickerIcon.setImageResource(R.drawable.ic_selected_red);
        });

    ImageView greenCircle = (ImageView) findViewById(R.id.greenCircle);
    greenCircle.setOnClickListener(
        (onClick) -> {
          setColor(GREEN);
          colorPickerIcon.setImageResource(R.drawable.ic_selected_green);
        });

    ImageView blueCircle = (ImageView) findViewById(R.id.blueCircle);
    blueCircle.setOnClickListener(
        (onClick) -> {
          setColor(BLUE);
          colorPickerIcon.setImageResource(R.drawable.ic_selected_blue);
        });

    ImageView blackCircle = (ImageView) findViewById(R.id.blackCircle);
    blackCircle.setOnClickListener(
        (onClick) -> {
          setColor(BLACK);
          colorPickerIcon.setImageResource(R.drawable.ic_selected_black);
        });

    ImageView rainbowCircle = (ImageView) findViewById(R.id.rainbowCircle);
    rainbowCircle.setOnClickListener(
        (onClick) -> {
          setTexture(R.drawable.rainbow_texture);
          colorPickerIcon.setImageResource(R.drawable.ic_selected_rainbow);
        });
  }

  @SuppressWarnings({"FutureReturnValueIgnored"})
  private void setTexture(int resourceId) {
    Texture.builder()
        .setSource(fragment.getContext(), resourceId)
        .setSampler(Sampler.builder().setWrapMode(WrapMode.REPEAT).build())
        .build()
        .thenCompose(
            texture -> MaterialFactory.makeOpaqueWithTexture(fragment.getContext(), texture))
        .thenAccept(material1 -> material = material1.makeCopy())
        .exceptionally(
            throwable -> {
              displayError(throwable);
              throw new CompletionException(throwable);
            });

    colorPanel.setVisibility(View.GONE);
    controlPanel.setVisibility(View.VISIBLE);
  }

  @SuppressWarnings({"FutureReturnValueIgnored"})
  private void setColor(Color color) {
    MaterialFactory.makeOpaqueWithColor(fragment.getContext(), color)
        .thenAccept(material1 -> material = material1.makeCopy())
        .exceptionally(
            throwable -> {
              displayError(throwable);
              throw new CompletionException(throwable);
            });
    colorPanel.setVisibility(View.GONE);
    controlPanel.setVisibility(View.VISIBLE);
  }

  @Override
  public void onPeekTouch(HitTestResult hitTestResult, MotionEvent tap) {
    int action = tap.getAction();
    Camera camera = fragment.getArSceneView().getScene().getCamera();
    Ray ray = camera.screenPointToRay(tap.getX(), tap.getY());
    Vector3 drawPoint = ray.getPoint(DRAW_DISTANCE);
    if (action == MotionEvent.ACTION_DOWN) {
      if (anchorNode == null) {
        ArSceneView arSceneView = fragment.getArSceneView();
        com.google.ar.core.Camera coreCamera = arSceneView.getArFrame().getCamera();
        if (coreCamera.getTrackingState() != TrackingState.TRACKING) {
          return;
        }
        Pose pose = coreCamera.getPose();
        anchorNode = new AnchorNode(arSceneView.getSession().createAnchor(pose));
        anchorNode.setParent(arSceneView.getScene());
      }
      currentStroke = new Stroke(anchorNode, material);
      strokes.add(currentStroke);
      currentStroke.add(drawPoint);
    } else if (action == MotionEvent.ACTION_MOVE && currentStroke != null) {
      currentStroke.add(drawPoint);
    }
  }

  @Override
  public void onUpdate(FrameTime frameTime) {
    com.google.ar.core.Camera camera = fragment.getArSceneView().getArFrame().getCamera();
    if (camera.getTrackingState() == TrackingState.TRACKING) {
      fragment.getPlaneDiscoveryController().hide();
    }
  }


  private void displayError(Throwable throwable) {
    Log.e(TAG, "Unable to create material", throwable);
    Toast toast = Toast.makeText(this, "Unable to create material", Toast.LENGTH_LONG);
    toast.setGravity(Gravity.CENTER, 0, 0);
    toast.show();
  }

  /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
      Log.e(TAG, "Sceneform requires Android N or later");
      Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }
    String openGlVersionString =
        ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
            .getDeviceConfigurationInfo()
            .getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
      Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
          .show();
      activity.finish();
      return false;
    }
    return true;
  }
}
