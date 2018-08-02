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

import com.google.ar.sceneform.rendering.Color;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A convenience class that holds a list of colors. Used to select colors for lights in the sample.
 */
public class ColorConfig {
  enum Type {
    MIXED("Mixed"),
    RED("Red"),
    YELLOW("Yellow"),
    GREEN("Green"),
    BLUE("Blue"),
    MAGENTA("Magenta"),
    WHITE("White");

    private final String displayName;

    Type(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  private static final Map<Type, List<Integer>> colors = new EnumMap<>(Type.class);
  private static boolean isMapInitialized;

  public static void initializeMap() {
    colors.put(
        Type.MIXED,
        Arrays.asList(
            android.graphics.Color.GREEN,
            android.graphics.Color.RED,
            android.graphics.Color.YELLOW,
            android.graphics.Color.BLUE));
    colors.put(Type.RED, Arrays.asList(android.graphics.Color.RED));
    colors.put(Type.YELLOW, Arrays.asList(android.graphics.Color.YELLOW));
    colors.put(Type.GREEN, Arrays.asList(android.graphics.Color.GREEN));
    colors.put(Type.BLUE, Arrays.asList(android.graphics.Color.BLUE));
    colors.put(Type.MAGENTA, Arrays.asList(android.graphics.Color.MAGENTA));
    colors.put(Type.WHITE, Arrays.asList(android.graphics.Color.WHITE));
    isMapInitialized = true;
  }

  /**
   * Returns the Color identified at a given position in the colors array, if the Enum contains a
   * single Color it will always default to returning that Color.
   */
  public static Color getColor(Type type, int pos) {
    if (!isMapInitialized) {
      initializeMap();
    }
    if (colors.get(type).size() == 1) {
      return new Color(colors.get(type).get(0));
    }
    return new Color(colors.get(type).get(pos));
  }
}
