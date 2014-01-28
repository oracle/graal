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
package com.oracle.graal.compiler.ptx.test;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.io.*;
import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.ptx.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.ptx.*;

/**
 * Base class for PTX tests.
 */
public abstract class PTXTest extends GraalCompilerTest {

    private static PTXHotSpotBackend getPTXBackend() {
        Backend backend = runtime().getBackend(PTX.class);
        Assume.assumeTrue(backend instanceof PTXHotSpotBackend);
        return (PTXHotSpotBackend) backend;
    }

    protected ExternalCompilationResult compileKernel(ResolvedJavaMethod method) {
        return getPTXBackend().compileKernel(method, getPTXBackend().isDeviceInitialized());
    }

    protected ExternalCompilationResult compileKernel(String test) {
        return compileKernel(getMetaAccess().lookupJavaMethod(getMethod(test)));
    }

    protected HotSpotNmethod installKernel(ResolvedJavaMethod method, ExternalCompilationResult ptxCode) {
        PTXHotSpotBackend ptxBackend = getPTXBackend();
        return ptxBackend.installKernel(method, ptxCode);
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph) {
        PTXHotSpotBackend ptxBackend = getPTXBackend();
        ExternalCompilationResult ptxCode = compileKernel(method);
        Assume.assumeTrue(ptxBackend.isDeviceInitialized());
        HotSpotNmethod installedPTXCode = installKernel(method, ptxCode);
        StructuredGraph wrapper = new PTXWrapperBuilder(method, installedPTXCode, (HotSpotProviders) getProviders()).getGraph();

        // The PTX C++ layer expects a 1:1 relationship between kernel compilation
        // and kernel execution as it creates a cuContext in the former and
        // destroys it in the latter. So, each kernel installed requires a unique
        // wrapper.
        // TODO: do cuContext management properly
        boolean forceCompile = true;

        return getCode(method, wrapper, forceCompile);
    }

    protected static void compileAndPrintCode(PTXTest test) {
        for (Method m : test.getClass().getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                PrintStream out = System.out;
                out.println(name + ": \n" + new String(test.compileKernel(name).getTargetCode()));
            }
        }
    }
}
