/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.gpu.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.ptx.*;

/**
 * Tests that a {@linkplain PTXWrapperBuilder PTX kernel wrapper} deoptimizes if the kernel is
 * invalid.
 */
public class PTXMethodInvalidation1Test extends PTXTest {

    @Test
    public void test() {
        test("testSnippet", 100);
    }

    @Override
    protected HotSpotNmethod installKernel(ResolvedJavaMethod method, ExternalCompilationResult ptxCode) {
        HotSpotNmethod ptxKernel = super.installKernel(method, ptxCode);
        ptxKernel.invalidate();
        return ptxKernel;
    }

    int f = 42;

    public int testSnippet(int delta) {
        return f + delta;
    }
}
