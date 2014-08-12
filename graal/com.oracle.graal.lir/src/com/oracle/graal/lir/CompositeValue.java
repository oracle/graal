/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import java.lang.annotation.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;

/**
 * Base class to represent values that need to be stored in more than one register.
 */
public abstract class CompositeValue extends Value implements Cloneable {

    private static final long serialVersionUID = -169180052684126180L;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Component {

        OperandFlag[] value() default OperandFlag.REG;
    }

    private final CompositeValueClass valueClass;

    private static final DebugMetric COMPOSITE_VALUE_COUNT = Debug.metric("CompositeValues");

    public CompositeValue(LIRKind kind) {
        super(kind);
        COMPOSITE_VALUE_COUNT.increment();
        valueClass = CompositeValueClass.get(getClass());
    }

    public final CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedureBase proc) {
        return valueClass.forEachComponent(inst, this, mode, proc);
    }

    public final void forEachComponent(LIRInstruction inst, OperandMode mode, ValuePositionProcedure proc, ValuePosition outerPosition) {
        valueClass.forEachComponent(inst, this, mode, proc, outerPosition);
    }

    @Override
    public String toString() {
        return valueClass.toString(this);
    }

    @Override
    public int hashCode() {
        return 53 * super.hashCode() + valueClass.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CompositeValue) {
            CompositeValue other = (CompositeValue) obj;
            return super.equals(other) && valueClass.equals(other.valueClass);
        }
        return false;
    }

    CompositeValueClass getValueClass() {
        return valueClass;
    }

    @Override
    public final CompositeValue clone() {
        CompositeValue compositeValue = null;
        try {
            compositeValue = (CompositeValue) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new GraalInternalError(e);
        }

        // copy value arrays
        getValueClass().copyValueArrays(compositeValue);

        return compositeValue;
    }

}
