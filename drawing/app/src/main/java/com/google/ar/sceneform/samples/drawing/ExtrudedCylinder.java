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

import com.google.ar.sceneform.math.MathHelper;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.RenderableDefinition;
import com.google.ar.sceneform.rendering.RenderableDefinition.Submesh;
import com.google.ar.sceneform.rendering.Vertex;
import com.google.ar.sceneform.rendering.Vertex.UvCoordinate;
import com.google.ar.sceneform.utilities.AndroidPreconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Utility class used to dynamically construct {@link ModelRenderable}s for extruded cylinders. */
public class ExtrudedCylinder {
  private static final String TAG = ExtrudedCylinder.class.getSimpleName();
  private static final int NUMBER_OF_SIDES = 8;

  private enum Direction {
    UP,
    DOWN
  }

  /**
   * Creates a {@link ModelRenderable} in the shape of a cylinder with the give specifications.
   *
   * @param radius the radius of the constructed cylinder
   * @param points the list of points the extruded cylinder will be constructed around
   * @param material the material to use for rendering the cylinder
   * @return renderable representing a cylinder with the given parameters
   */
  @SuppressWarnings("AndroidApiChecker")
  // CompletableFuture requires api level 24
  public static RenderableDefinition makeExtrudedCylinder(
      float radius, List<Vector3> points, Material material) {
    AndroidPreconditions.checkMinAndroidApiLevel();

    if (points.size() < 2) {
      return null;
    }

    ArrayList<Vertex> vertices = new ArrayList<>();
    ArrayList<Integer> triangleIndices = new ArrayList<>();
    ArrayList<Quaternion> rotations = new ArrayList<>();
    Vector3 desiredUp = Vector3.up();

    for (int point = 0; point < points.size() - 1; point++) {
      generateVerticesFromPoints(
          desiredUp, vertices, rotations, points.get(point + 1), points.get(point), radius);
    }

    updateConnectingPoints(vertices, points, rotations, radius);
    generateTriangleIndices(triangleIndices, points.size());
    updateEndPointUV(vertices);

    // Add start cap
    makeDisk(vertices, triangleIndices, points, 0, Direction.UP);
    // Add end cap
    makeDisk(vertices, triangleIndices, points, points.size() - 1, Direction.DOWN);

    Submesh submesh =
        Submesh.builder().setTriangleIndices(triangleIndices).setMaterial(material).build();

    RenderableDefinition renderableDefinition =
        RenderableDefinition.builder()
            .setVertices(vertices)
            .setSubmeshes(Arrays.asList(submesh))
            .build();

    return renderableDefinition;
  }

  private static void generateVerticesFromPoints(
      Vector3 desiredUp,
      List<Vertex> vertices,
      List<Quaternion> rotations,
      Vector3 firstPoint,
      Vector3 secondPoint,
      float radius) {

    final Vector3 difference = Vector3.subtract(firstPoint, secondPoint);
    Vector3 directionFromTopToBottom = difference.normalized();
    Quaternion rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, desiredUp);

    // cosTheta0 provides the angle between the rotations
    if (!rotations.isEmpty()) {
      double cosTheta0 = dot(rotations.get(rotations.size() - 1), rotationFromAToB);
      // Flip end rotation to get shortest path if needed
      if (cosTheta0 < 0.0) {
        rotationFromAToB = negated(rotationFromAToB);
      }
    }
    rotations.add(rotationFromAToB);

    directionFromTopToBottom =
        Quaternion.rotateVector(rotationFromAToB, Vector3.forward()).normalized();
    Vector3 rightDirection =
        Quaternion.rotateVector(rotationFromAToB, Vector3.right()).normalized();
    Vector3 upDirection = Quaternion.rotateVector(rotationFromAToB, Vector3.up()).normalized();
    desiredUp.set(upDirection);

    List<Vertex> bottomVertices = new ArrayList<>();

    final float halfHeight = difference.length() / 2;
    final Vector3 center = Vector3.add(firstPoint, secondPoint).scaled(.5f);

    final float thetaIncrement = (float) (2 * Math.PI) / NUMBER_OF_SIDES;
    float theta = 0;
    float cosTheta = (float) Math.cos(theta);
    float sinTheta = (float) Math.sin(theta);
    float uStep = (float) 1.0 / NUMBER_OF_SIDES;

    // Generate edge vertices along the sides of the cylinder.
    for (int edgeIndex = 0; edgeIndex <= NUMBER_OF_SIDES; edgeIndex++) {
      // Create top edge vertex
      Vector3 topPosition =
          Vector3.add(
              directionFromTopToBottom.scaled(-halfHeight),
              Vector3.add(
                  rightDirection.scaled(radius * cosTheta), upDirection.scaled(radius * sinTheta)));
      Vector3 normal =
          Vector3.subtract(topPosition, directionFromTopToBottom.scaled(-halfHeight)).normalized();
      topPosition = Vector3.add(topPosition, center);
      UvCoordinate uvCoordinate = new UvCoordinate(uStep * edgeIndex, 0);

      Vertex vertex =
          Vertex.builder()
              .setPosition(topPosition)
              .setNormal(normal)
              .setUvCoordinate(uvCoordinate)
              .build();
      vertices.add(vertex);

      // Create bottom edge vertex
      Vector3 bottomPosition =
          Vector3.add(
              directionFromTopToBottom.scaled(halfHeight),
              Vector3.add(
                  rightDirection.scaled(radius * cosTheta), upDirection.scaled(radius * sinTheta)));
      normal =
          Vector3.subtract(bottomPosition, directionFromTopToBottom.scaled(halfHeight))
              .normalized();
      bottomPosition = Vector3.add(bottomPosition, center);
      float vHeight = halfHeight * 2;
      uvCoordinate = new UvCoordinate(uStep * edgeIndex, vHeight);

      vertex =
          Vertex.builder()
              .setPosition(bottomPosition)
              .setNormal(normal)
              .setUvCoordinate(uvCoordinate)
              .build();
      bottomVertices.add(vertex);

      theta += thetaIncrement;
      cosTheta = (float) Math.cos(theta);
      sinTheta = (float) Math.sin(theta);
    }
    vertices.addAll(bottomVertices);
  }

  private static void updateConnectingPoints(
      List<Vertex> vertices, List<Vector3> points, List<Quaternion> rotations, float radius) {
    // Loop over each segment of cylinder, connecting the ends of this segment to start of the next.
    int currentSegmentVertexIndex = NUMBER_OF_SIDES + 1;
    int nextSegmentVertexIndex = currentSegmentVertexIndex + NUMBER_OF_SIDES + 1;

    for (int segmentIndex = 0; segmentIndex < points.size() - 2; segmentIndex++) {
      Vector3 influencePoint = points.get(segmentIndex + 1);

      Quaternion averagedRotation =
          lerp(rotations.get(segmentIndex), rotations.get(segmentIndex + 1), .5f);
      Vector3 rightDirection =
          Quaternion.rotateVector(averagedRotation, Vector3.right()).normalized();
      Vector3 upDirection = Quaternion.rotateVector(averagedRotation, Vector3.up()).normalized();

      for (int edgeIndex = 0; edgeIndex <= NUMBER_OF_SIDES; edgeIndex++) {
        // Connect bottom vertex of current edge to the top vertex of the edge on next segment.
        float theta = (float) (2 * Math.PI) * edgeIndex / NUMBER_OF_SIDES;
        float cosTheta = (float) Math.cos(theta);
        float sinTheta = (float) Math.sin(theta);

        // Create new position
        Vector3 position =
            Vector3.add(
                rightDirection.scaled(radius * cosTheta), upDirection.scaled(radius * sinTheta));
        Vector3 normal = position.normalized();
        position.set(Vector3.add(position, influencePoint));

        // Update position, UV, and normals of connecting vertices
        int previousSegmentVertexIndex = currentSegmentVertexIndex - NUMBER_OF_SIDES - 1;
        Vertex updatedVertex =
            Vertex.builder()
                .setPosition(position)
                .setNormal(normal)
                .setUvCoordinate(
                    new UvCoordinate(
                        vertices.get(currentSegmentVertexIndex).getUvCoordinate().x,
                        (Vector3.subtract(
                                    position,
                                    vertices.get(previousSegmentVertexIndex).getPosition())
                                .length()
                            + vertices.get(previousSegmentVertexIndex).getUvCoordinate().y)))
                .build();

        vertices.set(currentSegmentVertexIndex, updatedVertex);
        vertices.remove(nextSegmentVertexIndex);
        currentSegmentVertexIndex++;
      }
      currentSegmentVertexIndex = nextSegmentVertexIndex;
      nextSegmentVertexIndex += NUMBER_OF_SIDES + 1;
    }
  }

  private static void updateEndPointUV(List<Vertex> vertices) {
    // Update UV coordinates of ending vertices
    for (int edgeIndex = 0; edgeIndex <= NUMBER_OF_SIDES; edgeIndex++) {
      int vertexIndex = vertices.size() - edgeIndex - 1;
      Vertex currentVertex = vertices.get(vertexIndex);
      currentVertex.setUvCoordinate(
          new UvCoordinate(
              currentVertex.getUvCoordinate().x,
              (Vector3.subtract(
                          vertices.get(vertexIndex).getPosition(),
                          vertices.get(vertexIndex - NUMBER_OF_SIDES - 1).getPosition())
                      .length()
                  + vertices.get(vertexIndex - NUMBER_OF_SIDES - 1).getUvCoordinate().y)));
    }
  }

  private static void generateTriangleIndices(List<Integer> triangleIndices, int numberOfPoints) {
    // Create triangles along the sides of cylinder part
    for (int segment = 0; segment < numberOfPoints - 1; segment++) {
      int segmentVertexIndex = segment * (NUMBER_OF_SIDES + 1);
      for (int side = 0; side < NUMBER_OF_SIDES; side++) {
        int topLeft = side + segmentVertexIndex;
        int topRight = side + segmentVertexIndex + 1;
        int bottomLeft = side + NUMBER_OF_SIDES + segmentVertexIndex + 1;
        int bottomRight = side + NUMBER_OF_SIDES + segmentVertexIndex + 2;

        // First triangle of side.
        triangleIndices.add(topLeft);
        triangleIndices.add(bottomRight);
        triangleIndices.add(topRight);

        // Second triangle of side.
        triangleIndices.add(topLeft);
        triangleIndices.add(bottomLeft);
        triangleIndices.add(bottomRight);
      }
    }
  }

  private static void makeDisk(
      List<Vertex> vertices,
      List<Integer> triangleIndices,
      List<Vector3> points,
      int centerPointIndex,
      Direction direction) {

    Vector3 centerPoint = points.get(centerPointIndex);
    Vector3 nextPoint = points.get(centerPointIndex + (direction == Direction.UP ? 1 : -1));
    Vector3 normal = Vector3.subtract(centerPoint, nextPoint).normalized();
    Vertex center =
        Vertex.builder()
            .setPosition(centerPoint)
            .setNormal(normal)
            .setUvCoordinate(new UvCoordinate(.5f, .5f))
            .build();
    int centerIndex = vertices.size();
    vertices.add(center);

    int vertexPosition = centerPointIndex * (NUMBER_OF_SIDES + 1);
    for (int edge = 0; edge <= NUMBER_OF_SIDES; edge++) {
      Vertex edgeVertex = vertices.get(vertexPosition + edge);
      float theta = (float) (2 * Math.PI * edge / NUMBER_OF_SIDES);
      UvCoordinate uvCoordinate =
          new UvCoordinate((float) (Math.cos(theta) + 1f) / 2, (float) (Math.sin(theta) + 1f) / 2);
      Vertex topVertex =
          Vertex.builder()
              .setPosition(edgeVertex.getPosition())
              .setNormal(normal)
              .setUvCoordinate(uvCoordinate)
              .build();
      vertices.add(topVertex);

      if (edge != NUMBER_OF_SIDES) {
        // Add disk triangle, using direction to check which side the triangles should face
        if (direction == Direction.UP) {
          triangleIndices.add(centerIndex);
          triangleIndices.add(centerIndex + edge + 1);
          triangleIndices.add(centerIndex + edge + 2);
        } else {
          triangleIndices.add(centerIndex);
          triangleIndices.add(centerIndex + edge + 2);
          triangleIndices.add(centerIndex + edge + 1);
        }
      }
    }
  }

  /** The dot product of two Quaternions. */
  private static float dot(Quaternion lhs, Quaternion rhs) {
    return lhs.x * rhs.x + lhs.y * rhs.y + lhs.z * rhs.z + lhs.w * rhs.w;
  }

  private static Quaternion negated(Quaternion quat) {
    return new Quaternion(-quat.x, -quat.y, -quat.z, -quat.w);
  }

  private static Quaternion lerp(Quaternion a, Quaternion b, float ratio) {
    return new Quaternion(
        MathHelper.lerp(a.x, b.x, ratio),
        MathHelper.lerp(a.y, b.y, ratio),
        MathHelper.lerp(a.z, b.z, ratio),
        MathHelper.lerp(a.w, b.w, ratio));
  }
}
