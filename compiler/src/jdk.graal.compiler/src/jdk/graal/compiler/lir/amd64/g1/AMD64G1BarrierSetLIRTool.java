/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64.g1;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.gen.G1BarrierSetLIRTool;
import jdk.vm.ci.code.Register;

/**
 * Platform dependent code generation for G1 barrier operations.
 */
public interface AMD64G1BarrierSetLIRTool extends G1BarrierSetLIRTool {

    /**
     * Return the current thread in a register. Usually this is a fixed register which can't be
     * changed.
     */
    Register getThread(AMD64MacroAssembler masm);

    /**
     * Compute the card address into {@code cardAddress}.
     */
    void computeCard(Register cardAddress, Register storeAddress, Register cardtable, AMD64MacroAssembler masm);

    /**
     * Load an object field from {@code immediateAddress} into value.
     */
    void loadObject(AMD64MacroAssembler masm, Register previousValue, AMD64Address storeAddress);

    /**
     * Perform some lightweight validation that {@code value} is a valid object.
     */
    void verifyOop(AMD64MacroAssembler masm, Register value, Register tmp, Register tmp2, boolean compressed, boolean nonNull);
}
