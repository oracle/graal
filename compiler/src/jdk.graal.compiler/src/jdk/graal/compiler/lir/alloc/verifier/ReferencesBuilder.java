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
package jdk.graal.compiler.lir.alloc.verifier;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.dfa.LocationMarker;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.util.ValueSet;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.meta.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build references list for operations that can be used to in-validate references that are not part
 * of it to make sure GC-freed references are not used further.
 *
 * <p>
 * It uses the {@link LocationMarker} class that is used in the
 * {@link jdk.graal.compiler.lir.phases.FinalCodeAnalysisPhase} where the actual set of references
 * is built, we require this information earlier.
 * </p>
 */
public class ReferencesBuilder {
    class ReferenceSet extends ValueSet<ReferenceSet> {
        protected Set<RAValue> references;

        ReferenceSet() {
            references = new EconomicHashSet<>();
        }

        ReferenceSet(ReferenceSet other) {
            references = new EconomicHashSet<>(other.references);
        }

        @Override
        public void put(Value v) {
            if (v.getValueKind(LIRKind.class).isValue()) {
                return;
            }

            references.add(RAValue.create(v));
        }

        @Override
        public void remove(Value v) {
            references.remove(RAValue.create(v));
        }

        @Override
        public void putAll(ReferenceSet other) {
            references.addAll(other.references);
        }

        @Override
        public ReferenceSet copy() {
            return new ReferenceSet(this);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ReferenceSet other) {
                return other.references.equals(references);
            }
            return false;
        }

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException();
        }
    }

    class ReferenceMarker extends LocationMarker<ReferenceSet> {
        private final List<RegisterAttributes> registerAttributes;
        protected final Map<LIRInstruction, RAVInstruction.Base> preAllocMap;

        protected ReferenceMarker(LIR lir, FrameMap frameMap, Map<LIRInstruction, RAVInstruction.Base> preAllocMap) {
            super(lir, frameMap);
            this.registerAttributes = frameMap.getRegisterConfig().getAttributesMap();
            this.preAllocMap = preAllocMap;
        }

        @Override
        protected ReferenceSet newLiveValueSet() {
            return new ReferenceSet();
        }

        /**
         * Only process values that are registers (that can contain references and are allocatable)
         * and stack slots, where kind cannot be Illegal.
         *
         * <p>
         * Take from jdk.graal.compiler.lir.dfa.LocationMarkerPhase.Marker#shouldProcessValue
         * </p>
         *
         * @param operand Value to be processed
         * @return If matches criteria for LocationMarker processing
         */
        @Override
        protected boolean shouldProcessValue(Value operand) {
            if (isRegister(operand)) {
                Register reg = asRegister(operand);
                if (!reg.mayContainReference() || !attributes(reg).isAllocatable()) {
                    // register that's not allocatable or not part of the reference map
                    return false;
                }
            } else if (!isStackSlot(operand)) {
                // neither register nor stack slot
                return false;
            }

            return !operand.getValueKind().equals(LIRKind.Illegal);
        }

        private RegisterAttributes attributes(Register reg) {
            return registerAttributes.get(reg.number);
        }

        /**
         * Set references to the operation if it can be found in the pre-allocation map.
         *
         * @param instruction LIR instruction that holds these references
         * @param info LIR frame state
         * @param values Set of references for this instruction
         */
        @Override
        protected void processState(LIRInstruction instruction, LIRFrameState info, ReferenceSet values) {
            if (!preAllocMap.containsKey(instruction)) {
                return;
            }

            var op = (RAVInstruction.Op) preAllocMap.get(instruction);
            op.references = new EconomicHashSet<>(values.references); // Has to be a copy here
        }
    }

    /**
     * Build references for {@link RAVInstruction.Op intructions} to check GC roots.
     *
     * @param lir LIR
     * @param frameMap Frame map necessary to build references
     * @param preAllocMap Map of instructions before allocation
     */
    public void build(LIR lir, FrameMap frameMap, Map<LIRInstruction, RAVInstruction.Base> preAllocMap) {
        new ReferenceMarker(lir, frameMap, preAllocMap).build();
    }
}
