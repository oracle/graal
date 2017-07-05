/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.hotspot.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import com.oracle.nfi.NativeFunctionInterfaceRuntime;
import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.nfi.api.NativeFunctionInterface;

public class CompiledNativeFunctionInterfaceTest extends GraalCompilerTest {

    static {
        NativeFunctionInterface nfi = NativeFunctionInterfaceRuntime.getNativeFunctionInterface();
        NativeFunctionHandle handle = null;
        if (nfi != null && !System.getProperty("os.name").toUpperCase().contains("SUNOS")) {
            handle = nfi.getFunctionHandle("sqrt", double.class, double.class);
        }
        sqrt = handle;
    }

    private static final NativeFunctionHandle sqrt;

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        // verify that there is only a direct call, with all boxing eliminated
        FixedWithNextNode call = (FixedWithNextNode) graph.start().next();
        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Assert.assertTrue(param.hasExactlyOneUsage());
            Assert.assertTrue(param.getUsageAt(0) == call);
        }

        ReturnNode ret = (ReturnNode) call.next();
        Assert.assertTrue(ret.result() == call);
        return true;
    }

    @Test
    public void testSqrt() {
        Assume.assumeTrue("NFI not supported on this platform", sqrt != null);
        test("nativeSqrt", 42.0);
    }

    public static double nativeSqrt(double x) {
        return (Double) sqrt.call(x);
    }
}
