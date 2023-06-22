/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test on-stack-replacement with Graal. The test manually triggers a Graal OSR-compilation which is
 * later invoked when hitting the backedge counter overflow.
 */
public class GraalOSRTest extends GraalOSRTestBase {

    @Test
    public void testOSR01() {
        try {
            testOSR(getInitialOptions(), "testReduceLoop");
        } catch (Throwable t) {
            Assert.assertEquals("OSR compilation without OSR entry loop.", t.getMessage());
        }
    }

    @Test
    public void testOSR02() {
        testOSR(getInitialOptions(), "testSequentialLoop");
    }

    @Test
    public void testOSR03() {
        testOSR(getInitialOptions(), "testNonReduceLoop");
    }

    @Test
    public void testOSR04() {
        testOSR(getInitialOptions(), "testDeoptAfterCountedLoop");
    }

    static int limit = 10000;

    public static int sideEffect;

    public static ReturnValue testReduceLoop() {
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (GraalDirectives.inCompiledCode()) {
                return ReturnValue.SUCCESS;
            }
        }
        return ReturnValue.FAILURE;
    }

    public static ReturnValue testSequentialLoop() {
        ReturnValue ret = ReturnValue.FAILURE;
        for (int i = 1; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 7 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        if (sideEffect == 123) {
            return ReturnValue.SIDE;
        }
        for (int i = 1; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 33 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return ret;
    }

    public static ReturnValue testNonReduceLoop() {
        ReturnValue ret = ReturnValue.FAILURE;
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 33 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return ret;
    }

    public static ReturnValue testDeoptAfterCountedLoop() {
        long ret = 0;
        for (int i = 0; GraalDirectives.injectBranchProbability(1, i < limit * limit); i++) {
            GraalDirectives.blackhole(i);
            ret = GraalDirectives.opaque(i);
        }
        GraalDirectives.controlFlowAnchor();
        return ret + 1 == limit * limit ? ReturnValue.SUCCESS : ReturnValue.FAILURE;
    }
}
