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
public class CompositeValueClass<T> extends LIRIntrospection<T> {

    public static final <T extends CompositeValue> CompositeValueClass<T> create(Class<T> c) {
        return new CompositeValueClass<>(c);
    }

    public CompositeValueClass(Class<T> clazz) {
        this(clazz, new FieldsScanner.DefaultCalcOffset());
    }

    public CompositeValueClass(Class<T> clazz, FieldsScanner.CalcOffset calcOffset) {
        super(clazz);

        CompositeValueFieldsScanner vfs = new CompositeValueFieldsScanner(calcOffset);
        vfs.scan(clazz, CompositeValue.class, false);

        values = new Values(vfs.valueAnnotations.get(CompositeValue.Component.class));
        data = new Fields(vfs.data);
    }

    private static class CompositeValueFieldsScanner extends LIRFieldsScanner {

        public CompositeValueFieldsScanner(FieldsScanner.CalcOffset calc) {
            super(calc);
            valueAnnotations.put(CompositeValue.Component.class, new OperandModeAnnotation());
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

    final CompositeValue forEachComponent(LIRInstruction inst, CompositeValue obj, OperandMode mode, InstructionValueProcedure proc) {
        return super.forEachComponent(inst, obj, values, mode, proc);
    }

    public String toString(CompositeValue obj) {
        StringBuilder result = new StringBuilder();

        appendValues(result, obj, "", "", "{", "}", new String[]{""}, values);

        for (int i = 0; i < data.getCount(); i++) {
            result.append(" ").append(data.getName(i)).append(": ").append(getFieldString(obj, i, data));
        }

        return result.toString();
    }

    void copyValueArrays(CompositeValue compositeValue) {
        for (int i = values.getDirectCount(); i < values.getCount(); i++) {
            Value[] valueArray = values.getValueArray(compositeValue, i);
            Value[] newValueArray = Arrays.copyOf(valueArray, valueArray.length);
            values.setValueArray(compositeValue, i, newValueArray);
        }
    }
}
