/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.amd64.test;

import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.test.AssemblerTest;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;

public class IndirectCallTest extends AssemblerTest {

    @Before
    public void checkAMD64() {
        assumeTrue("skipping AMD64 specific test", codeCache.getTarget().arch instanceof AMD64);
    }

    private static final int DIRECT_CALL_INSTRUCTION_CODE = 0xE8;
    private static final int DIRECT_CALL_INSTRUCTION_SIZE = 5;

    @Test
    public void withoutJCCErratum() {
        for (int i = 0; i < DIRECT_CALL_INSTRUCTION_SIZE; i++) {
            AMD64MacroAssembler masm = new AMD64MacroAssembler(codeCache.getTarget());
            // 48 8b e8
            masm.movq(AMD64.rbp, AMD64.rax);
            masm.nop(i);
            masm.indirectCall(AMD64.rax, true);
            assertFalse(masm.getByte(masm.position() - DIRECT_CALL_INSTRUCTION_SIZE) == DIRECT_CALL_INSTRUCTION_CODE);
        }

        AMD64MacroAssembler masm = new AMD64MacroAssembler(codeCache.getTarget());
        // 48 8b e8
        masm.movq(AMD64.rbp, AMD64.rax);
        masm.nop(2);
        masm.indirectCall(AMD64.rax, false);
        assertTrue(masm.getByte(masm.position() - DIRECT_CALL_INSTRUCTION_SIZE) == DIRECT_CALL_INSTRUCTION_CODE);
    }

    @Test
    public void withJCCErratum() {
        for (int i = 0; i < AMD64Assembler.JCC_ERRATUM_MITIGATION_BOUNDARY; i++) {
            AMD64MacroAssembler masm = new AMD64MacroAssembler(codeCache.getTarget(), getInitialOptions(), true);
            masm.nop(i);
            // 48 8b e8
            masm.movq(AMD64.rbp, AMD64.rax);
            masm.indirectCall(AMD64.r10, true);
            assertFalse(masm.getByte(masm.position() - DIRECT_CALL_INSTRUCTION_SIZE) == DIRECT_CALL_INSTRUCTION_CODE);
        }

        AMD64MacroAssembler masm = new AMD64MacroAssembler(codeCache.getTarget(), getInitialOptions(), true);
        masm.nop(28);
        // 48 8b e8
        masm.movq(AMD64.rbp, AMD64.rax);
        masm.indirectCall(AMD64.r10, false);
        assertTrue(masm.getByte(masm.position() - DIRECT_CALL_INSTRUCTION_SIZE) == DIRECT_CALL_INSTRUCTION_CODE);
    }
}
