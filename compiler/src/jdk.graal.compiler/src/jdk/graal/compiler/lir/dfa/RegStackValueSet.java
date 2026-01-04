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
package jdk.graal.compiler.lir.dfa;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.ValueConsumer;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.framemap.ReferenceMapBuilder;
import jdk.graal.compiler.lir.util.IndexedValueMap;
import jdk.graal.compiler.lir.util.ValueSet;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.Value;

final class RegStackValueSet extends ValueSet<RegStackValueSet> {

    private final FrameMap frameMap;
    private final IndexedValueMap registers;
    private final IndexedValueMap stack;
    private Map<Integer, Value> extraStack;

    RegStackValueSet(FrameMap frameMap) {
        this.frameMap = frameMap;
        registers = new IndexedValueMap();
        stack = new IndexedValueMap();
    }

    private RegStackValueSet(FrameMap frameMap, RegStackValueSet s) {
        this.frameMap = frameMap;
        registers = new IndexedValueMap(s.registers);
        stack = new IndexedValueMap(s.stack);
        if (s.extraStack != null) {
            extraStack = new EconomicHashMap<>(s.extraStack);
        }
    }

    @Override
    public RegStackValueSet copy() {
        return new RegStackValueSet(frameMap, this);
    }

    @Override
    public void put(Value v) {
        if (!shouldProcessValue(v)) {
            return;
        }
        if (isRegister(v)) {
            int index = asRegister(v).number;
            registers.put(index, v);
        } else if (isStackSlot(v)) {
            int index = frameMap.offsetForStackSlot(asStackSlot(v));
            assert NumUtil.assertNonNegativeInt(index);
            if (index % 4 == 0) {
                stack.put(index / 4, v);
            } else {
                if (extraStack == null) {
                    extraStack = new EconomicHashMap<>();
                }
                extraStack.put(index, v);
            }
        }
    }

    @Override
    public void putAll(RegStackValueSet v) {
        registers.putAll(v.registers);
        stack.putAll(v.stack);
        if (v.extraStack != null) {
            if (extraStack == null) {
                extraStack = new EconomicHashMap<>();
            }
            extraStack.putAll(v.extraStack);
        }
    }

    @Override
    public void remove(Value v) {
        if (!shouldProcessValue(v)) {
            return;
        }
        if (isRegister(v)) {
            int index = asRegister(v).number;
            guaranteeEquals(v, registers.get(index));
            registers.put(index, null);
        } else if (isStackSlot(v)) {
            int index = frameMap.offsetForStackSlot(asStackSlot(v));
            assert NumUtil.assertNonNegativeInt(index);
            if (index % 4 == 0) {
                guaranteeEquals(v, stack.get(index / 4));
                stack.put(index / 4, null);
            } else if (extraStack != null) {
                guaranteeEquals(v, extraStack.get(index));
                extraStack.remove(index);
            }
        }
    }

    /**
     * Ensure that the uses and the defs agree about the value.
     */
    private static void guaranteeEquals(Value v1, Value v2) {
        if (v1 == null || v2 == null || v1.equals(v2)) {
            return;
        }
        if (!LIRValueUtil.uncast(v1).equals(LIRValueUtil.uncast(v2))) {
            throw new GraalError("mismatched definition: %s != %s", v1, v2);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RegStackValueSet) {
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

    private static boolean shouldProcessValue(Value v) {
        /*
         * We always process registers because we have to track the largest register size that is
         * alive across safepoints in order to save and restore them.
         */
        return isRegister(v) || !LIRKind.isValue(v);
    }

    public void addLiveValues(ReferenceMapBuilder refMap) {
        ValueConsumer addLiveValue = new ValueConsumer() {
            @Override
            public void visitValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                refMap.addLiveValue(value);
            }
        };
        registers.visitEach(null, null, null, addLiveValue);
        stack.visitEach(null, null, null, addLiveValue);
        if (extraStack != null) {
            for (Value v : extraStack.values()) {
                refMap.addLiveValue(v);
            }
        }
    }

    @Override
    public String toString() {
        return "registers: " + registers.toString() + "\n" + "stack: " + stack.toString();
    }
}
