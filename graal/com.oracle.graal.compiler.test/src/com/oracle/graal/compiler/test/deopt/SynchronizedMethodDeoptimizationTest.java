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
package com.oracle.graal.compiler.test.deopt;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;

/**
 * In the following tests, we try to deoptimize out of synchronized methods.
 */
public class SynchronizedMethodDeoptimizationTest extends GraalCompilerTest {

    public static final int N = 15000;

    public static synchronized Object testMethodSynchronized(Object o) {
        if (o == null) {
            return null;
        }
        return o;
    }

    @Test
    public void test1() {
        Method method = getMethod("testMethodSynchronized");
        String testString = "test";
        for (int i = 0; i < N; ++i) {
            Assert.assertEquals(testString, testMethodSynchronized(testString));
        }
        final StructuredGraph graph = parseProfiled(method);
        final ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
        InstalledCode compiledMethod = getCode(javaMethod, graph);
        try {
            Object result = compiledMethod.executeVarargs(testString);
            Assert.assertEquals(testString, result);
        } catch (InvalidInstalledCodeException t) {
            Assert.fail("method invalidated");
        }

        try {
            Object result = compiledMethod.executeVarargs(new Object[]{null});
            Assert.assertEquals(null, result);
            Assert.assertFalse(compiledMethod.isValid());
        } catch (InvalidInstalledCodeException t) {
            Assert.fail("method invalidated");
        }
    }
}
