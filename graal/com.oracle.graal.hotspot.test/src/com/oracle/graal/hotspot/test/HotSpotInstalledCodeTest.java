/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;

public class HotSpotInstalledCodeTest extends GraalCompilerTest {

    @Test
    public void testInstallCode() {
        final ResolvedJavaMethod testJavaMethod = runtime.lookupJavaMethod(getMethod("foo"));
        final StructuredGraph graph = parse("otherFoo");
        final HotSpotInstalledCode installedCode = (HotSpotInstalledCode) getCode(testJavaMethod, graph);
        Object result = installedCode.execute(null, null, null);
        assertEquals(43, result);
        installedCode.invalidate();
        result = installedCode.execute(null, null, null);
        System.out.println(result);
    }

    @SuppressWarnings("unused")
    public static Object foo(Object a1, Object a2, Object a3) {
        return 42;
    }

    @SuppressWarnings("unused")
    public static Object otherFoo(Object a1, Object a2, Object a3) {
        return 43;
    }
}
