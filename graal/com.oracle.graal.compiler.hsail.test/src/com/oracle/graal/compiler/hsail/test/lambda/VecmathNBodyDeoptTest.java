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
import javax.vecmath.*;

/**
 * Tests NBody algorithm using the javax.vecmath package (all objects non-escaping).
 */
public class VecmathNBodyDeoptTest extends GraalKernelTester {
    static final int bodies = 1024;
    static final float delT = .005f;
    static final float espSqr = 1.0f;
    static final float mass = 5f;
    static final int width = 768;
    static final int height = 768;

    static class Body extends Vector3f {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        public Body(float _x, float _y, float _z, float _m) {
            super(_x, _y, _z);
            m = _m;
            v = new Vector3f(0, 0, 0);
        }

        float m;
        Vector3f v;

        public float getM() {
            return m;
        }

        public Vector3f computeAcc(Body[] in_bodies, float espSqr1, float delT1) {
            Vector3f acc = new Vector3f();

            for (Body b : in_bodies) {
                Vector3f d = new Vector3f();
                d.sub(b, this);
                float invDist = 1.0f / (float) Math.sqrt(d.lengthSquared() + espSqr1);
                float s = b.getM() * invDist * invDist * invDist;
                acc.scaleAdd(s, d, acc);
            }

            // now return acc scaled by delT
            acc.scale(delT1);
            return acc;
        }
    }

    @Result Body[] in_bodies = new Body[bodies];
    @Result Body[] out_bodies = new Body[bodies];

    static Body[] seed_bodies = new Body[bodies];

    static {
        java.util.Random randgen = new Random(0);
        final float maxDist = width / 4;
        for (int body = 0; body < bodies; body++) {
            final float theta = (float) (randgen.nextFloat() * Math.PI * 2);
            final float phi = (float) (randgen.nextFloat() * Math.PI * 2);
            final float radius = randgen.nextFloat() * maxDist;
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
            Body inb = in_bodies[gid];
            Body outb = out_bodies[gid];
            Vector3f acc = inb.computeAcc(in_bodies, espSqr, delT);

            Vector3f tmpPos = new Vector3f();
            tmpPos.scaleAdd(delT, inb.v, inb);
            if (gid == bodies / 2) {
                tmpPos.x += forceDeopt(gid);
            }
            tmpPos.scaleAdd(0.5f * delT, acc, tmpPos);
            outb.set(tmpPos);

            outb.v.add(inb.v, acc);
        });
    }

    @Override
    protected boolean supportsRequiredCapabilities() {
        return (canHandleDeoptVirtualObjects() && canDeoptimize());
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
