/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.asm.amd64.test;

import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MROp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Op;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Shift;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEMRIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEMROp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.test.GraalTest;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.code.TargetDescription;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;

public class AMD64AssemblerUtilityTest extends GraalTest {

    /*
     * Independent expectations for memory-destination opcode families. New opcodes in these
     * families must be classified here so the interception coverage does not implicitly trust the
     * opcode's isMemRead flag.
     */
    private static final Set<String> NO_OPCODES = CollectionsUtil.setOf();
    private static final Set<String> AMD64_MR_OP_MEMORY_READS = CollectionsUtil.setOf(
                    "SHLD", "SHRD", "TEST", "CMPXCHGB", "CMPXCHG", "XADDB", "XADD");
    private static final Set<String> AMD64_MR_OP_WRITE_ONLY = CollectionsUtil.setOf("MOVB", "MOV");
    private static final Set<String> SSE_MR_OP_WRITE_ONLY = CollectionsUtil.setOf(
                    "MOVD", "MOVQ", "MOVSS", "MOVSD", "MOVAPS", "MOVDQU", "MOVUPD", "MOVUPS");
    private static final Set<String> AMD64_M_OP_MEMORY_READS = CollectionsUtil.setOf(
                    "DEC", "DECB", "DIV", "IDIV", "IMUL", "INC", "INCB", "MUL", "NEG", "NEGB", "NOT", "NOTB",
                    "PUSH", "SAR", "SAR1", "SHL", "SHL1", "SHR", "SHR1");
    private static final Set<String> AMD64_M_OP_WRITE_ONLY = CollectionsUtil.setOf("POP");
    private static final Set<String> AMD64_MI_OP_MEMORY_READS = CollectionsUtil.setOf(
                    "BT", "BTR", "BTS", "SAR", "SHL", "SHR", "TESTB", "TEST");
    private static final Set<String> AMD64_MI_OP_WRITE_ONLY = CollectionsUtil.setOf("MOVB", "MOV");
    private static final Set<String> SSE_MRI_OP_WRITE_ONLY = CollectionsUtil.setOf("PEXTRB", "PEXTRW", "PEXTRD", "PEXTRQ");
    private static final Set<String> AMD64_BINARY_ARITHMETIC_MEMORY_READS = CollectionsUtil.setOf(
                    "ADD", "OR", "ADC", "SBB", "AND", "SUB", "XOR", "CMP");
    private static final Set<String> AMD64_SHIFT_MEMORY_READS = CollectionsUtil.setOf("ROL", "ROR", "RCL", "RCR", "SHL", "SHR", "SAR");

    @Test
    public void testAVXSizeFitsWithin() {
        assertTrue(DWORD.fitsWithin(DWORD));
        assertFalse(DWORD.fitsWithin(QWORD));
        assertFalse(DWORD.fitsWithin(XMM));
        assertFalse(DWORD.fitsWithin(YMM));
        assertFalse(DWORD.fitsWithin(ZMM));

        assertFalse(QWORD.fitsWithin(DWORD));
        assertTrue(QWORD.fitsWithin(QWORD));
        assertFalse(QWORD.fitsWithin(XMM));
        assertFalse(QWORD.fitsWithin(YMM));
        assertFalse(QWORD.fitsWithin(ZMM));

        assertFalse(XMM.fitsWithin(DWORD));
        assertFalse(XMM.fitsWithin(QWORD));
        assertTrue(XMM.fitsWithin(XMM));
        assertTrue(XMM.fitsWithin(YMM));
        assertTrue(XMM.fitsWithin(ZMM));

        assertFalse(YMM.fitsWithin(DWORD));
        assertFalse(YMM.fitsWithin(QWORD));
        assertFalse(YMM.fitsWithin(XMM));
        assertTrue(YMM.fitsWithin(YMM));
        assertTrue(YMM.fitsWithin(ZMM));

        assertFalse(ZMM.fitsWithin(DWORD));
        assertFalse(ZMM.fitsWithin(QWORD));
        assertFalse(ZMM.fitsWithin(XMM));
        assertFalse(ZMM.fitsWithin(YMM));
        assertTrue(ZMM.fitsWithin(ZMM));
    }

    @Test
    public void testAVXKindGetRegisterSize() {
        for (AMD64Kind kind : AMD64Kind.values()) {
            AVXSize size = AVXKind.getRegisterSize(new ConstantValue(LIRKind.value(kind), null));
            if (kind.isXMM()) {
                assertTrue(size.getBytes() >= kind.getSizeInBytes());
            } else {
                assertTrue(size == XMM);
            }
        }
    }

    @Test
    public void testAVXKindChangeSize() {
        for (AMD64Kind kind : AMD64Kind.values()) {
            if (kind.isMask()) {
                continue;
            }
            for (AVXSize size : EnumSet.of(XMM, YMM, ZMM)) {
                AMD64Kind newKind = AVXKind.changeSize(kind, size);
                assertDeepEquals(newKind.getScalar(), kind.getScalar());
                assertTrue(newKind.getScalar() == kind.getScalar());
                assertTrue(newKind.getSizeInBytes() == size.getBytes());
            }
        }
    }

    @Test
    public void testAMD64AddressToString() {
        assertDeepEquals("[rsp]", new AMD64Address(AMD64.rsp).toString());
        assertDeepEquals("[rip + 255]", new AMD64Address(AMD64.rip, 0x000000FF).toString());
        assertDeepEquals("[rax + rcx * 4]", new AMD64Address(AMD64.rax, AMD64.rcx, Stride.S4).toString());
        assertDeepEquals("[r11 + r9 * 2 + 32]", new AMD64Address(AMD64.r11, AMD64.r9, Stride.S2, 0x00000020).toString());
        assertDeepEquals("[r8 * 1 + annotation]", new AMD64Address(Register.None, AMD64.r8, Stride.S1, 0, "annotation").toString());
    }

    /**
     * Tests that fallback fencing for locked memory-destination reads is emitted before the LOCK
     * prefix.
     */
    @Test
    public void testLockedMemoryDestinationReadFallbackFencePrecedesLockPrefix() {
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        Assume.assumeTrue("skipping non-AMD64 specific test", target.arch instanceof AMD64);

        AMD64Address address = new AMD64Address(AMD64.rax, AMD64.rbx, Stride.S1, 16);
        assertFallbackFencePrecedesLockPrefix(target, asm -> {
            asm.lock();
            asm.cmpxchgl(address, AMD64.rcx);
        });
        assertFallbackFencePrecedesLockPrefix(target, asm -> {
            asm.lock();
            asm.xaddl(address, AMD64.rcx);
        });
        assertFallbackFencePrecedesLockPrefix(target, asm -> {
            asm.lock();
            asm.incl(address);
        });
    }

    /**
     * Tests that resetting the assembler also clears the LOCK-prefix state used by fallback
     * fencing.
     */
    @Test
    public void testLockPositionIsClearedByReset() {
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        Assume.assumeTrue("skipping non-AMD64 specific test", target.arch instanceof AMD64);

        FencingAssembler asm = new FencingAssembler(target);
        AMD64Address address = new AMD64Address(AMD64.rax, AMD64.rbx, Stride.S1, 16);
        asm.lock();
        asm.reset();
        asm.emitByte(0x90);
        asm.movl(AMD64.rcx, address);
        assertDeepEquals(0x90, asm.getByte(0));
        assertDeepEquals(0x0F, asm.getByte(1));
        assertDeepEquals(0xAE, asm.getByte(2));
        assertDeepEquals(0xE8, asm.getByte(3));
    }

    /**
     * Tests that the fused memory-destination compare accounts for bytes emitted by the memory-read
     * interceptor.
     */
    @Test
    public void testMemoryDestinationCompareAndJccAccountsForInterceptorBytes() {
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        Assume.assumeTrue("skipping non-AMD64 specific test", target.arch instanceof AMD64);

        OptionValues options = new OptionValues(null, AMD64Assembler.Options.UseBranchesWithin32ByteBoundary, true);
        FencingAssembler asm = new FencingAssembler(target, options);
        AMD64Address address = new AMD64Address(AMD64.rax, AMD64.rbx, Stride.S1, 16);
        for (int i = 0; i < 24; i++) {
            asm.emitByte(0x90);
        }
        Label targetLabel = new Label();
        asm.cmplAndJcc(address, AMD64.rcx, ConditionFlag.Equal, targetLabel, true);
        asm.bind(targetLabel);
        assertDeepEquals(0x0F, asm.getByte(32));
        assertDeepEquals(0xAE, asm.getByte(33));
        assertDeepEquals(0xE8, asm.getByte(34));
    }

    /**
     * Tests that memory-destination opcode families are intercepted exactly when the destination is
     * also a memory read.
     */
    @Test
    public void testMemoryDestinationReadInterception() throws IllegalAccessException {
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        Assume.assumeTrue("skipping non-AMD64 specific test", target.arch instanceof AMD64);

        AMD64Address address = new AMD64Address(AMD64.rax, AMD64.rbx, Stride.S1, 16);

        for (NamedOp<AMD64MROp> namedOp : opsFromClass(AMD64MROp.class, AMD64MROp.class)) {
            AMD64MROp op = namedOp.op;
            OperandSize size = testSize(op);
            assertMROpClassification(target, op, size, address, expectedMemoryRead(namedOp, AMD64_MR_OP_MEMORY_READS, AMD64_MR_OP_WRITE_ONLY));
        }

        for (NamedOp<AMD64MROp> namedOp : opsFromClass(SSEMROp.class, AMD64MROp.class)) {
            AMD64MROp op = namedOp.op;
            OperandSize size = testSize(op);
            assertMROpClassification(target, op, size, address, AMD64.xmm0, expectedMemoryRead(namedOp, NO_OPCODES, SSE_MR_OP_WRITE_ONLY));
        }

        for (NamedOp<AMD64MOp> namedOp : opsFromClass(AMD64MOp.class, AMD64MOp.class)) {
            AMD64MOp op = namedOp.op;
            OperandSize size = testSize(op);
            assertMOpClassification(target, op, size, address, expectedMemoryRead(namedOp, AMD64_M_OP_MEMORY_READS, AMD64_M_OP_WRITE_ONLY));
        }

        for (NamedOp<AMD64MIOp> namedOp : opsFromClass(AMD64MIOp.class, AMD64MIOp.class)) {
            AMD64MIOp op = namedOp.op;
            OperandSize size = testSize(op);
            assertMIOpClassification(target, op, size, address, expectedMemoryRead(namedOp, AMD64_MI_OP_MEMORY_READS, AMD64_MI_OP_WRITE_ONLY));
        }

        for (NamedOp<AMD64BinaryArithmetic> namedOp : opsFromClass(AMD64BinaryArithmetic.class, AMD64BinaryArithmetic.class)) {
            AMD64BinaryArithmetic op = namedOp.op;
            boolean expectedMemoryRead = expectedMemoryRead(namedOp, AMD64_BINARY_ARITHMETIC_MEMORY_READS, NO_OPCODES);
            assertMROpClassification(target, op.getMROpcode(OperandSize.BYTE), OperandSize.BYTE, address, expectedMemoryRead);
            assertMROpClassification(target, op.getMROpcode(OperandSize.DWORD), OperandSize.DWORD, address, expectedMemoryRead);
            assertMIOpClassification(target, op.getMIOpcode(OperandSize.BYTE, false), OperandSize.BYTE, address, expectedMemoryRead);
            assertMIOpClassification(target, op.getMIOpcode(OperandSize.DWORD, false), OperandSize.DWORD, address, expectedMemoryRead);
            assertMIOpClassification(target, op.getMIOpcode(OperandSize.DWORD, true), OperandSize.DWORD, address, expectedMemoryRead);
        }

        for (NamedOp<SSEMRIOp> namedOp : opsFromClass(SSEMRIOp.class, SSEMRIOp.class)) {
            SSEMRIOp op = namedOp.op;
            OperandSize size = testSize(op);
            assertInterceptsMemoryRead(target, expectedMemoryRead(namedOp, NO_OPCODES, SSE_MRI_OP_WRITE_ONLY),
                            asm -> op.emit(asm, size, address, AMD64.xmm0, 1), op + " " + size);
        }

        for (NamedOp<AMD64Shift> namedOp : opsFromClass(AMD64Shift.class, AMD64Shift.class)) {
            AMD64Shift op = namedOp.op;
            boolean expectedMemoryRead = expectedMemoryRead(namedOp, AMD64_SHIFT_MEMORY_READS, NO_OPCODES);
            assertMOpClassification(target, op.m1Op, OperandSize.DWORD, address, expectedMemoryRead);
            assertMOpClassification(target, op.mcOp, OperandSize.DWORD, address, expectedMemoryRead);
            assertMIOpClassification(target, op.miOp, OperandSize.DWORD, address, expectedMemoryRead);
        }
    }

    /**
     * Tests that memory-read fence metadata is preserved from LIR addresses to
     * emitted AMD64 addresses.
     */
    @Test
    public void testCanSkipMemoryReadFenceMetadata() {
        TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
        Assume.assumeTrue("skipping non-AMD64 specific test", target.arch instanceof AMD64);

        AMD64Address address = new AMD64Address(AMD64.rax, AMD64.rbx, Stride.S1, 16);
        assertFalse(address.canSkipMemoryReadFence());
        AMD64Address skipFenceAddress = new AMD64Address(AMD64.rax, AMD64.rbx, Stride.S1, 16, true);
        assertTrue(skipFenceAddress.canSkipMemoryReadFence());
        assertDeepEquals(address.toString(), skipFenceAddress.toString());

        AMD64AddressValue addressValue = new AMD64AddressValue(LIRKind.value(AMD64Kind.QWORD), AMD64.rax.asValue(), AMD64.rbx.asValue(), Stride.S1, 16);
        assertFalse(addressValue.canSkipMemoryReadFence());
        AMD64AddressValue skipFenceAddressValue = new AMD64AddressValue(LIRKind.value(AMD64Kind.QWORD), AMD64.rax.asValue(), AMD64.rbx.asValue(), Stride.S1, 16, true);
        assertTrue(skipFenceAddressValue.canSkipMemoryReadFence());
        assertTrue(skipFenceAddressValue.withKind(LIRKind.value(AMD64Kind.DWORD)).canSkipMemoryReadFence());
        assertFalse(addressValue.toAddress(new AMD64MacroAssembler(target)).canSkipMemoryReadFence());
        assertTrue(skipFenceAddressValue.toAddress(new AMD64MacroAssembler(target)).canSkipMemoryReadFence());
    }

    private void assertInterceptsMemoryRead(TargetDescription target, boolean expected, Consumer<InterceptCountingAssembler> emit) {
        assertInterceptsMemoryRead(target, expected, emit, null);
    }

    private void assertMROpClassification(TargetDescription target, AMD64MROp op, OperandSize size, AMD64Address address, boolean expectedMemoryRead) {
        assertDeepEquals(op + " isMemRead", expectedMemoryRead, op.isMemRead());
        assertInterceptsMemoryRead(target, expectedMemoryRead, asm -> op.emit(asm, size, address, AMD64.rcx), op + " " + size);
    }

    private void assertMROpClassification(TargetDescription target, AMD64MROp op, OperandSize size, AMD64Address address, Register src, boolean expectedMemoryRead) {
        assertDeepEquals(op + " isMemRead", expectedMemoryRead, op.isMemRead());
        assertInterceptsMemoryRead(target, expectedMemoryRead, asm -> op.emit(asm, size, address, src), op + " " + size);
    }

    private void assertMOpClassification(TargetDescription target, AMD64MOp op, OperandSize size, AMD64Address address, boolean expectedMemoryRead) {
        assertDeepEquals(op + " isMemRead", expectedMemoryRead, op.isMemRead());
        assertInterceptsMemoryRead(target, expectedMemoryRead, asm -> op.emit(asm, size, address), op + " " + size);
    }

    private void assertMIOpClassification(TargetDescription target, AMD64MIOp op, OperandSize size, AMD64Address address, boolean expectedMemoryRead) {
        assertDeepEquals(op + " isMemRead", expectedMemoryRead, op.isMemRead());
        assertInterceptsMemoryRead(target, expectedMemoryRead, asm -> op.emit(asm, size, address, 1), op + " " + size);
    }

    private void assertInterceptsMemoryRead(TargetDescription target, boolean expected, Consumer<InterceptCountingAssembler> emit, String message) {
        InterceptCountingAssembler asm = new InterceptCountingAssembler(target);
        emit.accept(asm);
        assertDeepEquals(message, expected ? 1 : 0, asm.intercepts);
    }

    private void assertFallbackFencePrecedesLockPrefix(TargetDescription target, Consumer<FencingAssembler> emit) {
        FencingAssembler asm = new FencingAssembler(target);
        emit.accept(asm);
        assertDeepEquals(0x0F, asm.getByte(0));
        assertDeepEquals(0xAE, asm.getByte(1));
        assertDeepEquals(0xE8, asm.getByte(2));
        assertDeepEquals(0xF0, asm.getByte(3));
    }

    private static OperandSize testSize(AMD64Op op) {
        OperandSize[] allowedSizes = op.getAllowedSizes();
        for (OperandSize size : allowedSizes) {
            if (size == OperandSize.DWORD) {
                return OperandSize.DWORD;
            }
        }
        for (OperandSize size : allowedSizes) {
            if (size == OperandSize.QWORD) {
                return OperandSize.QWORD;
            }
        }
        return allowedSizes[0];
    }

    private static <T> List<NamedOp<T>> opsFromClass(Class<?> holder, Class<T> opcodeType) throws IllegalAccessException {
        List<NamedOp<T>> opcodes = new ArrayList<>();
        for (Field field : holder.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && opcodeType.isAssignableFrom(field.getType())) {
                opcodes.add(new NamedOp<>(field.getName(), opcodeType.cast(field.get(null))));
            }
        }
        assertFalse(holder.getName(), opcodes.isEmpty());
        return opcodes;
    }

    private static boolean expectedMemoryRead(NamedOp<?> namedOp, Set<String> memoryReads, Set<String> writeOnly) {
        assertTrue("Classify " + namedOp.name + " as either a memory-reading opcode or a write-only opcode in " + AMD64AssemblerUtilityTest.class.getSimpleName(),
                        memoryReads.contains(namedOp.name) || writeOnly.contains(namedOp.name));
        assertFalse("Classify " + namedOp.name + " in exactly one opcode classification set in " + AMD64AssemblerUtilityTest.class.getSimpleName(),
                        memoryReads.contains(namedOp.name) && writeOnly.contains(namedOp.name));
        return memoryReads.contains(namedOp.name);
    }

    private static final class NamedOp<T> {
        private final String name;
        private final T op;

        private NamedOp(String name, T op) {
            this.name = name;
            this.op = op;
        }
    }

    private static final class InterceptCountingAssembler extends AMD64Assembler {
        private int intercepts;

        private InterceptCountingAssembler(TargetDescription target) {
            super(target);
        }

        @Override
        public void interceptMemorySrcOperands(AMD64Address addr) {
            intercepts++;
        }
    }

    private static final class FencingAssembler extends AMD64MacroAssembler {
        private FencingAssembler(TargetDescription target) {
            super(target);
        }

        private FencingAssembler(TargetDescription target, OptionValues optionValues) {
            super(target, optionValues, true);
        }

        @Override
        public void interceptMemorySrcOperands(AMD64Address addr) {
            lfenceBeforeLock();
        }

        @Override
        public int extraSourceAddressBytes(AMD64Address addr) {
            return 3;
        }
    }
}
