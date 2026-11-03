/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.amd64.test;

import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.nodes.ChainedPhiValueSplitNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.core.test.MatchRuleTest;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.RangeTableSwitchOp;
import jdk.graal.compiler.lir.amd64.AMD64Unary;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;

/**
 * Checks that narrowing a long to an int needs no instruction and preserves the original long.
 */
public class AMD64NarrowTest extends MatchRuleTest {
    private static final long[] LONG_VALUES = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0x1234567800000001L, 0x1234567880000000L, 0x12345678ffffffffL};
    private static final int[] INT_VALUES = {-1, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE};
    private static volatile int sideEffect;

    @Before
    public void checkAMD64() {
        assumeTrue(getTarget().arch instanceof AMD64);
    }

    /**
     * A conversion MOV is distinct from moves introduced for calling conventions or allocation.
     */
    private void checkNoNarrowMove(String snippet) {
        checkLIRforAll(snippet, op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 0);
    }

    /**
     * Exercises consumers of the narrowed value with nonzero upper bits and both signs.
     */
    private void checkUnaryResults(String snippet) {
        for (long value : LONG_VALUES) {
            test(snippet, value);
        }
    }

    /**
     * Checks that the extension is part of the load, with no register MOV or separate extension.
     */
    private void checkExtendedLoad(String snippet, String opcode) {
        checkLIRforAll(snippet, op -> op instanceof AMD64Unary.MemoryOp && op.name().equals(opcode), 1);
        checkLIRforAll(snippet, op -> op instanceof AMD64Unary.RMOp && op.name().startsWith("MOV"), 0);
    }

    public static int narrow(long value) {
        return (int) value;
    }

    @Test
    public void testNarrow() {
        checkNoNarrowMove("narrow");
        checkUnaryResults("narrow");
    }

    public static long unsigned(long value) {
        return Integer.toUnsignedLong((int) value);
    }

    @Test
    public void testUnsigned() {
        /* One DWORD MOV is required to clear the original long's upper bits. */
        checkLIRforAll("unsigned", op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 1);
        checkUnaryResults("unsigned");
    }

    /**
     * Uses nested side effects to require iterative traversal to the narrowed input.
     */
    public static long unsignedSideEffect(long value) {
        return Integer.toUnsignedLong(GraalDirectives.sideEffect(GraalDirectives.sideEffect((int) value)));
    }

    @Test
    public void testUnsignedSideEffect() {
        checkLIRforAll("unsignedSideEffect", op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 1);
        checkUnaryResults("unsignedSideEffect");
        test("unsignedSideEffect", 0x0000000100000000L);
    }

    /**
     * Merges a narrowed long with an int before using the result as an array index. The volatile
     * stores prevent replacing the merge with a conditional move that clears the upper bits. The
     * anchor prevents duplicating the array access into the branches, which would remove the phi.
     */
    public static int narrowPhiIndex(long value, int shift, boolean flag, int[] array) {
        int index;
        if (flag) {
            sideEffect = 1;
            index = (int) (value >>> shift);
        } else {
            sideEffect = 2;
            index = sideEffect;
        }
        GraalDirectives.controlFlowAnchor();
        return array[index];
    }

    /**
     * Checks the required clearing of upper bits before executing the array access, so a missing
     * conversion is reported as a LIR failure rather than an invalid memory access.
     */
    @Test
    public void testNarrowPhiIndex() {
        checkLIRforAll("narrowPhiIndex", op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 1);
        int[] array = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        test("narrowPhiIndex", 0x0000000b0000000aL, 0, true, array);
        test("narrowPhiIndex", 0x0000000b0000000aL, 32, true, array);
        test("narrowPhiIndex", 0x0000000b0000000aL, 0, false, array);
    }

    @BytecodeParserNeverInline
    public static long returnLong(long value) {
        return value;
    }

    /**
     * Narrows a long returned in the result register. Returning an int can reuse that register
     * without clearing its upper bits, as permitted by the calling convention.
     */
    @BytecodeParserNeverInline
    public static int narrowReturn(long value) {
        return (int) returnLong(value);
    }

    public static int narrowReturnIndex(long value, int[] array) {
        return array[narrowReturn(value)];
    }

    /**
     * Checks the conversion before executing the access, with both callees compiled and installed
     * as their default implementations so that the int result can have the original long's
     * upper bits. Invalidates both implementations after the test.
     */
    @Test
    public void testNarrowReturnIndex() {
        OptionValues options = new OptionValues(getInitialOptions(), HighTier.Options.Inline, false);
        InstalledCode returnLongCode = getCode(getResolvedJavaMethod("returnLong"), null, true, true, options);
        try {
            InstalledCode narrowReturnCode = getCode(getResolvedJavaMethod("narrowReturn"), null, true, true, options);
            try {
                checkLIRforAll(options, "narrowReturnIndex", op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 1);
                test(options, "narrowReturnIndex", 0x0000000100000000L, new int[]{42});
            } finally {
                narrowReturnCode.invalidate();
            }
        } finally {
            returnLongCode.invalidate();
        }
    }

    /**
     * The two loop phis form a chain. The split introduced for the previous value forwards a
     * narrowed long without clearing its upper register bits.
     */
    public static long unsignedChainedPhi(long value, int count) {
        int current = (int) value;
        int previous = 0;
        long result = 0;
        for (int i = 0; i < count; i++) {
            previous = current;
            result = Integer.toUnsignedLong(current);
            current = (int) GraalDirectives.sideEffect(value + result);
        }
        return result + Integer.toUnsignedLong(previous);
    }

    @Test
    public void testUnsignedChainedPhi() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false,
                        LoopInversionPhase.Options.LoopInversion, false, MidTier.Options.StripMineCountedLoops, false);
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("unsignedChainedPhi"), options);
        assertTrue(graph.getNodes().filter(ChainedPhiValueSplitNode.class).isNotEmpty());
        checkLIRforAll(options, "unsignedChainedPhi", op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 2);
        for (long value : LONG_VALUES) {
            test(options, "unsignedChainedPhi", value, 3);
        }
    }

    /**
     * Uses a jump table starting at zero, so subtracting its lowest key cannot clear the upper
     * register bits. The original long is also used after dispatch and must not be overwritten.
     */
    public static long denseSwitch(long value) {
        int result = switch ((int) value) {
            case 0 -> 17;
            case 1 -> -5;
            case 2 -> 83;
            case 3 -> 26;
            case 4 -> -19;
            case 5 -> 101;
            case 6 -> 7;
            case 7 -> -43;
            case 8 -> 64;
            case 9 -> 11;
            case 10 -> -2;
            case 11 -> 35;
            case 12 -> 42;
            case 13 -> -31;
            case 14 -> 0;
            case 15 -> 90;
            default -> -1;
        };
        return value ^ result;
    }

    /**
     * Checks that the jump table index is explicitly zero-extended before executing the switch,
     * so a missing conversion cannot crash the test JVM.
     */
    @Test
    public void testDenseSwitch() {
        checkLIRforAll("denseSwitch", op -> op instanceof RangeTableSwitchOp, 1);
        checkLIRforAll("denseSwitch", op -> op instanceof AMD64Unary.RMOp && op.name().equals("MOV"), 1);
        for (int key = 0; key <= 16; key++) {
            test("denseSwitch", 0x0000000100000000L | key);
        }
        checkUnaryResults("denseSwitch");
    }

    public static long store(long value, int[] output) {
        output[0] = (int) value;
        return value;
    }

    @Test
    public void testStore() {
        checkNoNarrowMove("store");
        for (long value : LONG_VALUES) {
            test("store", value, new int[1]);
        }
    }

    public static long storeByte(long value, byte[] output) {
        output[0] = (byte) value;
        return value;
    }

    @Test
    public void testStoreByte() {
        checkNoNarrowMove("storeByte");
        for (long value : LONG_VALUES) {
            test("storeByte", value, new byte[1]);
        }
    }

    /**
     * Enough arguments to exercise narrowing values passed on the stack as well as in registers.
     */
    public static long stackArguments(long a, long b, long c, long d, long e, long f, long g, long h) {
        return a + b + c + d + e + f + g + h + ((int) a ^ (int) b ^ (int) c ^ (int) d ^ (int) e ^ (int) f ^ (int) g ^ (int) h);
    }

    @Test
    public void testStackArguments() {
        checkNoNarrowMove("stackArguments");
        for (long value : LONG_VALUES) {
            test("stackArguments", value, ~value, value + 1, value - 1, -value, value ^ Long.MIN_VALUE, value ^ Long.MAX_VALUE, value + 42);
        }
    }

    public static float byteToFloat(byte[] values) {
        return values[0];
    }

    @Test
    public void testByteToFloat() {
        checkExtendedLoad("byteToFloat", "MOVSXB");
        for (byte value : new byte[]{Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE}) {
            test("byteToFloat", new byte[]{value, -1, -1, -1});
        }
    }

    public static long signedInt(int[] values) {
        return values[0];
    }

    @Test
    public void testSignedInt() {
        checkExtendedLoad("signedInt", "MOVSXD");
        for (int value : INT_VALUES) {
            test("signedInt", new int[]{value, -1});
        }
    }

    public static long unsignedInt(int[] values) {
        return Integer.toUnsignedLong(values[0]);
    }

    @Test
    public void testUnsignedInt() {
        checkExtendedLoad("unsignedInt", "MOV");
        for (int value : INT_VALUES) {
            test("unsignedInt", new int[]{value, -1});
        }
    }
}
