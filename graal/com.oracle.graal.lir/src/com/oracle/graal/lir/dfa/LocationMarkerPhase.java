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

    private static final class Marker<T extends AbstractBlockBase<T>> extends LocationMarker<T, Marker<T>.RegStackValueSet> {

        private final class RegStackValueSet extends LiveValueSet<Marker<T>.RegStackValueSet> {

            private final ValueSet registers;
            private final ValueSet stack;
            private Set<Value> extraStack;

            public RegStackValueSet() {
                registers = new ValueSet();
                stack = new ValueSet();
            }

            private RegStackValueSet(RegStackValueSet s) {
                registers = new ValueSet(s.registers);
                stack = new ValueSet(s.stack);
                if (s.extraStack != null) {
                    extraStack = new HashSet<>(s.extraStack);
                }
            }

            @Override
            public Marker<T>.RegStackValueSet copy() {
                return new RegStackValueSet(this);
            }

            @Override
            public void put(Value v) {
                if (isRegister(v)) {
                    int index = asRegister(v).getReferenceMapIndex();
                    registers.put(index, v);
                } else if (isStackSlot(v)) {
                    int index = frameMap.offsetForStackSlot(asStackSlot(v));
                    assert index >= 0;
                    if (index % 4 == 0) {
                        stack.put(index / 4, v);
                    } else {
                        if (extraStack == null) {
                            extraStack = new HashSet<>();
                        }
                        extraStack.add(v);
                    }
                }
            }

            @Override
            public void putAll(RegStackValueSet v) {
                registers.putAll(v.registers);
                stack.putAll(v.stack);
                if (v.extraStack != null) {
                    if (extraStack == null) {
                        extraStack = new HashSet<>();
                    }
                    extraStack.addAll(v.extraStack);
                }
            }

            @Override
            public void remove(Value v) {
                if (isRegister(v)) {
                    int index = asRegister(v).getReferenceMapIndex();
                    registers.put(index, null);
                } else if (isStackSlot(v)) {
                    int index = frameMap.offsetForStackSlot(asStackSlot(v));
                    assert index >= 0;
                    if (index % 4 == 0) {
                        stack.put(index / 4, null);
                    } else if (extraStack != null) {
                        extraStack.remove(v);
                    }
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Marker.RegStackValueSet) {
                    RegStackValueSet other = (RegStackValueSet) obj;
                    return registers.equals(other.registers) && stack.equals(other.stack) && Objects.equals(extraStack, other.extraStack);
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() {
                throw new UnsupportedOperationException();
            }

            public void addLiveValues(ReferenceMapBuilder refMap) {
                registers.addLiveValues(refMap);
                stack.addLiveValues(refMap);
                if (extraStack != null) {
                    for (Value v : extraStack) {
                        refMap.addLiveValue(v);
                    }
                }
            }
        }

        private final RegisterAttributes[] registerAttributes;

        private Marker(LIR lir, FrameMap frameMap) {
            super(lir, frameMap);
            this.registerAttributes = frameMap.getRegisterConfig().getAttributesMap();
        }

        @Override
        protected Marker<T>.RegStackValueSet newLiveValueSet() {
            return new RegStackValueSet();
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
