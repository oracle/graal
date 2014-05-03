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

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Base class for testing deopt when objects are in stack slots.
 */
public abstract class ObjSpillDeoptBase extends GraalKernelTester {

    abstract int getSize();

    int loopcount = 5;
    int objcount = 20;
    @Result double[] out = new double[getSize()];
    @Result double[] aux = new double[getSize()];
    DVec3[] in = new DVec3[objcount];

    public void doCompute(int gid, boolean causeDeopt) {
        int idx = gid * 2 + 7;
        DVec3 v0 = in[(idx++) % objcount];
        DVec3 v1 = in[(idx++) % objcount];
        DVec3 v2 = in[(idx++) % objcount];
        DVec3 v3 = in[(idx++) % objcount];
        DVec3 v4 = in[(idx++) % objcount];
        DVec3 v5 = in[(idx++) % objcount];
        DVec3 v6 = in[(idx++) % objcount];
        DVec3 v7 = in[(idx++) % objcount];
        DVec3 v8 = in[(idx++) % objcount];
        DVec3 v9 = in[(idx++) % objcount];
        idx += gid;
        DVec3 v10 = in[(idx++) % objcount];
        DVec3 v11 = in[(idx++) % objcount];
        DVec3 v12 = in[(idx++) % objcount];
        DVec3 v13 = in[(idx++) % objcount];
        DVec3 v14 = in[(idx++) % objcount];
        DVec3 v15 = in[(idx++) % objcount];
        DVec3 v16 = in[(idx++) % objcount];
        DVec3 v17 = in[(idx++) % objcount];
        DVec3 v18 = in[(idx++) % objcount];
        DVec3 v19 = in[(idx++) % objcount];
        double sum = 0.0;
        double sum1 = 0.0;
        double sum2 = 0.0;
        for (int i = 0; i < loopcount; i++) {
            sum1 += v0.x + v1.y + v2.z + v3.x + v4.y + v5.z + v6.x + v7.y + v8.z + v9.x + i;
            sum2 += v10.y + v11.z + v12.x + v13.y + v14.z + v15.x + v16.y + v17.z + v18.x + v19.y - i;
            sum += sum1 - sum2 + i;
            aux[gid] += sum1 + 1.2345;
        }
        if (causeDeopt) {
            aux[gid] += forceDeopt(sum1);
        }
        out[gid] += sum;
    }

    @Override
    public void runTest() {
        Arrays.fill(out, -1.0);
        for (int i = 0; i < objcount; i++) {
            in[i] = new DVec3(i / 10f, (i + 1) / 10f, (i + 2) / 10f);
        }
        dispatchMethodKernel(getSize());
    }

}
