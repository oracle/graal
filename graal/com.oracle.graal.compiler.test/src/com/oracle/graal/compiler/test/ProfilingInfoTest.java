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
package com.oracle.graal.compiler.test;

import java.io.*;
import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;

public class ProfilingInfoTest extends GraalCompilerTest {

    private static final int N = 100;
    private static final double DELTA = 1d / Integer.MAX_VALUE;

    @Test
    public void testBranchTakenProbability() {
        ProfilingInfo info = profile("branchProbabilitySnippet", 0);
        Assert.assertEquals(0.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(1));
        Assert.assertEquals(-1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(0, info.getExecutionCount(8));

        info = profile("branchProbabilitySnippet", 1);
        Assert.assertEquals(1.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(1));
        Assert.assertEquals(0.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(8));

        info = profile("branchProbabilitySnippet", 2);
        Assert.assertEquals(1.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(1));
        Assert.assertEquals(1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(8));

        continueProfiling(3 * N, "branchProbabilitySnippet", 0);
        Assert.assertEquals(0.25, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(4 * N, info.getExecutionCount(1));
        Assert.assertEquals(1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(8));

        resetProfile("branchProbabilitySnippet");
        Assert.assertEquals(-1.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(0, info.getExecutionCount(1));
        Assert.assertEquals(-1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(0, info.getExecutionCount(8));
    }

    public static int branchProbabilitySnippet(int value) {
        if (value == 0) {
            return -1;
        } else if (value == 1) {
            return -2;
        } else {
            return -3;
        }
    }

    @Test
    public void testSwitchProbabilities() {
        ProfilingInfo info = profile("switchProbabilitySnippet", 0);
        Assert.assertArrayEquals(new double[]{1.0, 0.0, 0.0}, info.getSwitchProbabilities(1), DELTA);

        info = profile("switchProbabilitySnippet", 1);
        Assert.assertArrayEquals(new double[]{0.0, 1.0, 0.0}, info.getSwitchProbabilities(1), DELTA);

        info = profile("switchProbabilitySnippet", 2);
        Assert.assertArrayEquals(new double[]{0.0, 0.0, 1.0}, info.getSwitchProbabilities(1), DELTA);

        resetProfile("switchProbabilitySnippet");
        Assert.assertNull(info.getSwitchProbabilities(1));
    }

    public static int switchProbabilitySnippet(int value) {
        switch (value) {
            case 0:
                return -1;
            case 1:
                return -2;
            default:
                return -3;
        }
    }

    @Test
    public void testProfileInvokeVirtual() throws NoSuchMethodException {
        testTypeProfile("invokeVirtualSnippet", 1);
        testMethodProfile("invokeVirtualSnippet", 1, "hashCode");
    }

    public static int invokeVirtualSnippet(Object obj) {
        return obj.hashCode();
    }

    @Test
    public void testTypeProfileInvokeInterface() throws NoSuchMethodException {
        testTypeProfile("invokeInterfaceSnippet", 1);
        testMethodProfile("invokeInterfaceSnippet", 1, "length");
    }

    public static int invokeInterfaceSnippet(CharSequence a) {
        return a.length();
    }

    @Test
    public void testTypeProfileCheckCast() {
        testTypeProfile("checkCastSnippet", 1);
    }

    public static Serializable checkCastSnippet(Object obj) {
        return (Serializable) obj;
    }

    @Test
    public void testTypeProfileInstanceOf() {
        testTypeProfile("instanceOfSnippet", 1);
    }

    public static boolean instanceOfSnippet(Object obj) {
        return obj instanceof Serializable;
    }

    private void testTypeProfile(String testSnippet, int bci) {
        ResolvedJavaType stringType = runtime.lookupJavaType(String.class);
        ResolvedJavaType stringBuilderType = runtime.lookupJavaType(StringBuilder.class);

        ProfilingInfo info = profile(testSnippet, "ABC");
        JavaTypeProfile typeProfile = info.getTypeProfile(bci);
        Assert.assertEquals(0.0, typeProfile.getNotRecordedProbability(), DELTA);
        Assert.assertEquals(1, typeProfile.getTypes().length);
        Assert.assertEquals(stringType, typeProfile.getTypes()[0].getType());
        Assert.assertEquals(1.0, typeProfile.getTypes()[0].getProbability(), DELTA);

        continueProfiling(testSnippet, new StringBuilder());
        typeProfile = info.getTypeProfile(bci);
        Assert.assertEquals(0.0, typeProfile.getNotRecordedProbability(), DELTA);
        Assert.assertEquals(2, typeProfile.getTypes().length);
        Assert.assertEquals(stringType, typeProfile.getTypes()[0].getType());
        Assert.assertEquals(stringBuilderType, typeProfile.getTypes()[1].getType());
        Assert.assertEquals(0.5, typeProfile.getTypes()[0].getProbability(), DELTA);
        Assert.assertEquals(0.5, typeProfile.getTypes()[1].getProbability(), DELTA);

        resetProfile(testSnippet);
        typeProfile = info.getTypeProfile(bci);
        Assert.assertNull(typeProfile);
    }

    private void testMethodProfile(String testSnippet, int bci, String expectedProfiledMethod) throws NoSuchMethodException {
        ResolvedJavaMethod stringMethod = runtime.lookupJavaMethod(String.class.getMethod(expectedProfiledMethod));
        ResolvedJavaMethod stringBuilderMethod = runtime.lookupJavaMethod(StringBuilder.class.getMethod(expectedProfiledMethod));

        ProfilingInfo info = profile(testSnippet, "ABC");
        JavaMethodProfile methodProfile = info.getMethodProfile(bci);
        Assert.assertEquals(0.0, methodProfile.getNotRecordedProbability(), DELTA);
        Assert.assertEquals(1, methodProfile.getMethods().length);
        Assert.assertEquals(stringMethod, methodProfile.getMethods()[0].getMethod());
        Assert.assertEquals(1.0, methodProfile.getMethods()[0].getProbability(), DELTA);

        continueProfiling(testSnippet, new StringBuilder());
        methodProfile = info.getMethodProfile(bci);
        Assert.assertEquals(0.0, methodProfile.getNotRecordedProbability(), DELTA);
        Assert.assertEquals(2, methodProfile.getMethods().length);
        Assert.assertEquals(stringMethod, methodProfile.getMethods()[0].getMethod());
        Assert.assertEquals(stringBuilderMethod, methodProfile.getMethods()[1].getMethod());
        Assert.assertEquals(0.5, methodProfile.getMethods()[0].getProbability(), DELTA);
        Assert.assertEquals(0.5, methodProfile.getMethods()[1].getProbability(), DELTA);

        resetProfile(testSnippet);
        methodProfile = info.getMethodProfile(bci);
        Assert.assertNull(methodProfile);
    }

    @Test
    public void testExceptionSeen() {
        // NullPointerException
        ProfilingInfo info = profile("nullPointerExceptionSnippet", 5);
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        info = profile("nullPointerExceptionSnippet", (Object) null);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(1));

        resetProfile("nullPointerExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        // ArrayOutOfBoundsException
        info = profile("arrayIndexOutOfBoundsExceptionSnippet", new int[1]);
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(2));

        info = profile("arrayIndexOutOfBoundsExceptionSnippet", new int[0]);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(2));

        resetProfile("arrayIndexOutOfBoundsExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(2));

        // CheckCastException
        info = profile("checkCastExceptionSnippet", "ABC");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        info = profile("checkCastExceptionSnippet", 5);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(1));

        resetProfile("checkCastExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        // Invoke with exception
        info = profile("invokeWithExceptionSnippet", false);
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        info = profile("invokeWithExceptionSnippet", true);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(1));

        resetProfile("invokeWithExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));
    }

    public static int nullPointerExceptionSnippet(Object obj) {
        try {
            return obj.hashCode();
        } catch (NullPointerException e) {
            return 1;
        }
    }

    public static int arrayIndexOutOfBoundsExceptionSnippet(int[] array) {
        try {
            return array[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            return 1;
        }
    }

    public static int checkCastExceptionSnippet(Object obj) {
        try {
            return ((String) obj).length();
        } catch (ClassCastException e) {
            return 1;
        }
    }

    public static int invokeWithExceptionSnippet(boolean doThrow) {
        try {
            return throwException(doThrow);
        } catch (IllegalArgumentException e) {
            return 1;
        }
    }

    private static int throwException(boolean doThrow) {
        if (doThrow) {
            throw new IllegalArgumentException();
        } else {
            return 1;
        }
    }

    @Test
    public void testNullSeen() {
        ProfilingInfo info = profile("instanceOfSnippet", 1);
        Assert.assertEquals(TriState.FALSE, info.getNullSeen(1));

        continueProfiling("instanceOfSnippet", "ABC");
        Assert.assertEquals(TriState.FALSE, info.getNullSeen(1));

        continueProfiling("instanceOfSnippet", (Object) null);
        Assert.assertEquals(TriState.TRUE, info.getNullSeen(1));

        continueProfiling("instanceOfSnippet", 0.0);
        Assert.assertEquals(TriState.TRUE, info.getNullSeen(1));

        resetProfile("instanceOfSnippet");
        Assert.assertEquals(TriState.FALSE, info.getNullSeen(1));
    }

    private ProfilingInfo profile(String methodName, Object... args) {
        return profile(true, N, methodName, args);
    }

    private void continueProfiling(String methodName, Object... args) {
        profile(false, N, methodName, args);
    }

    private void continueProfiling(int executions, String methodName, Object... args) {
        profile(false, executions, methodName, args);
    }

    private ProfilingInfo profile(boolean resetProfile, int executions, String methodName, Object... args) {
        Method method = getMethod(methodName);
        Assert.assertTrue(Modifier.isStatic(method.getModifiers()));

        ResolvedJavaMethod javaMethod = runtime.lookupJavaMethod(method);
        if (resetProfile) {
            javaMethod.reprofile();
        }

        for (int i = 0; i < executions; ++i) {
            try {
                method.invoke(null, args);
            } catch (Throwable e) {
                Assert.fail("method should not throw an exception: " + e.toString());
            }
        }

        return javaMethod.getProfilingInfo();
    }

    private void resetProfile(String methodName) {
        ResolvedJavaMethod javaMethod = runtime.lookupJavaMethod(getMethod(methodName));
        javaMethod.reprofile();
    }
}
