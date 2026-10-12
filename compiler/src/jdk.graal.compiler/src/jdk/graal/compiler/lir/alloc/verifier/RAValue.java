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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.LIRKindWithCast;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Wrapper around Value to change how indexing in data structures like {@link java.util.Map} or
 * {@link java.util.Set} is done.
 *
 * <p>
 * Values are indexed without their {@link LIRKind kind} associated with them, this is necessary for
 * {@link AllocationStateMap} because locations can change kinds and still be associated with one
 * key/value pair in the map.
 * </p>
 *
 * <p>
 * Some value types like {@link RAVConstant constants}, {@link RAVariable variables},
 * {@link RAVRegister registers} have their own subclass, to use them as types for keys and values
 * in collections.
 * </p>
 */
public class RAValue {
    /**
     * Create a new RAValue instance from {@link Value}.
     *
     * @param value Value we are wrapping
     * @return Instance of RAValue
     */
    public static RAValue create(Value value) {
        if (LIRValueUtil.isVariable(value)) {
            return new RAVariable(LIRValueUtil.asVariable(value));
        }

        if (ValueUtil.isRegister(value)) {
            return new RAVRegister(ValueUtil.asRegisterValue(value));
        }

        if (LIRValueUtil.isConstantValue(value)) {
            return new RAVConstant(LIRValueUtil.asConstantValue(value), true);
        }

        return new RAValue(value);
    }

    protected static boolean kindsEqual(RAValue orig, RAValue location) {
        var origKind = orig.getLIRKind();
        var currKind = location.getLIRKind();
        if (currKind instanceof LIRKindWithCast castKind) {
            currKind = (LIRKind) castKind.getActualKind();
        }

        return origKind.equals(currKind);
    }

    public static RAValue cast(RAValue symbol, RAValue kindSrc) {
        RAValue castOrig;
        if (symbol.isVariable()) {
            castOrig = RAValue.create(new Variable(kindSrc.getLIRKind(), symbol.asVariable().getVariable().index));
        } else if (symbol.isConstant()) {
            castOrig = RAValue.create(new ConstantValue(kindSrc.getLIRKind(), symbol.asConstant().getConstant()));
        } else {
            throw new GraalError("should not reach here: cannot cast orig " + symbol);
        }
        return castOrig;
    }

    protected final Value value;

    protected RAValue(Value value) {
        this.value = value;
    }

    public Value getValue() {
        return this.value;
    }

    public boolean isIllegal() {
        return Value.ILLEGAL.equals(value);
    }

    public RAVariable asVariable() {
        return (RAVariable) this;
    }

    public boolean isVariable() {
        return false;
    }

    public boolean isConstant() {
        return false;
    }

    public RAVConstant asConstant() {
        return (RAVConstant) this;
    }

    public boolean isRegister() {
        return false;
    }

    public RAVRegister asRegister() {
        return (RAVRegister) this;
    }

    public LIRKind getLIRKind() {
        if (isIllegal() || value.getValueKind().equals(ValueKind.Illegal)) {
            return LIRKind.Illegal;
        }

        return value.getValueKind(LIRKind.class);
    }

    public boolean isLocation() {
        return isRegister() || isStackSlot();
    }

    public boolean isStackSlot() {
        return LIRValueUtil.isStackSlotValue(value);
    }

    @Override
    public int hashCode() {
        if (LIRValueUtil.isVirtualStackSlot(this.value)) {
            return LIRValueUtil.asVirtualStackSlot(this.value).getId();
        }

        if (ValueUtil.isStackSlot(this.value)) {
            var stackSlot = ValueUtil.asStackSlot(this.value);
            return stackSlot.getRawOffset();
        }

        return this.value.hashCode();
    }

    /**
     * Are two {@link RAValue values} equal? - check for offset for
     * {@link jdk.vm.ci.code.StackSlot}, check for id for
     * {@link jdk.graal.compiler.lir.VirtualStackSlot}, otherwise default to normal equals on
     * {@link Value}
     *
     * @param other The reference object with which to compare.
     * @return Are said values equal?
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof RAValue otherValueWrap) {
            if (LIRValueUtil.isVirtualStackSlot(this.value) && LIRValueUtil.isVirtualStackSlot(otherValueWrap.value)) {
                return LIRValueUtil.asVirtualStackSlot(this.value).getId() == LIRValueUtil.asVirtualStackSlot(otherValueWrap.value).getId();
            }

            if (ValueUtil.isStackSlot(this.value) && ValueUtil.isStackSlot(otherValueWrap.value)) {
                var ourSlot = ValueUtil.asStackSlot(this.value);
                var otherSlot = ValueUtil.asStackSlot(otherValueWrap.value);

                return ourSlot.getRawOffset() == otherSlot.getRawOffset() && ourSlot.getRawAddFrameSize() == otherSlot.getRawAddFrameSize();
            }

            return this.value.equals(otherValueWrap.value);
        }

        return false;
    }

    @Override
    public String toString() {
        if (LIRValueUtil.isVirtualStackSlot(this.value)) {
            return "vstack:" + LIRValueUtil.asVirtualStackSlot(this.value).getId();
        }

        if (ValueUtil.isStackSlot(this.value)) {
            return "stack:" + ValueUtil.asStackSlot(this.value).getRawOffset();
        }

        return value.toString();
    }
}
