/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.gen;

import org.graalvm.compiler.lir.LIRInstruction;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public interface DiagnosticLIRGeneratorTool {
    LIRInstruction createBenchmarkCounter(String name, String group, Value increment);

    LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments);

    /**
     * Creates an instruction that fills a given set of registers with a known garbage value.
     *
     * @param zappedRegisters registers to be zapped
     * @param zapValues values used for zapping
     *
     * @see DiagnosticLIRGeneratorTool#createZapRegisters()
     */
    LIRInstruction createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues);

    /**
     * Creates an instruction that fills a given set of registers with a
     * {@link LIRGenerator#zapValueForKind known garbage value}.
     *
     * @param zappedRegisters registers to be zapped
     *
     * @see DiagnosticLIRGeneratorTool#createZapRegisters()
     */
    LIRInstruction createZapRegisters(Register[] zappedRegisters);

    /**
     * Creates an instruction that fills all {@link RegisterConfig#getAllocatableRegisters()
     * allocatable registers} with a {@link LIRGenerator#zapValueForKind known garbage value}.
     *
     * @see DiagnosticLIRGeneratorTool#createZapRegisters(Register[], JavaConstant[])
     */
    LIRInstruction createZapRegisters();

    /**
     * Marker interface for {@link LIRInstruction instructions} that should be succeeded with a
     * {@link DiagnosticLIRGeneratorTool#createZapRegisters() ZapRegisterOp} if assertions are
     * enabled.
     */
    interface ZapRegistersAfterInstruction {
    }

    /**
     * Marker interface for {@link LIRInstruction instructions} that should be preceded with a
     * {@link DiagnosticLIRGeneratorTool#zapArgumentSpace ZapArgumentSpaceOp} if assertions are
     * enabled.
     */
    interface ZapStackArgumentSpaceBeforeInstruction {
    }

    LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues);

    LIRInstruction zapArgumentSpace();
}
