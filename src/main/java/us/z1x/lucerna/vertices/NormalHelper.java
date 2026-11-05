package us.z1x.lucerna.vertices;

/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import us.z1x.lucerna.vertices.views.QuadView;
import us.z1x.lucerna.vertices.views.TriView;

public abstract class NormalHelper {
    private NormalHelper() {
    }

    // assumes counter-clockwise winding and convex quads with co-planar points
    public static void computeFaceNormal(@NotNull Vector3f saveTo, QuadView q) {
        final float x0 = q.x(0);
        final float y0 = q.y(0);
        final float z0 = q.z(0);
        final float x1 = q.x(1);
        final float y1 = q.y(1);
        final float z1 = q.z(1);
        final float x2 = q.x(2);
        final float y2 = q.y(2);
        final float z2 = q.z(2);
        final float x3 = q.x(3);
        final float y3 = q.y(3);
        final float z3 = q.z(3);

        final float dx0 = x2 - x0;
        final float dy0 = y2 - y0;
        final float dz0 = z2 - z0;
        final float dx1 = x3 - x1;
        final float dy1 = y3 - y1;
        final float dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        saveTo.set(normX, normY, normZ);
    }

    public static int computeTangent(float normalX, float normalY, float normalZ, TriView t) {
        float x0 = t.x(0);
        float y0 = t.y(0);
        float z0 = t.z(0);

        float x1 = t.x(1);
        float y1 = t.y(1);
        float z1 = t.z(1);

        float x2 = t.x(2);
        float y2 = t.y(2);
        float z2 = t.z(2);

        float edge1x = x1 - x0;
        float edge1y = y1 - y0;
        float edge1z = z1 - z0;

        float edge2x = x2 - x0;
        float edge2y = y2 - y0;
        float edge2z = z2 - z0;

        float u0 = t.u(0);
        float v0 = t.v(0);

        float u1 = t.u(1);
        float v1 = t.v(1);

        float u2 = t.u(2);
        float v2 = t.v(2);

        float deltaU1 = u1 - u0;
        float deltaV1 = v1 - v0;
        float deltaU2 = u2 - u0;
        float deltaV2 = v2 - v0;

        float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
        float f;

        if (fdenom == 0.0) {
            f = 1.0f;
        } else {
            f = 1.0f / fdenom;
        }

        float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
        float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
        float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
        float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
        tangentx *= tcoeff;
        tangenty *= tcoeff;
        tangentz *= tcoeff;

        float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
        float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
        float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
        float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
        bitangentx *= bitcoeff;
        bitangenty *= bitcoeff;
        bitangentz *= bitcoeff;

        // predicted bitangent = tangent x normal

        float pbitangentx = tangenty * normalZ - tangentz * normalY;
        float pbitangenty = tangentz * normalX - tangentx * normalZ;
        float pbitangentz = tangentx * normalY - tangenty * normalX;

        float dot = (bitangentx * pbitangentx) + (bitangenty * pbitangenty) + (bitangentz * pbitangentz);
        float tangentW;

        if (dot < 0) {
            tangentW = -1.0F;
        } else {
            tangentW = 1.0F;
        }

        return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
    }

    private static float rsqrt(float value) {
        if (value == 0.0f) {
            // 1 / 0 counts as 1 so zero length vectors stay zero length
            return 1.0f;
        } else {
            return (float) (1.0 / Math.sqrt(value));
        }
    }
}
