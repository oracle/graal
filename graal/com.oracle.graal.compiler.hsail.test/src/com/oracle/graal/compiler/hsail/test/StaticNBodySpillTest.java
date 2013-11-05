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

package com.oracle.graal.compiler.hsail.test;

import java.util.*;

import org.junit.*;

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * This version of NBody causes Graal to generate register spilling code.
 */
public class StaticNBodySpillTest extends GraalKernelTester {

    static final int bodies = 5;
    static final float delT = .005f;
    static final float espSqr = 1.0f;
    static final float mass = 5f;
    static final int width = 768;
    static final int height = 768;
    // Positions xy and z of bodies.
    @Result private float[] inxyz = new float[bodies * 3];
    // Positions xy and z of bodies.
    @Result private float[] outxyz = new float[bodies * 3]; // positions xy and z of bodies
    // Velocity component of x,y and z of bodies.
    @Result private float[] invxyz = new float[bodies * 3];
    @Result private float[] outvxyz = new float[bodies * 3];
    static float[] seedxyz = new float[bodies * 3];
    static {
        final float maxDist = width / 4;
        for (int body = 0; body < (bodies * 3); body += 3) {
            final float theta = (float) (Math.random() * Math.PI * 2);
            final float phi = (float) (Math.random() * Math.PI * 2);
            final float radius = (float) (Math.random() * maxDist);
            seedxyz[body + 0] = (float) (radius * Math.cos(theta) * Math.sin(phi)) + width / 2;
            seedxyz[body + 1] = (float) (radius * Math.sin(theta) * Math.sin(phi)) + height / 2;
            seedxyz[body + 2] = (float) (radius * Math.cos(phi));
        }
    }

    public static void run(float[] inxyz, float[] outxyz, float[] invxyz, float[] outvxyz, int gid) {
        final int count = bodies * 3;
        final int globalId = gid * 3;
        float accx = 0.f;
        float accy = 0.f;
        float accz = 0.f;
        for (int i = 0; i < count; i += 3) {
            final float dx = inxyz[i + 0] - inxyz[globalId + 0];
            final float dy = inxyz[i + 1] - inxyz[globalId + 1];
            final float dz = inxyz[i + 2] - inxyz[globalId + 2];
            final float invDist = (float) (1.0 / (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz) + espSqr)));
            accx += mass * invDist * invDist * invDist * dx;
            accy += mass * invDist * invDist * invDist * dy;
            accz += mass * invDist * invDist * invDist * dz;
        }
        accx *= delT;
        accy *= delT;
        accz *= delT;
        outxyz[globalId + 0] = inxyz[globalId + 0] + (invxyz[globalId + 0] * delT) + (accx * .5f * delT);
        outxyz[globalId + 1] = inxyz[globalId + 1] + (invxyz[globalId + 1] * delT) + (accy * .5f * delT);
        outxyz[globalId + 2] = inxyz[globalId + 2] + (invxyz[globalId + 2] * delT) + (accz * .5f * delT);
        outvxyz[globalId + 0] = invxyz[globalId + 0] + accx;
        outvxyz[globalId + 1] = invxyz[globalId + 1] + accy;
        outvxyz[globalId + 2] = invxyz[globalId + 2] + accz;
    }

    @Override
    public void runTest() {
        System.arraycopy(seedxyz, 0, inxyz, 0, seedxyz.length);
        Arrays.fill(outxyz, 0f);
        Arrays.fill(outvxyz, 0f);
        Arrays.fill(invxyz, 0f);
        dispatchMethodKernel(bodies, inxyz, outxyz, invxyz, outvxyz);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
