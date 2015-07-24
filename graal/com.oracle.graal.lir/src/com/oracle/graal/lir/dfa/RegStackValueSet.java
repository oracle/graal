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
package com.oracle.graal.lir.dfa;

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.framemap.*;

final class RegStackValueSet extends LiveValueSet<RegStackValueSet> {

    private final FrameMap frameMap;
    private final ValueSet registers;
    private final ValueSet stack;
    private Set<Value> extraStack;

    public RegStackValueSet(FrameMap frameMap) {
        this.frameMap = frameMap;
        registers = new ValueSet();
        stack = new ValueSet();
    }

    private RegStackValueSet(FrameMap frameMap, RegStackValueSet s) {
        this.frameMap = frameMap;
        registers = new ValueSet(s.registers);
        stack = new ValueSet(s.stack);
        if (s.extraStack != null) {
            extraStack = new HashSet<>(s.extraStack);
        }
    }

    @Override
    public RegStackValueSet copy() {
        return new RegStackValueSet(frameMap, this);
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

    public void addLiveValues(ReferenceMapBuilder refMap) {
        ValueConsumer addLiveValue = new ValueConsumer() {
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                refMap.addLiveValue(value);
            }
        };
        registers.forEach(null, null, null, addLiveValue);
        stack.forEach(null, null, null, addLiveValue);
        if (extraStack != null) {
            for (Value v : extraStack) {
                refMap.addLiveValue(v);
            }
        }
    }
}
