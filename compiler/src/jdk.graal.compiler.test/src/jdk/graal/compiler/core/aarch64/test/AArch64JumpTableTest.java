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
package jdk.graal.compiler.core.aarch64.test;

import static org.junit.Assume.assumeTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.asm.JumpTableOutOfBoundsException;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.CompilationResult.JumpTable;
import jdk.graal.compiler.core.gen.LIRGenerationProvider;
import jdk.graal.compiler.core.test.backend.BackendTest;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.aarch64.AArch64;

public class AArch64JumpTableTest extends BackendTest {
    @Before
    public void checkAArch64() {
        assumeTrue("AArch64-specific test", getTarget().arch instanceof AArch64);
    }

    public static void stub() {
    }

    private void checkJumpTable(int nopCount, boolean expectRetry) {
        StructuredGraph graph = parseEager("stub", StructuredGraph.AllowAssumptions.YES);
        LIRGenerationResult lirGenRes = getLIRGenerationResult(graph);
        CompilationResult result = new CompilationResult(graph.compilationId());
        CompilationResultBuilder crb = ((LIRGenerationProvider) getBackend()).newCompilationResultBuilder(lirGenRes, lirGenRes.getFrameMap(), result,
                        CompilationResultBuilderFactory.Default, null);
        AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
        Assert.assertFalse(masm.isInlineJumpTables());
        if (expectRetry) {
            try {
                emitJumpTable(crb, masm, nopCount);
                Assert.fail("Expected the deferred table to exceed ADR range");
            } catch (JumpTableOutOfBoundsException expected) {
                Assert.assertFalse(masm.isInlineJumpTables());
                crb.resetForEmittingCode();
                masm.setInlineJumpTables();
            }
        }
        Label[] targets = emitJumpTable(crb, masm, nopCount);
        Assert.assertEquals(expectRetry, masm.isInlineJumpTables());
        int adrPosition = Integer.BYTES;

        Assert.assertEquals(1, result.getCodeAnnotations().size());
        JumpTable table = (JumpTable) result.getCodeAnnotations().get(0);
        Assert.assertEquals(0, table.low);
        Assert.assertEquals(2, table.high);
        Assert.assertEquals(JumpTable.EntryFormat.OFFSET_ONLY, table.entryFormat);
        ByteBuffer code = ByteBuffer.wrap(masm.close(false)).order(getTarget().arch.getByteOrder());
        int instruction = code.getInt(adrPosition);
        Assert.assertEquals(0x10000000, instruction & 0x9f000000);
        int immediate = ((instruction >>> 5) & 0x7ffff) << 2 | ((instruction >>> 29) & 3);
        int displacement = (immediate << 11) >> 11;
        Assert.assertEquals(table.getPosition() - adrPosition, displacement);
        if (!expectRetry) {
            Assert.assertTrue("Short methods retain the deferred table", table.getPosition() > targets[2].position());
        } else {
            Assert.assertTrue("Retried methods use an adjacent table", table.getPosition() < targets[1].position());
        }
        // Validate both a backward target and forward targets more than 1 MiB from the table.
        for (int i = 0; i < targets.length; i++) {
            Assert.assertEquals(targets[i].position() - table.getPosition(), code.getInt(table.getPosition() + i * Integer.BYTES));
        }
    }

    private static Label[] emitJumpTable(CompilationResultBuilder crb, AArch64MacroAssembler masm, int nopCount) {
        Label[] targets = {new Label(), new Label(), new Label()};
        masm.bind(targets[0]);
        masm.nop();
        AArch64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, AArch64.r1, AArch64.r0, 0, 2, Arrays.stream(targets));
        // A deferred jump table would lie beyond ADR's signed 21-bit byte displacement.
        for (int i = 0; i < nopCount; i++) {
            masm.nop();
        }
        masm.bind(targets[1]);
        masm.nop();
        masm.bind(targets[2]);
        masm.nop();
        if (crb.getLIR().getSlowPaths() != null) {
            for (LIRInstruction.LIRInstructionSlowPath slowPath : crb.getLIR().getSlowPaths()) {
                slowPath.emitSlowPathCode();
            }
        }

        return targets;
    }

    @Test
    public void testShortJumpTable() {
        checkJumpTable(0, false);
    }

    @Test
    public void testLargestReachableJumpTable() {
        // The dispatch, two target NOPs and HALT account for 28 bytes after ADR.
        checkJumpTable((1 << 18) - 8, false);
    }

    @Test
    public void testFirstUnreachableJumpTable() {
        checkJumpTable((1 << 18) - 7, true);
    }

    @Test
    public void testLongJumpTable() {
        checkJumpTable(1 << 18, true);
    }
}
