/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.ptx.test;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.ptx.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.ptx.*;

/**
 * Tests the mechanism for launching a PTX kernel method via wrapper code.
 */
public class PTXLaunchKernelTest extends GraalCompilerTest {

    /**
     * Compiles and installs PTX kernel code for a given method.
     */
    StructuredGraph getKernelGraph(final ResolvedJavaMethod method) {
        Backend backend = runtime().getBackend(PTX.class);
        Assume.assumeTrue(backend instanceof PTXHotSpotBackend);
        PTXHotSpotBackend ptxBackend = (PTXHotSpotBackend) backend;
        Assume.assumeTrue(ptxBackend.isDeviceInitialized());
        return new PTXGraphProducer(runtime().getHostBackend(), ptxBackend) {
            @Override
            protected boolean canOffloadToGPU(ResolvedJavaMethod m) {
                return m == method;
            }
        }.getGraphFor(method);
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph) {
        return super.getCode(method, getKernelGraph(method));
    }

    @Test
    public void testStaticIntKernel() {
        test("staticIntKernel", 'a', 42);
    }

    @Test
    public void testVirtualIntKernel() {
        test("virtualIntKernel", 'a', 42);
    }

    public static int staticIntKernel(char p0, int p1) {
        return p1 + p0;
    }

    public int virtualIntKernel(char p0, int p1) {
        return p1 + p0;
    }
}
