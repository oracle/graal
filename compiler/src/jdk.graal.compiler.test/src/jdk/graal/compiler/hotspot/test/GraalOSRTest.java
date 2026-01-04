/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.bytecode.BytecodeDisassembler;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test on-stack-replacement with Graal. The test manually triggers a Graal OSR-compilation which is
 * later invoked when hitting the backedge counter overflow.
 */
public class GraalOSRTest extends GraalOSRTestBase {

    @Test
    public void testOSR01() {
        try {
            testOSR(getInitialOptions(), "testReduceLoop");
        } catch (Throwable t) {
            Assert.assertEquals("OSR compilation without OSR entry loop.", t.getMessage());
        }
    }

    @Test
    public void testOSR02() {
        testOSR(getInitialOptions(), "testSequentialLoop");
    }

    @Test
    public void testOSR03() {
        testOSR(getInitialOptions(), "testNonReduceLoop");
    }

    @Test
    public void testOSR04() {
        testOSR(getInitialOptions(), "testDeoptAfterCountedLoop");
    }

    @Test
    public void testOSR05() {
        testOSR(getInitialOptions(), "testBooleanArray");
    }

    static int limit = 10000;

    public static int sideEffect;

    public static ReturnValue testReduceLoop() {
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (GraalDirectives.inCompiledCode()) {
                return ReturnValue.SUCCESS;
            }
        }
        return ReturnValue.FAILURE;
    }

    public static ReturnValue testSequentialLoop() {
        ReturnValue ret = ReturnValue.FAILURE;
        for (int i = 1; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 7 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        if (sideEffect == 123) {
            return ReturnValue.SIDE;
        }
        for (int i = 1; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 33 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return ret;
    }

    public static ReturnValue testNonReduceLoop() {
        ReturnValue ret = ReturnValue.FAILURE;
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 33 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return ret;
    }

    public static ReturnValue testDeoptAfterCountedLoop() {
        long ret = 0;
        for (int i = 0; GraalDirectives.injectBranchProbability(1, i < limit * limit); i++) {
            GraalDirectives.blackhole(i);
            ret = GraalDirectives.opaque(i);
        }
        GraalDirectives.controlFlowAnchor();
        return ret + 1 == limit * limit ? ReturnValue.SUCCESS : ReturnValue.FAILURE;
    }

    public static ReturnValue testBooleanArray() {
        boolean[] array = new boolean[16];
        for (int i = 0; GraalDirectives.injectIterationCount(17, i < array.length); i++) {
            array[i] = !array[i];

            byte rawValue = UNSAFE.getByte(array, Unsafe.ARRAY_BYTE_BASE_OFFSET + i * Unsafe.ARRAY_BYTE_INDEX_SCALE);
            if (rawValue != 1) {
                return ReturnValue.FAILURE;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return ReturnValue.SUCCESS;
    }

    public static volatile boolean stopped;

    /**
     * A method that shows the need for {@code HotSpotResolvedJavaMethod.getOopMapAt(int bci)} to
     * clear oops at OSR entry points.
     *
     * The Java source code below should produce {@link #TEST_OOP_MAP_BYTECODE}.
     */
    public static ReturnValue testOopMap(Object[] local0, AtomicInteger local1, Runnable local2) {
        try {
            // 1. Block defining local4 as an Object
            Object local3 = local0;
            local3.hashCode();
            Object local4 = local0;
            local4.hashCode();
        } catch (NullPointerException local3) {
            // 2. Exception handler defining local4 as an int
            int local4 = 0x54321;
            String.valueOf(local4);
        }

        // 3. Merge of 1 and 2. If an exception never occurred, Graal only
        // parses block 1 so local4 is available here. In contrast,
        // the interpreter says local4 is dead here.
        //
        // See OnStackReplacementPhase.narrowOsrLocal for more detail.
        while (local1.decrementAndGet() >= 0) {
            if (local2 != null) {
                local2.run();
            }
        }
        return ReturnValue.SUCCESS;
    }

    /**
     * Expected bytecode for {@link #testOopMap(Object[], AtomicInteger, Runnable)}.
     */
    // @formatter:off
    private static final String TEST_OOP_MAP_BYTECODE = """
           0: aload_0
           1: astore_3
           2: aload_3
           3: invokevirtual #12         // java.lang.Object.hashCode:()int
           6: pop
           7: aload_0
           8: astore        4
          10: aload         4
          12: invokevirtual #12         // java.lang.Object.hashCode:()int
          15: pop
          16: goto          30
          19: astore_3
          20: ldc           #110        // 344865
          22: istore        4
          24: iload         4
          26: invokestatic  #13         // java.lang.String.valueOf:(int)java.lang.String
          29: pop
          30: aload_1
          31: invokevirtual #14         // java.util.concurrent.atomic.AtomicInteger.decrementAndGet:()int
          34: iflt          50
          37: aload_2
          38: ifnull        30
          41: aload_2
          42: invokeinterface#15, 1      // java.lang.Runnable.run:()void
          47: goto          30
          50: getstatic     #1          // jdk.graal.compiler.hotspot.test.GraalOSRTestBase$ReturnValue.SUCCESS:jdk.graal.compiler.hotspot.test.GraalOSRTestBase$ReturnValue
          53: areturn""";
    // @formatter:on

    /**
     * Tests that dead oops are cleared at OSR entry points.
     */
    @Test
    public void testOSR06() {
        // Check that javap produced what we expect
        ResolvedJavaMethod method = getResolvedJavaMethod("testOopMap");
        String dis = new BytecodeDisassembler().disassemble(method);
        String actual = normalizedDisassembly(dis);
        String expect = normalizedDisassembly(TEST_OOP_MAP_BYTECODE);
        Assert.assertEquals(String.format("unexpected disassembly {%n%s}", dis), expect, actual);

        Object[] arr = {"1", "2", "3"};
        testOopMap(arr, new AtomicInteger(4), null);
        TestOopMapFrameChecker checker = new TestOopMapFrameChecker();
        Runnable r = () -> {
            stackIntrospection.iterateFrames(null, null, 0, checker);
        };
        AtomicInteger iterations = new AtomicInteger(Integer.getInteger("OSRIterations", 50000));
        testOSR(getInitialOptions(), "testOopMap", null, null, iterations, r);

        Assert.assertTrue(String.valueOf(iterations), checker.checked);
    }

    static StackIntrospection stackIntrospection = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getStackIntrospection();

    static class TestOopMapFrameChecker implements InspectedFrameVisitor<Object> {
        boolean checked;

        @Override
        public Object visitFrame(InspectedFrame frame) {
            ResolvedJavaMethod method = frame.getMethod();
            if (!checked && method.getName().equals("testOopMap")) {
                Assert.assertEquals(5, method.getMaxLocals());
                Assert.assertNull("local4 should have been cleared by OnStackReplacementPhase", frame.getLocal(4));
                checked = true;
            }
            return null;
        }
    }

    /**
     * Normalizes the bytecode disassembly in {@code dis} into trimmed lines with constant pool
     * indexes converted to {@code "#__"}. This makes {@link #TEST_OOP_MAP_BYTECODE} resilient to
     * changes in this source file (apart from rearranging code in {@link #testOopMap} itself).
     */
    private static String normalizedDisassembly(String dis) {
        Pattern cpRef = Pattern.compile("#\\d+");
        return Stream.of(dis.split("\n")).map(line -> cpRef.matcher(line.trim()).replaceAll(_ -> "#__")).collect(Collectors.joining(System.lineSeparator()));
    }

    private static final int ArrayLength = 10000;

    static ReturnValue testArrayBottom(byte[] aB, int[] aI, int i) {
        Object a = null;
        long base = 0;
        if (i % 2 == 0) {
            a = aB;
            base = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        } else {
            a = aI;
            base = Unsafe.ARRAY_INT_BASE_OFFSET;
        }
        int res = 0;
        for (int j = 0; j < ArrayLength; j++) {
            res += UNSAFE.getByte(a, base + j);
        }
        GraalDirectives.sideEffect(res);

        return ReturnValue.SUCCESS;
    }

    @Test
    public void testOSR07() {
        testOSR(getInitialOptions(), "testArrayBottom", null, new byte[ArrayLength], new int[ArrayLength], 10);
    }

    static ReturnValue testNonNullArrayBottom(int i) {
        Object a;
        long base;
        if (i % 2 == 0) {
            a = new byte[ArrayLength];
            base = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        } else {
            a = new int[ArrayLength];
            base = Unsafe.ARRAY_INT_BASE_OFFSET;
        }
        int res = 0;
        for (int j = 0; j < 10000; j++) {
            res += UNSAFE.getByte(a, base + j);
        }
        GraalDirectives.sideEffect(res);

        return ReturnValue.SUCCESS;
    }

    @Test
    public void testOSR08() {
        testOSR(getInitialOptions(), "testNonNullArrayBottom", null, 10);
    }
}
