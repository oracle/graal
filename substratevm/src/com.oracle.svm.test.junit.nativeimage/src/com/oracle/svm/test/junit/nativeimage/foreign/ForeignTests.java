/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.foreign;

import static org.junit.Assert.assertEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.SubstrateOptions;

@SuppressWarnings("restricted")
public class ForeignTests {
    public static final FunctionDescriptor QSORT_COMPARE_DESC = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_INT),
                    ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_INT));
    private static final String STRING = "Hello, World!";
    private static final FunctionDescriptor STRLEN_SIG = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
    private static final FunctionDescriptor QSORT_SIG = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
    private static final MethodHandle COMPARE_HANDLE = initCompareHandle();

    @Test
    public void testInvokeStrlen() throws Throwable {
        Assume.assumeFalse("Linker.nativeLinker().defaultLookup() is not supported in static executables",
                        SubstrateOptions.StaticExecutable.getValue());

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeString = arena.allocateFrom(STRING);
            assertEquals(STRING.length(), (long) strlenMH().invokeExact(nativeString));
        }
    }

    @Test
    public void testUpcall() throws Throwable {
        Assume.assumeFalse("Linker.nativeLinker().defaultLookup() is not supported in static executables",
                        SubstrateOptions.StaticExecutable.getValue());

        /*
         * Due to native memory tracking (NMT) tests, we use 'Arena.global()' to ensure that the
         * upcall stub won't be deallocated during NMT tests which would lead to unexpected memory
         * usage values.
         */
        MemorySegment compareFunc = Linker.nativeLinker().upcallStub(COMPARE_HANDLE, QSORT_COMPARE_DESC, Arena.global());

        int[] unsortedArray = new int[]{0, 9, 3, 4, 6, 5, 1, 8, 2, 7};

        int[] sorted;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = arena.allocateFrom(ValueLayout.JAVA_INT, unsortedArray);

            qsortMH().invoke(array,
                            (long) unsortedArray.length,
                            ValueLayout.JAVA_INT.byteSize(),
                            compareFunc);

            sorted = array.toArray(ValueLayout.JAVA_INT);
        }

        int[] jSortedArray = Arrays.copyOf(unsortedArray, unsortedArray.length);
        Arrays.sort(jSortedArray);

        Assert.assertArrayEquals(jSortedArray, sorted);
    }

    private static MethodHandle strlenMH() {
        SymbolLookup stdLib = Linker.nativeLinker().defaultLookup();
        MemorySegment strlenAddr = stdLib.find("strlen").orElseThrow();
        return Linker.nativeLinker().downcallHandle(strlenAddr, STRLEN_SIG);
    }

    private static MethodHandle qsortMH() {
        MemorySegment qsortAddr = Linker.nativeLinker().defaultLookup().find("qsort").orElseThrow();
        return Linker.nativeLinker().downcallHandle(qsortAddr, QSORT_SIG);
    }

    private static MethodHandle initCompareHandle() {
        try {
            return MethodHandles.lookup().findStatic(Qsort.class, "qsortCompare",
                            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static class Qsort {
        static int qsortCompare(MemorySegment elem1, MemorySegment elem2) {
            return Integer.compare(elem1.get(ValueLayout.JAVA_INT, 0), elem2.get(ValueLayout.JAVA_INT, 0));
        }
    }

    public static class TestFeature implements Feature {

        @Override
        public void duringSetup(DuringSetupAccess access) {
            RuntimeForeignAccess.registerForDowncall(STRLEN_SIG);
            RuntimeForeignAccess.registerForDowncall(QSORT_SIG);
            RuntimeForeignAccess.registerForDirectUpcall(COMPARE_HANDLE, QSORT_COMPARE_DESC);
        }
    }
}
