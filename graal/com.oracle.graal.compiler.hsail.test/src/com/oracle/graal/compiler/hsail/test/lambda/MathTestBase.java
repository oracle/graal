/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests call to Math.tan(double)
 */
public abstract class MathTestBase extends GraalKernelTester {

    abstract String getInputString(int idx);

    // standard ulps requirement for these tests is one ULP
    @Override
    protected int ulpsDelta() {
        return 1;
    }

    // if logging of DeepEquals is set, override assertDeepEquals to just log all input and output
    // with ulps errors if any
    @Override
    protected void assertDeepEquals(String message, Object expected, Object actual, int ulpsDelta) {
        try (Scope s = Debug.scope("DeepEquals")) {
            if (Debug.isLogEnabled()) {
                if (expected != null && actual != null) {
                    Class<?> expectedClass = expected.getClass();
                    Class<?> actualClass = actual.getClass();
                    Assert.assertEquals(message, expectedClass, actualClass);
                    if (expectedClass.isArray()) {
                        if (expected instanceof double[]) {
                            double[] ae = (double[]) expected;
                            double[] aa = (double[]) actual;
                            for (int i = 0; i < ae.length; i++) {
                                double de = ae[i];
                                double da = aa[i];
                                String ulpsStr = "";
                                if (!Double.isNaN(de) && Double.isFinite(de)) {
                                    double absdiff = Math.abs(de - da);
                                    double absdiffUlps = absdiff / Math.ulp(de);
                                    ulpsStr = ", absDiffUlps=" + absdiffUlps;
                                }
                                Debug.log(i + "| input=" + getInputString(i) + ", expected=" + de + ", actual=" + da + ulpsStr);
                            }
                            return;
                        } else if (expected instanceof float[]) {
                            float[] ae = (float[]) expected;
                            float[] aa = (float[]) actual;
                            for (int i = 0; i < ae.length; i++) {
                                float fe = ae[i];
                                float fa = aa[i];
                                String ulpsStr = "";
                                if (!Float.isNaN(fe) && Float.isFinite(fe)) {
                                    float absdiff = Math.abs(fe - fa);
                                    float absdiffUlps = absdiff / Math.ulp(fe);
                                    ulpsStr = ", absDiffUlps=" + absdiffUlps;
                                }
                                Debug.log(i + "| input=" + getInputString(i) + ", expected=" + fe + ", actual=" + fa + ulpsStr);
                            }
                            return;
                        }
                    }
                }
            }
        }
        super.assertDeepEquals(message, expected, actual, ulpsDelta);
    }

}
