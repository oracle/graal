/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.svm.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.Test;

@NativeImageBuildArgs({
                "--features=com.oracle.svm.test.MethodHandlesLoopObservationTest$TestFeature"
})
public class MethodHandlesLoopObservationTest {
    private static final int REPETITIONS = 100;

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeReflection.register(MethodHandlesLoopObservationTest.class);
            RuntimeReflection.register(MethodHandlesLoopObservationTest.class.getDeclaredMethods());
            RuntimeReflection.register(LoopObserver.class);
            RuntimeReflection.register(LoopObserver.class.getDeclaredConstructors());
            RuntimeReflection.register(LoopObserver.class.getDeclaredMethods());
        }
    }

    @Test
    public void testLoopLocalVariablesRemainObservable() throws Throwable {
        MethodHandle loop = createLoop();
        for (int i = 0; i < REPETITIONS; i++) {
            LoopObserver observer = new LoopObserver();
            assertEquals(27, (int) loop.invoke(observer, 10, 1, 15));

            List<InvocationRecord> records = observer.records();
            assertFalse(records.isEmpty());
            records.forEach(record -> {
                assertNotNull(record);
                assertNotNull(record.passedArguments);
            });

            List<Object[]> observedLocalVariables = observer.getRecords(3, records.size() - 1).stream()
                            .map(record -> new Object[]{record.passedArguments[0], record.passedArguments[1], record.passedArguments[2]})
                            .collect(Collectors.toList());
            assertFalse(observedLocalVariables.isEmpty());
            assertArrayEquals(new Object[]{1, 2, 15}, observedLocalVariables.get(0));
            assertArrayEquals(new Object[]{10, 10, 7}, observedLocalVariables.get(observedLocalVariables.size() - 1));
        }
    }

    private static MethodHandle createLoop() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType initType = MethodType.methodType(int.class, LoopObserver.class, int.class, int.class, int.class);
        MethodType bodyType = MethodType.methodType(int.class, int.class, int.class, int.class, LoopObserver.class, int.class, int.class, int.class);
        MethodType predType = bodyType.changeReturnType(boolean.class);

        MethodHandle init0 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "init0", initType);
        MethodHandle init1 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "init1", initType);
        MethodHandle init2 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "init2", initType);
        MethodHandle step0 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "step0", bodyType);
        MethodHandle step1 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "step1", bodyType);
        MethodHandle step2 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "step2", bodyType);
        MethodHandle pred0 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "pred0", predType);
        MethodHandle pred1 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "pred1", predType);
        MethodHandle pred2 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "pred2", predType);
        MethodHandle fini0 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "fini0", bodyType);
        MethodHandle fini1 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "fini1", bodyType);
        MethodHandle fini2 = lookup.findStatic(MethodHandlesLoopObservationTest.class, "fini2", bodyType);

        return MethodHandles.loop(
                        new MethodHandle[]{init0, step0, pred0, fini0},
                        new MethodHandle[]{init1, step1, pred1, fini1},
                        new MethodHandle[]{init2, step2, pred2, fini2});
    }

    public static int init0(LoopObserver observer, int count, int start, int limit) {
        observer.record("init0", observer, count, start, limit);
        return start;
    }

    public static int init1(LoopObserver observer, int count, int start, int limit) {
        observer.record("init1", observer, count, start, limit);
        return start + 1;
    }

    public static int init2(LoopObserver observer, int count, int start, int limit) {
        observer.record("init2", observer, count, start, limit);
        return limit;
    }

    public static int step0(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("step0", var0, var1, var2, observer, count, start, limit);
        return var0 + 1;
    }

    public static int step1(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("step1", var0, var1, var2, observer, count, start, limit);
        return var1 + 1;
    }

    public static int step2(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("step2", var0, var1, var2, observer, count, start, limit);
        return var2 - 1;
    }

    public static boolean pred0(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("pred0", var0, var1, var2, observer, count, start, limit);
        return var0 < count;
    }

    public static boolean pred1(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("pred1", var0, var1, var2, observer, count, start, limit);
        return true;
    }

    public static boolean pred2(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("pred2", var0, var1, var2, observer, count, start, limit);
        return true;
    }

    public static int fini0(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("fini0", var0, var1, var2, observer, count, start, limit);
        return var0 + var1 + var2;
    }

    public static int fini1(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("fini1", var0, var1, var2, observer, count, start, limit);
        return var0 + var1 + var2;
    }

    public static int fini2(int var0, int var1, int var2, LoopObserver observer, int count, int start, int limit) {
        observer.record("fini2", var0, var1, var2, observer, count, start, limit);
        return var0 + var1 + var2;
    }

    static final class LoopObserver {
        private final List<InvocationRecord> records = new ArrayList<>();

        void record(String functionId, Object... passedArguments) {
            records.add(new InvocationRecord(functionId, Arrays.copyOf(passedArguments, passedArguments.length)));
        }

        List<InvocationRecord> records() {
            return records;
        }

        List<InvocationRecord> getRecords(int fromIndex, int toIndex) {
            return records.subList(fromIndex, toIndex + 1);
        }
    }

    static final class InvocationRecord {
        final String functionId;
        final Object[] passedArguments;

        InvocationRecord(String functionId, Object[] passedArguments) {
            this.functionId = functionId;
            this.passedArguments = passedArguments;
        }
    }
}
