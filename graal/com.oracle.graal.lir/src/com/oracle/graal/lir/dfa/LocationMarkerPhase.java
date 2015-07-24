/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.dfa;

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;

/**
 * Mark all live references for a frame state. The frame state use this information to build the OOP
 * maps.
 */
public final class LocationMarkerPhase extends AllocationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        new Marker<B>(lirGenRes.getLIR(), lirGenRes.getFrameMap()).build();
    }

    static final class Marker<T extends AbstractBlockBase<T>> extends LocationMarker<T, RegStackValueSet> {

        private final RegisterAttributes[] registerAttributes;

        private Marker(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
            this.registerAttributes = frameMap.getRegisterConfig().getAttributesMap();
        }

        @Override
        protected RegStackValueSet newLiveValueSet() {
            return new RegStackValueSet(frameMap);
        }

        @Override
        protected boolean shouldProcessValue(Value operand) {
            return (isRegister(operand) && attributes(asRegister(operand)).isAllocatable() || isStackSlot(operand)) && operand.getPlatformKind() != Kind.Illegal;
        }

        /**
         * This method does the actual marking.
         */
        @Override
        protected void processState(LIRInstruction op, LIRFrameState info, RegStackValueSet values) {
            if (!info.hasDebugInfo()) {
                info.initDebugInfo(frameMap, !op.destroysCallerSavedRegisters() || !frameMap.getRegisterConfig().areAllAllocatableRegistersCallerSaved());
            }

            ReferenceMapBuilder refMap = frameMap.newReferenceMapBuilder();
            frameMap.addLiveValues(refMap);
            values.addLiveValues(refMap);

            info.debugInfo().setReferenceMap(refMap.finish(info));
        }

        /**
         * Gets an object describing the attributes of a given register according to this register
         * configuration.
         */
        private RegisterAttributes attributes(Register reg) {
            return registerAttributes[reg.number];
        }

    }
}
