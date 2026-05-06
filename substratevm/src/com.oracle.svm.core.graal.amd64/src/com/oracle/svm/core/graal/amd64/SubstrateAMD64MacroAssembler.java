/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import com.oracle.svm.core.ReservedRegisters;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class was introduced for the {@link AMD64MemoryMaskingAndFencing} mitigation. It allows
 * fine-grained control of op codes that use memory as source operand, needed to intercept addresses
 * created after the {@link AMD64AddressLoweringAndMaskingByNodePhase} has run. It emits LFENCEs on
 * top of each memory access that is not already masked or fenced.
 */
public class SubstrateAMD64MacroAssembler extends AMD64MacroAssembler {

    /**
     * Position of the most recent emitted protection marker, either from an explicit {@code lfence}
     * or from a source-address computation that was already protected before the dereference. A
     * value of {@code -1} means that no protection marker has been recorded yet.
     */
    private int lastMarkedPosition = -1;

    public SubstrateAMD64MacroAssembler(TargetDescription target, OptionValues optionValues, boolean hasIntelJccErratum) {
        super(target, optionValues, hasIntelJccErratum);
    }

    @Override
    public void interceptMemorySrcOperands(AMD64Address addr) {
        if (memorySrcNeedsFence(addr)) {
            lfenceBeforeLock();
        }
    }

    private boolean memorySrcNeedsFence(AMD64Address addr) {

        if (!AMD64MemoryMaskingAndFencing.isEnabled()) {
            return false;
        }

        /**
         * If the base is a reserved register and the index is invalid, we don't need protection.
         * Heap-base-relative addresses with a live index are only safe when the lowering or
         * emission phase explicitly marked the address computation as already protected.
         */
        if (ReservedRegisters.singleton().isReservedRegister(addr.getBase()) && !addr.getIndex().isValid()) {
            return false;
        }

        /**
         * Accesses that cannot be attacker-controlled do not need protection.
         */
        if (addr.getBase().equals(Register.None) && addr.getIndex().equals(Register.None)) {
            return false;
        }

        /*
         * If the current position has been marked, then we either already emitted the lfence, or we
         * don't need it.
         */
        if (position() == lastMarkedPosition) {
            return false;
        }

        // If there is no lfence on top of the last position, then we need to add it.
        return true;
    }

    @Override
    public int extraSourceAddressBytes(AMD64Address addr) {
        return memorySrcNeedsFence(addr) ? 3 : 0;
    }

    @Override
    public void lfence() {
        super.lfence();
        lastMarkedPosition = position();
    }

    @Override
    public void lock() {
        /**
         * The lock prefix can only be added to read-modify-write instruction. Those instructions,
         * even if executed speculatively on a memory location that shouldn't be accessible, will
         * require an additional read to the same memory location, which will be properly masked. An
         * exception are the "compare and exchange" instructions, since they can return the value
         * stored in the memory location used as destination operand.
         */
        super.lock();
        lastMarkedPosition = position();
    }

    public void markImageHeapConstantLea() {
        markProtectedMemorySourceAddress();
    }

    public void markProtectedMemorySourceAddress() {
        /*
         * The next source-memory operand uses an address that was already protected by the lowering
         * or emission phase, so it does not need an additional fallback fence.
         */
        this.lastMarkedPosition = position();
    }
}
