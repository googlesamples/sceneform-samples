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
package com.google.ar.sceneform.samples.lighting;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Node.OnTapListener;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Light;
import com.google.ar.sceneform.rendering.Light.Type;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import java.util.ArrayList;
import java.util.concurrent.CompletionException;

/**
 * Shows how to use lighting features in an augmented reality (AR) application using the ARCore and
 * Sceneform APIs.
 */
public class LightingActivity extends AppCompatActivity {
  private static final String TAG = LightingActivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment fragment;

  private ModelRenderable shaderModel1;
  private ModelRenderable shaderModel2;
  private ModelRenderable boxRenderable;

  private BottomSheetDialog lightUiMenu;
  private ToggleButton toggleLights;
  private ToggleButton toggleShadows;
  private SeekBar intensityBar;
  private SeekBar orbitSpeedBar;
  private SeekBar numberOfLightsSlider;
  private Spinner lightColorSpinner;

  private AnchorNode anchorNode;

  private boolean isLightingInitialized;
  private boolean hasPlacedShapes;

  private Node modelNode1;
  private Node modelNode2;
  private Node openMenuNode;
  private final ArrayList<Node> pointlightNodes = new ArrayList<>();

  // Create (initial) colors for the lights.
  private ColorConfig.Type pointlightColorConfig = ColorConfig.Type.RED;

  // Create color for the box.
  private static final Color GREY = new Color(0.5f, 0.5f, 0.5f);
  private static final Color DARK_GREY = new Color(0.2f, 0.2f, 0.2f);

  // Create dimensions for the box.
  private static final Vector3 CUBE_SIZE_METERS = new Vector3(1.25f, .12f, 0.8f);
  private static final float MODEL_CUBE_HEIGHT_OFFSET_METERS = CUBE_SIZE_METERS.y;
  private static final float POINTLIGHT_CUBE_HEIGHT_OFFSET_METERS = .33f + CUBE_SIZE_METERS.y;

  // Create light intensity values.
  private static final int DEFAULT_LIGHT_INTENSITY = 2500;
  private static final int MAXIMUM_LIGHT_INTENSITY = 12000;
  private static final float LIGHT_FALLOFF_RADIUS = .5f;

  // Create light number values
  private static final int DEFAULT_LIGHT_NUMBER = 2;
  private static final int MAXIMUM_LIGHT_NUMBER = 4;

  private static final int MAXIMUM_MATERIAL_PROPERTY_VALUE = 100;
  private static final int MAXIMUM_LIGHT_SPEED = 100;

  private static final Quaternion ROTATION_180_DEGREES = new Quaternion(Vector3.up(), 180f);

  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    setContentView(R.layout.activity_light);

    fragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

    // Create material asynchronously, then use it to create a cube renderable on the main thread.
    MaterialFactory.makeOpaqueWithColor(this, GREY)
        .thenAccept(
            material -> {
              Material boxMaterial = material.makeCopy();
              boxMaterial.setFloat3(MaterialFactory.MATERIAL_COLOR, DARK_GREY);
              boxRenderable =
                  ShapeFactory.makeCube(
                      CUBE_SIZE_METERS, new Vector3(0, CUBE_SIZE_METERS.y / 2, 0), boxMaterial);
            })
        .exceptionally(
            throwable -> {
              displayError(throwable);
              throw new CompletionException(throwable);
            });
    ModelRenderable.builder()
        .setSource(this, R.raw.shader_d)
        .build()
        .thenAccept(
            modelRenderable -> {
              shaderModel1 = modelRenderable;
              shaderModel2 = modelRenderable.makeCopy();
            })
        .exceptionally(
            throwable -> {
              displayError(throwable);
              throw new CompletionException(throwable);
            });

    fragment.setOnTapArPlaneListener(
        (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
          // Create an anchor at tap location.
          Anchor newAnchor = hitResult.createAnchor();
          if (hasPlacedShapes) {
            // If we've already created the scene, we just need to reposition where it's anchored.
            Anchor oldAnchor = anchorNode.getAnchor();
            if (oldAnchor != null) {
              oldAnchor.detach();
            }
            anchorNode.setAnchor(newAnchor);
          } else {
            // Build the scene and position it with the anchor.
            // Create the AnchorNode.
            anchorNode = new AnchorNode(newAnchor);
            anchorNode.setParent(fragment.getArSceneView().getScene());

            modelNode1 =
                createShapeNode(
                    anchorNode,
                    shaderModel1,
                    new Vector3(0.2f, MODEL_CUBE_HEIGHT_OFFSET_METERS, 0.0f));
            modelNode1.setLocalRotation(ROTATION_180_DEGREES);

            modelNode2 =
                createShapeNode(
                    anchorNode,
                    shaderModel2,
                    new Vector3(-0.2f, MODEL_CUBE_HEIGHT_OFFSET_METERS, 0.0f));
            modelNode2.setLocalRotation(ROTATION_180_DEGREES);

            // Create ViewRenderable Menus for each model
            createMenuNode(modelNode1, new Vector3(0.0f, 0.35f, 0.0f));
            createMenuNode(modelNode2, new Vector3(0.0f, 0.35f, 0.0f));

            // Create a thin box beneath the models.
            createShapeNode(anchorNode, boxRenderable, new Vector3(0.0f, 0.0f, 0.0f));

            // Setup lights.
            setUpLights();

            hasPlacedShapes = true;
          }
        });

    setupLightingUi();
  }

  private void setUpLights() {
    Light.Builder lightBuilder =
        Light.builder(Type.POINT)
            .setFalloffRadius(LIGHT_FALLOFF_RADIUS)
            .setShadowCastingEnabled(false)
            .setIntensity(intensityBar.getProgress());

    for (int i = 0; i < 4; i++) {
      // Sets the color of and creates the light.
      lightBuilder.setColor(ColorConfig.getColor(pointlightColorConfig, i));
      Light light = lightBuilder.build();

      // Create node and set its light.
      Vector3 localPosition =
          new Vector3(-0.4f + (i * .2f), POINTLIGHT_CUBE_HEIGHT_OFFSET_METERS, 0.0f);

      RotatingNode orbit = new RotatingNode();
      orbit.setParent(anchorNode);

      Node lightNode = new Node();
      lightNode.setParent(orbit);
      lightNode.setLocalPosition(localPosition);
      lightNode.setLight(light);
      //  Check if lights are currently switched on or off, and update accordingly.
      lightNode.setEnabled(toggleLights.isChecked());

      pointlightNodes.add(lightNode);
    }

    isLightingInitialized = true;
  }

  private void setupLightingUi() {
    // Sets up the dialog box for lighting UI.
    lightUiMenu = new BottomSheetDialog(this);
    lightUiMenu.setContentView(R.layout.menu_controls);

    // Stop the lighting menu from dimming the screen.
    lightUiMenu.getWindow().getAttributes().dimAmount = 0f;
    Button launchMenuButton = (Button) findViewById(R.id.expand_controls);
    launchMenuButton.setOnClickListener(
        view -> {
          lightUiMenu.create();
          lightUiMenu.show();
        });

    // Initialize the Lights ToggleButton and default it to "On".
    toggleLights = (ToggleButton) lightUiMenu.findViewById(R.id.lightSwitchControlsButton);
    toggleLights.setChecked(true);
    // Link the state of the light toggle button in the scene to the lights (red, green)
    toggleLights.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          if (isLightingInitialized) {
            int numberOfLights = numberOfLightsSlider.getProgress();
            for (int i = 0; i < pointlightNodes.size(); i++) {
              pointlightNodes.get(i).setEnabled(isChecked && i < numberOfLights);
            }
            intensityBar.setEnabled(isChecked);
            lightColorSpinner.setEnabled(isChecked);
            orbitSpeedBar.setEnabled(isChecked);
            numberOfLightsSlider.setEnabled(isChecked);
            toggleShadows.setEnabled(isChecked);
          }
        });

    // Initialize the Shadows ToggleButton and default it to "On".
    toggleShadows = (ToggleButton) lightUiMenu.findViewById(R.id.shadowControlButton);
    toggleShadows.setChecked(true);

    toggleShadows.setOnCheckedChangeListener(
        ((buttonView, isChecked) -> {
          if (isLightingInitialized) {
            shaderModel1.setShadowCaster(isChecked);
            shaderModel2.setShadowCaster(isChecked);
            boxRenderable.setShadowCaster(isChecked);
          }
        }));

    // Initialize Seekbar to DEFAULT_LIGHT_INTENSITY and set max to MAXIMUM LIGHT INTENSITY
    intensityBar = (SeekBar) lightUiMenu.findViewById(R.id.lightIntensitySeekBar);
    intensityBar.setMax(MAXIMUM_LIGHT_INTENSITY);
    intensityBar.setProgress(DEFAULT_LIGHT_INTENSITY);

    // Link Seekbar to the light intensity
    intensityBar.setOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (isLightingInitialized) {
              for (Node node : pointlightNodes) {
                node.getLight().setIntensity(progress);
              }
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    // Initialize Light Color Spinner and populate it with preset options from ColorPair.
    lightColorSpinner = (Spinner) lightUiMenu.findViewById(R.id.colorSpinner);
    lightColorSpinner.setAdapter(
        new ArrayAdapter<com.google.ar.sceneform.samples.lighting.ColorConfig.Type>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            com.google.ar.sceneform.samples.lighting.ColorConfig.Type.values()));

    // Default the lights to be red.
    lightColorSpinner.setSelection(1);

    // Implement the logic for changing the color of the lights.
    lightColorSpinner.setOnItemSelectedListener(
        new OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            pointlightColorConfig =
                (com.google.ar.sceneform.samples.lighting.ColorConfig.Type)
                    parent.getItemAtPosition(position);
            if (isLightingInitialized) {
              for (int i = 0; i < pointlightNodes.size(); i++) {
                Light light = pointlightNodes.get(i).getLight();
                light.setColor(ColorConfig.getColor(pointlightColorConfig, i));
              }
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {
            if (isLightingInitialized) {
              for (int i = 0; i < pointlightNodes.size(); i++) {
                Light light = pointlightNodes.get(i).getLight();
                light.setColor(ColorConfig.getColor(pointlightColorConfig, i));
              }
            }
          }
        });

    // Initialize Orbit Speed Bar and set default speed to 1 and max to 10.
    orbitSpeedBar = (SeekBar) lightUiMenu.findViewById(R.id.lightRotationSpeedSlider);
    orbitSpeedBar.setMax(MAXIMUM_LIGHT_SPEED);
    orbitSpeedBar.setProgress(MAXIMUM_LIGHT_SPEED / 2);

    // // Implement logic for changing the orbit speed of the lights.
    orbitSpeedBar.setOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (isLightingInitialized) {
              for (Node node : pointlightNodes) {
                RotatingNode orbit = (RotatingNode) node.getParent();
                orbit.setSpeedMultiplier((float) progress / MAXIMUM_LIGHT_SPEED);
              }
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    // Initialize Number of Lights Slider and set max to 4.
    numberOfLightsSlider = (SeekBar) lightUiMenu.findViewById(R.id.numOfLightsSlider);
    numberOfLightsSlider.setMax(MAXIMUM_LIGHT_NUMBER);
    numberOfLightsSlider.setProgress(DEFAULT_LIGHT_NUMBER);

    numberOfLightsSlider.setOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            for (int i = 0; i < pointlightNodes.size(); i++) {
              pointlightNodes.get(i).setEnabled(toggleLights.isChecked() && i < progress);
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });
  }

  private void changeMaterialValue(String propertyName, float change, Node node) {
    Material material = node.getParent().getRenderable().getMaterial();
    material.setFloat(propertyName, change);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void addMenuToNode(Node node, Vector3 localPosition) {
    ViewRenderable.builder()
        .setView(this, R.layout.material_options_view)
        .build()
        .thenAccept(
            viewRenderable -> {
              node.setRenderable(viewRenderable);
              node.setEnabled(false);
              node.setLocalPosition(localPosition);
              node.setWorldScale(new Vector3(.65f, .65f, .5f));
              setupMaterialMenu(viewRenderable, node);
            })
        .exceptionally(
            throwable -> {
              displayError(throwable);
              throw new CompletionException(throwable);
            });
  }

  private void setupMaterialMenu(ViewRenderable viewRenderable, Node node) {
    ToggleButton metallicButton =
        (ToggleButton) viewRenderable.getView().findViewById(R.id.metallic_button);
    metallicButton.setChecked(true);
    metallicButton.setOnCheckedChangeListener(
        new OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            float newValue = (isChecked) ? 1f : 0f;
            changeMaterialValue("metallicFactor", newValue, node);
          }
        });
    SeekBar roughnessBar = (SeekBar) viewRenderable.getView().findViewById(R.id.roughness_slider);
    roughnessBar.setMax(MAXIMUM_MATERIAL_PROPERTY_VALUE);
    // Set initial roughness to half of its maximum value
    roughnessBar.setProgress(MAXIMUM_MATERIAL_PROPERTY_VALUE);
    roughnessBar.setOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float newValue = progress / (float) MAXIMUM_MATERIAL_PROPERTY_VALUE;
            changeMaterialValue("roughnessFactor", newValue, node);
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });
  }

  private Node createShapeNode(
      AnchorNode anchorNode, ModelRenderable renderable, Vector3 localPosition) {
    Node shape = new Node();
    shape.setParent(anchorNode);
    shape.setRenderable(renderable);
    shape.setLocalPosition(localPosition);
    return shape;
  }

  private Node createMenuNode(Node node, Vector3 localPosition) {
    Node menu = new Node();
    menu.setParent(node);
    addMenuToNode(menu, localPosition);
    node.setOnTapListener(
        new OnTapListener() {
          @Override
          public void onTap(HitTestResult hitTestResult, MotionEvent motionEvent) {
            menu.setEnabled(!menu.isEnabled());
            if (openMenuNode != null) {
              openMenuNode.setEnabled(false);
              openMenuNode = (openMenuNode == menu) ? null : menu;
            } else {
              openMenuNode = menu;
            }
          }
        });
    return menu;
  }

  private void displayError(Throwable throwable) {
    Log.e(TAG, "Unable to read renderable", throwable);
    Toast toast = Toast.makeText(this, "Unable to read renderable", Toast.LENGTH_LONG);
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
