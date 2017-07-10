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
package com.oracle.truffle.api.test.vm;

import static com.oracle.truffle.api.test.ReflectionUtils.invokeStatic;
import static com.oracle.truffle.api.test.ReflectionUtils.loadRelative;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.vm.PolyglotEngine;

public class JavaInteropDocumentationTest {
    private PolyglotEngine engine;

    private static final Class<?> SNIPPETS = loadRelative(JavaInteropDocumentationTest.class, "PolyglotEngineSnippets");

    @Before
    public void initializeEngine() {
        if (SNIPPETS != null) {
            initializeEngineImpl();
        }
    }

    private void initializeEngineImpl() {
        engine = (PolyglotEngine) invokeStatic(SNIPPETS, "configureJavaInteropWithMul");
    }

    @Test
    public void accessStaticJavaClass() {
        if (engine == null) {
            return;
        }
        Operation staticMul = engine.findGlobalSymbol("mul").as(Operation.class);
        assertNotNull("mul symbol found", staticMul);
        int res = staticMul.mul(10, 20);
        assertEquals("Result is 200", 200, res);
    }

    @Test
    public void accessInstanceMethod() {
        if (engine == null) {
            return;
        }
        Operation staticMul = engine.findGlobalSymbol("compose").as(Operation.class);
        assertNotNull("compose symbol found", staticMul);
        int res = staticMul.mul(10, 20);
        assertEquals("Result is 200", 200, res);
    }

    public interface Operation {
        int mul(int x, int y);
    }
}
