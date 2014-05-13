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
package com.oracle.graal.test;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import org.junit.*;
import org.junit.internal.*;

/**
 * Base class for Graal tests.
 * <p>
 * This contains common utility methods and classes that are used in tests.
 */
public class GraalTest {

    protected Method getMethod(String methodName) {
        return getMethod(getClass(), methodName);
    }

    protected Method getMethod(Class<?> clazz, String methodName) {
        Method found = null;
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName)) {
                Assert.assertNull(found);
                found = m;
            }
        }
        if (found == null) {
            /* Now look for non-public methods (but this does not look in superclasses). */
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    Assert.assertNull(found);
                    found = m;
                }
            }
        }
        if (found != null) {
            return found;
        } else {
            throw new RuntimeException("method not found: " + methodName);
        }
    }

    protected Method getMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("method not found: " + methodName + "" + Arrays.toString(parameterTypes));
        }
    }

    /**
     * Compares two given objects for {@linkplain Assert#assertEquals(Object, Object) equality}.
     * Does a deep copy equality comparison if {@code expected} is an array.
     */
    protected void assertEquals(Object expected, Object actual) {
        if (expected != null && expected.getClass().isArray()) {
            Assert.assertTrue(expected != null);
            Assert.assertTrue(actual != null);
            Assert.assertEquals(expected.getClass(), actual.getClass());
            if (expected instanceof int[]) {
                Assert.assertArrayEquals((int[]) expected, (int[]) actual);
            } else if (expected instanceof byte[]) {
                Assert.assertArrayEquals((byte[]) expected, (byte[]) actual);
            } else if (expected instanceof char[]) {
                Assert.assertArrayEquals((char[]) expected, (char[]) actual);
            } else if (expected instanceof short[]) {
                Assert.assertArrayEquals((short[]) expected, (short[]) actual);
            } else if (expected instanceof float[]) {
                Assert.assertArrayEquals((float[]) expected, (float[]) actual, 0.0f);
            } else if (expected instanceof long[]) {
                Assert.assertArrayEquals((long[]) expected, (long[]) actual);
            } else if (expected instanceof double[]) {
                Assert.assertArrayEquals((double[]) expected, (double[]) actual, 0.0d);
            } else if (expected instanceof boolean[]) {
                new ExactComparisonCriteria().arrayEquals(null, expected, actual);
            } else if (expected instanceof Object[]) {
                Assert.assertArrayEquals((Object[]) expected, (Object[]) actual);
            } else {
                Assert.fail("non-array value encountered: " + expected);
            }
        } else {
            Assert.assertEquals(expected, actual);
        }
    }

    @SuppressWarnings("serial")
    public static class MultiCauseAssertionError extends AssertionError {

        private Throwable[] causes;

        public MultiCauseAssertionError(String message, Throwable... causes) {
            super(message);
            this.causes = causes;
        }

        @Override
        public void printStackTrace(PrintStream out) {
            super.printStackTrace(out);
            int num = 0;
            for (Throwable cause : causes) {
                if (cause != null) {
                    out.print("cause " + (num++));
                    cause.printStackTrace(out);
                }
            }
        }

        @Override
        public void printStackTrace(PrintWriter out) {
            super.printStackTrace(out);
            int num = 0;
            for (Throwable cause : causes) {
                if (cause != null) {
                    out.print("cause " + (num++) + ": ");
                    cause.printStackTrace(out);
                }
            }
        }
    }
}
