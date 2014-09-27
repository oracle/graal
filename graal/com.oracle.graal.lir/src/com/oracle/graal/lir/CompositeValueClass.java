/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.CompositeValue.Component;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;

/**
 * Lazily associated metadata for every {@link CompositeValue} type. The metadata includes:
 * <ul>
 * <li>The offsets of fields annotated with {@link Component} as well as methods for iterating over
 * such fields.</li>
 * </ul>
 */
public class CompositeValueClass extends LIRIntrospection {

    public static final CompositeValueClass get(Class<? extends CompositeValue> c) {
        CompositeValueClass clazz = (CompositeValueClass) allClasses.get(c);
        if (clazz != null) {
            return clazz;
        }

        // We can have a race of multiple threads creating the LIRInstructionClass at the same time.
        // However, only one will be put into the map, and this is the one returned by all threads.
        clazz = new CompositeValueClass(c);
        CompositeValueClass oldClazz = (CompositeValueClass) allClasses.putIfAbsent(c, clazz);
        if (oldClazz != null) {
            return oldClazz;
        } else {
            return clazz;
        }
    }

    public CompositeValueClass(Class<? extends CompositeValue> clazz) {
        this(clazz, new DefaultCalcOffset());
    }

    public CompositeValueClass(Class<? extends CompositeValue> clazz, CalcOffset calcOffset) {
        super(clazz);

        ValueFieldScanner vfs = new ValueFieldScanner(calcOffset);
        vfs.scan(clazz);

        values = new Values(vfs.valueAnnotations.get(CompositeValue.Component.class));
        data = new Fields(vfs.data);
    }

    private static class ValueFieldScanner extends FieldScanner {

        public ValueFieldScanner(CalcOffset calc) {
            super(calc);
            valueAnnotations.put(CompositeValue.Component.class, new OperandModeAnnotation());
        }

        @Override
        public void scan(Class<?> clazz) {
            super.scan(clazz);
        }

        @Override
        protected EnumSet<OperandFlag> getFlags(Field field) {
            EnumSet<OperandFlag> result = EnumSet.noneOf(OperandFlag.class);
            if (field.isAnnotationPresent(CompositeValue.Component.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(CompositeValue.Component.class).value()));
            } else {
                GraalInternalError.shouldNotReachHere();
            }
            return result;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(getClass().getSimpleName()).append(" ").append(getClazz().getSimpleName()).append(" components[");
        values.appendFields(str);
        str.append("] data[");
        data.appendFields(str);
        str.append("]");
        return str.toString();
    }

    final CompositeValue forEachComponent(LIRInstruction inst, CompositeValue obj, OperandMode mode, InstructionValueProcedureBase proc) {
        return super.forEachComponent(inst, obj, values, mode, proc);
    }

    final void forEachComponent(LIRInstruction inst, CompositeValue obj, OperandMode mode, ValuePositionProcedure proc, ValuePosition outerPosition) {
        forEach(inst, obj, values, mode, proc, outerPosition);
    }

    public String toString(CompositeValue obj) {
        StringBuilder result = new StringBuilder();

        appendValues(result, obj, "", "", "{", "}", new String[]{""}, values);

        for (int i = 0; i < data.getCount(); i++) {
            result.append(" ").append(data.getName(i)).append(": ").append(getFieldString(obj, i, data));
        }

        return result.toString();
    }

    Value getValue(CompositeValue obj, ValuePosition pos) {
        return getValueForPosition(obj, values, pos);
    }

    CompositeValue createUpdatedValue(CompositeValue compValue, ValuePosition pos, Value value) {
        CompositeValue newCompValue = compValue.clone();
        setValueForPosition(newCompValue, values, pos, value);
        return newCompValue;
    }

    EnumSet<OperandFlag> getFlags(ValuePosition pos) {
        return values.getFlags(pos.getIndex());
    }

    void copyValueArrays(CompositeValue compositeValue) {
        for (int i = values.getDirectCount(); i < values.getCount(); i++) {
            Value[] valueArray = values.getValueArray(compositeValue, i);
            Value[] newValueArray = Arrays.copyOf(valueArray, valueArray.length);
            values.setValueArray(compositeValue, i, newValueArray);
        }
    }
}
