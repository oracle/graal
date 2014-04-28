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

import org.junit.*;
import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;
import com.oracle.graal.compiler.hsail.test.Vec3;

/**
 * Tests Oop NBody calling a method that returns acceleration.
 */
public class InstanceOopNBodyAccTest extends GraalKernelTester {

    static final int bodies = 1024;
    static final float delT = .005f;
    static final float espSqr = 1.0f;
    static final float mass = 5f;
    static final int width = 768;
    static final int height = 768;

    static class Body extends com.oracle.graal.compiler.hsail.test.lambda.Body {

        public Body(float x, float y, float z, float m) {
            super(x, y, z, m);
        }

        public Vec3 computeAcc(Body[] in_bodies, float espSqr1, float delT1) {
            float accx = 0.f;
            float accy = 0.f;
            float accz = 0.f;
            float myPosx = x;
            float myPosy = y;
            float myPosz = z;

            for (int b = 0; b < in_bodies.length; b++) {
                float dx = in_bodies[b].getX() - myPosx;
                float dy = in_bodies[b].getY() - myPosy;
                float dz = in_bodies[b].getZ() - myPosz;
                float invDist = 1.0f / (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz) + espSqr1);
                float s = in_bodies[b].getM() * invDist * invDist * invDist;
                accx = accx + (s * dx);
                accy = accy + (s * dy);
                accz = accz + (s * dz);
            }

            // now return acc as a Vec3
            return new Vec3(accx * delT1, accy * delT1, accz * delT1);
        }
    }

    @Result Body[] in_bodies = new Body[bodies];
    @Result Body[] out_bodies = new Body[bodies];

    static Body[] seed_bodies = new Body[bodies];
    static {
        final float maxDist = width / 4;
        for (int body = 0; body < bodies; body++) {
            final float theta = (float) (Math.random() * Math.PI * 2);
            final float phi = (float) (Math.random() * Math.PI * 2);
            final float radius = (float) (Math.random() * maxDist);
            float x = (float) (radius * Math.cos(theta) * Math.sin(phi)) + width / 2;
            float y = (float) (radius * Math.sin(theta) * Math.sin(phi)) + height / 2;
            float z = (float) (radius * Math.cos(phi));
            seed_bodies[body] = new Body(x, y, z, mass);
        }
    }

    @Override
    public void runTest() {
        System.arraycopy(seed_bodies, 0, in_bodies, 0, seed_bodies.length);
        for (int b = 0; b < bodies; b++) {
            out_bodies[b] = new Body(0, 0, 0, mass);
        }
        // no local copies of arrays so we make it an instance lambda

        dispatchLambdaKernel(bodies, (gid) -> {
            Body bin = in_bodies[gid];
            Body bout = out_bodies[gid];
            Vec3 acc = bin.computeAcc(in_bodies, espSqr, delT);

            float myPosx = bin.getX();
            float myPosy = bin.getY();
            float myPosz = bin.getZ();
            bout.setX(myPosx + (bin.getVx() * delT) + (acc.x * .5f * delT));
            bout.setY(myPosy + (bin.getVy() * delT) + (acc.y * .5f * delT));
            bout.setZ(myPosz + (bin.getVz() * delT) + (acc.z * .5f * delT));

            bout.setVx(bin.getVx() + acc.x);
            bout.setVy(bin.getVy() + acc.y);
            bout.setVz(bin.getVz() + acc.z);
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
