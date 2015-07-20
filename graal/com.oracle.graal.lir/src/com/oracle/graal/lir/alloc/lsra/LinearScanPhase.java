/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.alloc.lsra;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.options.*;
import jdk.internal.jvmci.options.DerivedOptionValue.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.lir.ssa.*;

public final class LinearScanPhase extends AllocationPhase {

    public static final DerivedOptionValue<Boolean> SSA_LSRA = new DerivedOptionValue<>(new OptionSupplier<Boolean>() {

        private static final long serialVersionUID = 9115795480259228194L;

        public Boolean get() {
            return SSA_LIR.getValue() && !SSADestructionPhase.Options.LIREagerSSADestruction.getValue();
        }
    });

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        final LinearScan allocator;
        if (LinearScanPhase.SSA_LSRA.getValue()) {
            allocator = new SSALinearScan(target, lirGenRes, spillMoveFactory, registerAllocationConfig, linearScanOrder);
        } else {
            allocator = new LinearScan(target, lirGenRes, spillMoveFactory, registerAllocationConfig, linearScanOrder);
        }
        allocator.allocate(target, lirGenRes, codeEmittingOrder, linearScanOrder, spillMoveFactory, registerAllocationConfig);
    }

}
