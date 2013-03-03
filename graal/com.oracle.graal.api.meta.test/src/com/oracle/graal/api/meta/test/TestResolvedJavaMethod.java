/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta.test;

import static com.oracle.graal.api.meta.test.TestJavaMethod.*;
import static java.lang.reflect.Modifier.*;
import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;

/**
 * Tests for {@link ResolvedJavaMethod}.
 */
public class TestResolvedJavaMethod {

    public TestResolvedJavaMethod() {
    }

    /**
     * @see ResolvedJavaMethod#getCode()
     */
    @Test
    public void getCodeTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            byte[] code = m.getCode();
            if (code == null) {
                assertTrue(m.getCodeSize() == 0);
            } else {
                if (isAbstract(m.getModifiers())) {
                    assertTrue(code.length == 0);
                } else if (!isNative(m.getModifiers())) {
                    assertTrue(code.length > 0);
                }
            }
        }
    }

    /**
     * @see ResolvedJavaMethod#getCodeSize()
     */
    @Test
    public void getCodeSizeTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            int codeSize = m.getCodeSize();
            if (isAbstract(m.getModifiers())) {
                assertTrue(codeSize == 0);
            } else if (!isNative(m.getModifiers())) {
                assertTrue(codeSize > 0);
            }
        }
    }

    /**
     * @see ResolvedJavaMethod#getCompiledCodeSize()
     */
    @Test
    public void getCompiledCodeSizeTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            int size = m.getCompiledCodeSize();
            if (isAbstract(m.getModifiers())) {
                assertTrue(size == 0);
            } else {
                assertTrue(size >= 0);
            }
        }
    }

    @Test
    public void getCompilationComplexityTest() {
        // TODO
    }

    @Test
    public void getMaxLocalsTest() {
        // TODO
    }

    @Test
    public void getMaxStackSizeTest() {
        // TODO
    }

    @Test
    public void getModifiersTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            int expected = e.getKey().getModifiers() & Modifier.methodModifiers();
            int actual = m.getModifiers();
            assertEquals(expected, actual);
        }
    }

    /**
     * @see ResolvedJavaMethod#isClassInitializer()
     */
    @Test
    public void isClassInitializerTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            // Class initializers are hidden from reflection
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isClassInitializer());
        }
        for (Map.Entry<Constructor, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isClassInitializer());
        }
    }

    @Test
    public void isConstructorTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isConstructor());
        }
        for (Map.Entry<Constructor, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertTrue(m.isConstructor());
        }
    }

    @Test
    public void canBeStaticallyBoundTest() {
        // TODO
    }

    @Test
    public void getExceptionHandlersTest() {
        // TODO
    }

    @Test
    public void asStackTraceElementTest() {
        // TODO
    }

    @Test
    public void getProfilingInfoTest() {
        // TODO
    }

    @Test
    public void getCompilerStorageTest() {
        // TODO
    }

    @Test
    public void getConstantPoolTest() {
        // TODO
    }

    @Test
    public void getAnnotationTest() {
        // TODO
    }

    @Test
    public void getParameterAnnotationsTest() {
        // TODO
    }

    @Test
    public void getGenericParameterTypesTest() {
        // TODO

    }

    @Test
    public void canBeInlinedTest() {
        // TODO
    }
}
