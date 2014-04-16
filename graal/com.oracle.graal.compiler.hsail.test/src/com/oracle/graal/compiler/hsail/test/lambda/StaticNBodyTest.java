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

    @Result float[] in_xyz = new float[bodies * 3]; // positions xy and z of bodies

    @Result float[] out_xyz = new float[bodies * 3]; // positions xy and z of bodies

    @Result float[] in_vxyz = new float[bodies * 3]; // velocity component of x,y and z of
    // bodies

    @Result float[] out_vxyz = new float[bodies * 3];

    static float[] seed_xyz = new float[bodies * 3];
    static {
        final float maxDist = width / 4;
        for (int body = 0; body < (bodies * 3); body += 3) {
            final float theta = (float) (Math.random() * Math.PI * 2);
            final float phi = (float) (Math.random() * Math.PI * 2);
            final float radius = (float) (Math.random() * maxDist);
            seed_xyz[body + 0] = (float) (radius * Math.cos(theta) * Math.sin(phi)) + width / 2;
            seed_xyz[body + 1] = (float) (radius * Math.sin(theta) * Math.sin(phi)) + height / 2;
            seed_xyz[body + 2] = (float) (radius * Math.cos(phi));
        }
    }

    @Override
    public void runTest() {
        System.arraycopy(seed_xyz, 0, in_xyz, 0, seed_xyz.length);
        Arrays.fill(out_xyz, 0f);
        Arrays.fill(out_vxyz, 0f);
        Arrays.fill(in_vxyz, 0f);

        // local copies for a static lambda
        float[] in_xyz1 = this.in_xyz;
        float[] out_xyz1 = this.out_xyz;
        float[] in_vxyz1 = this.in_vxyz;
        float[] out_vxyz1 = this.out_vxyz;

        dispatchLambdaKernel(bodies, (gid) -> {
            final int count = bodies * 3;
            final int globalId = gid * 3;

            float accx = 0.f;
            float accy = 0.f;
            float accz = 0.f;
            for (int i = 0; i < count; i += 3) {
                final float dx = in_xyz1[i + 0] - in_xyz1[globalId + 0];
                final float dy = in_xyz1[i + 1] - in_xyz1[globalId + 1];
                final float dz = in_xyz1[i + 2] - in_xyz1[globalId + 2];
                final float invDist = (float) (1.0 / (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz) + espSqr)));
                accx += mass * invDist * invDist * invDist * dx;
                accy += mass * invDist * invDist * invDist * dy;
                accz += mass * invDist * invDist * invDist * dz;
            }
            accx *= delT;
            accy *= delT;
            accz *= delT;
            out_xyz1[globalId + 0] = in_xyz1[globalId + 0] + (in_vxyz1[globalId + 0] * delT) + (accx * .5f * delT);
            out_xyz1[globalId + 1] = in_xyz1[globalId + 1] + (in_vxyz1[globalId + 1] * delT) + (accy * .5f * delT);
            out_xyz1[globalId + 2] = in_xyz1[globalId + 2] + (in_vxyz1[globalId + 2] * delT) + (accz * .5f * delT);

            out_vxyz1[globalId + 0] = in_vxyz1[globalId + 0] + accx;
            out_vxyz1[globalId + 1] = in_vxyz1[globalId + 1] + accy;
            out_vxyz1[globalId + 2] = in_vxyz1[globalId + 2] + accz;
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
