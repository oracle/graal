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
package com.oracle.svm.configure.test.conditionalconfig;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.Assert;

/** Foreign-call tracing fixture. */
@SuppressWarnings("restricted") // Intentional restricted FFM calls for agent tracing.
final class ForeignCalls {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final FunctionDescriptor COMPARATOR = FunctionDescriptor.of(JAVA_INT, ADDRESS.withTargetLayout(JAVA_INT), ADDRESS.withTargetLayout(JAVA_INT));

    static void runTest() {
        try {
            Assert.assertEquals(5L, strlen());
            Assert.assertArrayEquals(new int[]{1, 2, 3}, sort(true));
            Assert.assertArrayEquals(new int[]{3, 2, 1}, sort(false));
            ClassUtil.forName("ForeignCalls$Target");
        } catch (Throwable failure) {
            throw new AssertionError("Foreign-call fixture failed", failure);
        }
    }

    private static long strlen() throws Throwable {
        MethodHandle strlen = LINKER.downcallHandle(LINKER.defaultLookup().find("strlen").orElseThrow(), FunctionDescriptor.of(JAVA_LONG, ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            return (long) strlen.invokeExact(arena.allocateFrom("hello"));
        }
    }

    private static int[] sort(boolean ascending) throws Throwable {
        MethodHandle qsort = LINKER.downcallHandle(LINKER.defaultLookup().find("qsort").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS));
        MethodHandle compare = MethodHandles.lookup().findStatic(ForeignCalls.class, "compare", MethodType.methodType(int.class, int.class, MemorySegment.class, MemorySegment.class));
        MethodHandle target = ascending
                        ? MethodHandles.lookup().findStatic(ForeignCalls.class, "compareAscending", MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class))
                        : MethodHandles.insertArguments(compare, 0, -1);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = arena.allocateFrom(JAVA_INT, new int[]{3, 1, 2});
            MemorySegment comparator = LINKER.upcallStub(target, COMPARATOR, arena);
            qsort.invokeExact(array, 3L, JAVA_INT.byteSize(), comparator);
            return array.toArray(JAVA_INT);
        }
    }

    static int compareAscending(MemorySegment first, MemorySegment second) {
        return compare(1, first, second);
    }

    static int compare(int sign, MemorySegment first, MemorySegment second) {
        return sign * Integer.compare(first.get(JAVA_INT, 0), second.get(JAVA_INT, 0));
    }

    @SuppressWarnings("unused") // Loaded reflectively by name in runTest.
    private static final class Target {
    }
}
