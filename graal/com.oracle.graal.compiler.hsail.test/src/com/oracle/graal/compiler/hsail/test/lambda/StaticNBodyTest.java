/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.graal.compiler.hsail.test.lambda;

import java.util.*;
import org.junit.*;
import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

/**
 * Tests a static lambda version of nbody.
 */
public class StaticNBodyTest extends GraalKernelTester {

    static final int bodies = 1024;
    static final float delT = .005f;
    static final float espSqr = 1.0f;
    static final float mass = 5f;
    static final int width = 768;
    static final int height = 768;

    @Result float[] inXyz = new float[bodies * 3]; // positions xy and z of bodies

    @Result float[] outXyz = new float[bodies * 3]; // positions xy and z of bodies

    @Result float[] inVxyz = new float[bodies * 3]; // velocity component of x,y and z of
    // bodies

    @Result float[] outVxyz = new float[bodies * 3];

    static float[] seedXyz = new float[bodies * 3];
    static {
        final float maxDist = width / 4;
        for (int body = 0; body < (bodies * 3); body += 3) {
            final float theta = (float) (Math.random() * Math.PI * 2);
            final float phi = (float) (Math.random() * Math.PI * 2);
            final float radius = (float) (Math.random() * maxDist);
            seedXyz[body + 0] = (float) (radius * Math.cos(theta) * Math.sin(phi)) + width / 2;
            seedXyz[body + 1] = (float) (radius * Math.sin(theta) * Math.sin(phi)) + height / 2;
            seedXyz[body + 2] = (float) (radius * Math.cos(phi));
        }
    }

    @Override
    public void runTest() {
        System.arraycopy(seedXyz, 0, inXyz, 0, seedXyz.length);
        Arrays.fill(outXyz, 0f);
        Arrays.fill(outVxyz, 0f);
        Arrays.fill(inVxyz, 0f);

        // local copies for a static lambda
        float[] inXyz1 = this.inXyz;
        float[] outXyz1 = this.outXyz;
        float[] inVxyz1 = this.inVxyz;
        float[] outVxyz1 = this.outVxyz;

        dispatchLambdaKernel(bodies, (gid) -> {
            final int count = bodies * 3;
            final int globalId = gid * 3;

            float accx = 0.f;
            float accy = 0.f;
            float accz = 0.f;
            for (int i = 0; i < count; i += 3) {
                final float dx = inXyz1[i + 0] - inXyz1[globalId + 0];
                final float dy = inXyz1[i + 1] - inXyz1[globalId + 1];
                final float dz = inXyz1[i + 2] - inXyz1[globalId + 2];
                final float invDist = (float) (1.0 / (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz) + espSqr)));
                accx += mass * invDist * invDist * invDist * dx;
                accy += mass * invDist * invDist * invDist * dy;
                accz += mass * invDist * invDist * invDist * dz;
            }
            accx *= delT;
            accy *= delT;
            accz *= delT;
            outXyz1[globalId + 0] = inXyz1[globalId + 0] + (inVxyz1[globalId + 0] * delT) + (accx * .5f * delT);
            outXyz1[globalId + 1] = inXyz1[globalId + 1] + (inVxyz1[globalId + 1] * delT) + (accy * .5f * delT);
            outXyz1[globalId + 2] = inXyz1[globalId + 2] + (inVxyz1[globalId + 2] * delT) + (accz * .5f * delT);

            outVxyz1[globalId + 0] = inVxyz1[globalId + 0] + accx;
            outVxyz1[globalId + 1] = inVxyz1[globalId + 1] + accy;
            outVxyz1[globalId + 2] = inVxyz1[globalId + 2] + accz;
        });
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }

    @Test
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }

}
