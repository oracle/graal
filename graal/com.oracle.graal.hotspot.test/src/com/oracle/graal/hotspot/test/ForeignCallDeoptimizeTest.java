/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import com.oracle.jvmci.meta.ForeignCallDescriptor;
import org.junit.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.runtime.*;

/**
 * Tests that deoptimization upon exception handling works.
 */
public class ForeignCallDeoptimizeTest extends GraalCompilerTest {

    private static boolean substitutionsInstalled;

    public ForeignCallDeoptimizeTest() {
        if (!substitutionsInstalled) {
            Replacements replacements = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders().getReplacements();
            replacements.registerSubstitutions(ForeignCallDeoptimizeTest.class, Substitutions.class);
            substitutionsInstalled = true;
        }
    }

    @ClassSubstitution(ForeignCallDeoptimizeTest.class)
    static class Substitutions {

        @MethodSubstitution(isStatic = true)
        static int testCallInt(int value) {
            return testDeoptimizeCallInt(HotSpotForeignCallsProviderImpl.TEST_DEOPTIMIZE_CALL_INT, value);
        }
    }

    /**
     * Exercise deoptimization inside of a non leaf runtime call.
     */
    @NodeIntrinsic(ForeignCallNode.class)
    static native int testDeoptimizeCallInt(@ConstantNodeParameter ForeignCallDescriptor descriptor, int value);

    public static int testCallInt(int value) {
        return value;
    }

    public static int testForeignCall(int value) {
        if (testCallInt(value) != value) {
            throw new InternalError();
        }
        return value;
    }

    @Test
    public void test1() {
        test("testForeignCall", 0);
    }

    @Test
    public void test2() {
        test("testForeignCall", -1);
    }
}
