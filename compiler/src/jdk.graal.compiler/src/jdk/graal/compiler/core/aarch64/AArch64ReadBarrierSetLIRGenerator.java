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
package jdk.graal.compiler.core.aarch64;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.ReadBarrierSetLIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AArch64 specific LIR generation for GC barriers.
 */
public interface AArch64ReadBarrierSetLIRGenerator extends ReadBarrierSetLIRGeneratorTool {

    /**
     * Emit an atomic read-and-write instruction with any required GC barriers.
     */
    Value emitAtomicReadAndWrite(LIRGeneratorTool tool, LIRKind readKind, Value address, Value newValue, BarrierType barrierType);

    /**
     * Emit an atomic compare and swap with any required GC barriers.
     */
    void emitCompareAndSwapOp(LIRGeneratorTool tool, boolean isLogic, Value address, MemoryOrderMode memoryOrder, AArch64Kind memKind, Variable result,
                    AllocatableValue allocatableExpectedValue, AllocatableValue allocatableNewValue, BarrierType barrierType);

}
