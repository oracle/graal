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

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;

/**
 * Tests for {@link JavaType}.
 */
public class TestJavaType extends TypeUniverse {

    public TestJavaType() {
    }

    @Test
    public void getKindTest() {
        for (Class<?> c : classes) {
            JavaType type = metaAccess.lookupJavaType(c);
            Kind expected = Kind.fromJavaClass(c);
            Kind actual = type.getKind();
            assertEquals(expected, actual);
        }
    }

    static class A {
        A or(A other) {
            return other;
        }
    }

    @Test
    public void testResolve() throws ClassNotFoundException {
        String classPath = System.getProperty("java.class.path");
        String[] parts = classPath.split(File.pathSeparator);
        URL[] urls = Arrays.asList(parts).stream().map(e -> asURL(e)).collect(Collectors.toList()).toArray(new URL[parts.length]);
        URLClassLoader clOne = newClassLoader(urls);
        URLClassLoader clTwo = newClassLoader(urls);

        String className = getClass().getName() + "$A";
        Class<?> aClassOne = Class.forName(className, true, clOne);
        Class<?> aClassTwo = Class.forName(getClass().getName() + "$A", true, clTwo);

        assertNotEquals(aClassOne, aClassTwo);
        assertNotEquals(aClassOne.getClassLoader(), aClassTwo.getClassLoader());

        ResolvedJavaType aTypeOne = metaAccess.lookupJavaType(aClassOne);
        ResolvedJavaType aTypeTwo = metaAccess.lookupJavaType(aClassTwo);

        assertNotEquals(aTypeOne, aTypeTwo);

        checkResolveWithoutAccessingClass(aTypeOne);
        checkResolveWithoutAccessingClass(aTypeTwo);

        assertEquals(aTypeOne.resolve(aTypeOne), aTypeOne);
        assertNotEquals(aTypeOne.resolve(aTypeTwo), aTypeOne);
        assertEquals(aTypeOne.resolve(aTypeTwo), aTypeTwo);

        assertEquals(aTypeTwo.resolve(aTypeTwo), aTypeTwo);
        assertNotEquals(aTypeTwo.resolve(aTypeOne), aTypeTwo);
        assertEquals(aTypeTwo.resolve(aTypeOne), aTypeOne);

        ResolvedJavaMethod m = ResolvedJavaTypeResolveMethodTest.getMethod(aTypeOne, "or");
        JavaType resolvedTypeOne = m.getSignature().getParameterType(0, aTypeOne);
        JavaType resolvedTypeTwo = m.getSignature().getReturnType(aTypeOne);
        JavaType unresolvedTypeOne = m.getSignature().getParameterType(0, null);
        JavaType unresolvedTypeTwo = m.getSignature().getReturnType(null);

        assertTrue(resolvedTypeOne instanceof ResolvedJavaType);
        assertTrue(resolvedTypeTwo instanceof ResolvedJavaType);
        assertFalse(unresolvedTypeOne instanceof ResolvedJavaType);
        assertFalse(unresolvedTypeTwo instanceof ResolvedJavaType);

        assertEquals(resolvedTypeOne.resolve(aTypeOne), aTypeOne);
        assertEquals(resolvedTypeOne.resolve(aTypeTwo), aTypeTwo);
        assertEquals(resolvedTypeTwo.resolve(aTypeOne), aTypeOne);
        assertEquals(resolvedTypeTwo.resolve(aTypeTwo), aTypeTwo);

        checkResolveWithoutAccessingClass(unresolvedTypeOne);
        checkResolveWithoutAccessingClass(unresolvedTypeTwo);

        assertEquals(unresolvedTypeOne.resolve(aTypeOne), aTypeOne);
        assertEquals(unresolvedTypeOne.resolve(aTypeTwo), aTypeTwo);
    }

    private static void checkResolveWithoutAccessingClass(JavaType type) {
        try {
            type.resolve(null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    private static URLClassLoader newClassLoader(URL[] urls) {
        URLClassLoader cl = new URLClassLoader(urls) {
            @Override
            protected java.lang.Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                boolean callSuper = name.startsWith("java/") || name.startsWith("java.");
                return callSuper ? super.loadClass(name, resolve) : super.findClass(name);
            }
        };
        return cl;
    }

    private static URL asURL(String e) {
        try {
            return new File(e).toURI().toURL();
        } catch (MalformedURLException e1) {
            throw new RuntimeException(e1);
        }
    }
}
