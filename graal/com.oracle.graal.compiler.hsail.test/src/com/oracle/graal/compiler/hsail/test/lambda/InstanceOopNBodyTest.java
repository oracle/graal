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

/**
 * Tests OopStream NBody as an instance lambda.
 */
public class InstanceOopNBodyTest extends GraalKernelTester {

    static final int bodies = 1024;
    static final float delT = .005f;
    static final float espSqr = 1.0f;
    static final float mass = 5f;
    static final int width = 768;
    static final int height = 768;

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
            float accx = 0.f;
            float accy = 0.f;
            float accz = 0.f;
            Body inb = in_bodies[gid];
            Body outb = out_bodies[gid];
            float myPosx = inb.getX();
            float myPosy = inb.getY();
            float myPosz = inb.getZ();

            for (Body b : in_bodies) {
                final float dx = b.getX() - myPosx;
                final float dy = b.getY() - myPosy;
                final float dz = b.getZ() - myPosz;
                final float invDist = 1.0f / (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz) + espSqr);
                final float s = b.getM() * invDist * invDist * invDist;
                accx = accx + (s * dx);
                accy = accy + (s * dy);
                accz = accz + (s * dz);
            }

            accx = accx * delT;
            accy = accy * delT;
            accz = accz * delT;
            outb.setX(myPosx + (inb.getVx() * delT) + (accx * .5f * delT));
            outb.setY(myPosy + (inb.getVy() * delT) + (accy * .5f * delT));
            outb.setZ(myPosz + (inb.getVz() * delT) + (accz * .5f * delT));

            outb.setVx(inb.getVx() + accx);
            outb.setVy(inb.getVy() + accy);
            outb.setVz(inb.getVz() + accz);
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
