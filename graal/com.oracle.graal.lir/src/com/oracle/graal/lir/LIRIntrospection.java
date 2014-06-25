/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.LIRInstruction.InstructionValueProcedure;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;

abstract class LIRIntrospection extends FieldIntrospection {

    private static final Class<Value> VALUE_CLASS = Value.class;
    private static final Class<Constant> CONSTANT_CLASS = Constant.class;
    private static final Class<Variable> VARIABLE_CLASS = Variable.class;
    private static final Class<RegisterValue> REGISTER_VALUE_CLASS = RegisterValue.class;
    private static final Class<StackSlot> STACK_SLOT_CLASS = StackSlot.class;
    private static final Class<Value[]> VALUE_ARRAY_CLASS = Value[].class;

    public LIRIntrospection(Class<?> clazz) {
        super(clazz);
    }

    protected static class OperandModeAnnotation {

        public final ArrayList<Long> scalarOffsets = new ArrayList<>();
        public final ArrayList<Long> arrayOffsets = new ArrayList<>();
        public final Map<Long, EnumSet<OperandFlag>> flags = new HashMap<>();
    }

    protected abstract static class FieldScanner extends BaseFieldScanner {

        public final Map<Class<? extends Annotation>, OperandModeAnnotation> valueAnnotations;
        public final ArrayList<Long> stateOffsets = new ArrayList<>();

        public FieldScanner(CalcOffset calc) {
            super(calc);

            valueAnnotations = new HashMap<>();
        }

        protected OperandModeAnnotation getOperandModeAnnotation(Field field) {
            OperandModeAnnotation result = null;
            for (Entry<Class<? extends Annotation>, OperandModeAnnotation> entry : valueAnnotations.entrySet()) {
                Annotation annotation = field.getAnnotation(entry.getKey());
                if (annotation != null) {
                    assert result == null : "Field has two operand mode annotations: " + field;
                    result = entry.getValue();
                }
            }
            return result;
        }

        protected abstract EnumSet<OperandFlag> getFlags(Field field);

        @Override
        protected void scanField(Field field, Class<?> type, long offset) {
            if (VALUE_CLASS.isAssignableFrom(type) && type != CONSTANT_CLASS) {
                assert !Modifier.isFinal(field.getModifiers()) : "Value field must not be declared final because it is modified by register allocator: " + field;
                OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                assert annotation != null : "Field must have operand mode annotation: " + field;
                annotation.scalarOffsets.add(offset);
                EnumSet<OperandFlag> flags = getFlags(field);
                assert verifyFlags(field, type, flags);
                annotation.flags.put(offset, getFlags(field));
            } else if (VALUE_ARRAY_CLASS.isAssignableFrom(type)) {
                OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                assert annotation != null : "Field must have operand mode annotation: " + field;
                annotation.arrayOffsets.add(offset);
                EnumSet<OperandFlag> flags = getFlags(field);
                assert verifyFlags(field, type.getComponentType(), flags);
                annotation.flags.put(offset, getFlags(field));
            } else {
                assert getOperandModeAnnotation(field) == null : "Field must not have operand mode annotation: " + field;
                assert field.getAnnotation(LIRInstruction.State.class) == null : "Field must not have state annotation: " + field;
                dataOffsets.add(offset);
            }
        }

        private static boolean verifyFlags(Field field, Class<?> type, EnumSet<OperandFlag> flags) {
            if (flags.contains(REG)) {
                assert type.isAssignableFrom(REGISTER_VALUE_CLASS) || type.isAssignableFrom(VARIABLE_CLASS) : "Cannot assign RegisterValue / Variable to field with REG flag:" + field;
            }
            if (flags.contains(STACK)) {
                assert type.isAssignableFrom(STACK_SLOT_CLASS) : "Cannot assign StackSlot to field with STACK flag:" + field;
            }
            if (flags.contains(CONST)) {
                assert type.isAssignableFrom(CONSTANT_CLASS) : "Cannot assign Constant to field with CONST flag:" + field;
            }
            return true;
        }
    }

    protected static void forEach(LIRInstruction inst, Object obj, int directCount, long[] offsets, OperandMode mode, EnumSet<OperandFlag>[] flags, InstructionValueProcedure proc) {
        for (int i = 0; i < offsets.length; i++) {
            assert LIRInstruction.ALLOWED_FLAGS.get(mode).containsAll(flags[i]);

            if (i < directCount) {
                Value value = getValue(obj, offsets[i]);
                if (value instanceof CompositeValue) {
                    CompositeValue composite = (CompositeValue) value;
                    composite.forEachComponent(inst, mode, proc);
                } else {
                    setValue(obj, offsets[i], proc.doValue(inst, value, mode, flags[i]));
                }
            } else {
                Value[] values = getValueArray(obj, offsets[i]);
                for (int j = 0; j < values.length; j++) {
                    Value value = values[j];
                    if (value instanceof CompositeValue) {
                        CompositeValue composite = (CompositeValue) value;
                        composite.forEachComponent(inst, mode, proc);
                    } else {
                        values[j] = proc.doValue(inst, value, mode, flags[i]);
                    }
                }
            }
        }
    }

    protected static Value getValue(Object obj, long offset) {
        return (Value) unsafe.getObject(obj, offset);
    }

    protected static void setValue(Object obj, long offset, Value value) {
        unsafe.putObject(obj, offset, value);
    }

    protected static Value[] getValueArray(Object obj, long offset) {
        return (Value[]) unsafe.getObject(obj, offset);
    }

    protected void appendValues(StringBuilder result, Object obj, String start, String end, String startMultiple, String endMultiple, String[] prefix, long[]... moffsets) {
        int total = 0;
        for (long[] offsets : moffsets) {
            total += offsets.length;
        }
        if (total == 0) {
            return;
        }

        result.append(start);
        if (total > 1) {
            result.append(startMultiple);
        }
        String sep = "";
        for (int i = 0; i < moffsets.length; i++) {
            long[] offsets = moffsets[i];

            for (int j = 0; j < offsets.length; j++) {
                result.append(sep).append(prefix[i]);
                long offset = offsets[j];
                if (total > 1) {
                    result.append(fieldNames.get(offset)).append(": ");
                }
                result.append(getFieldString(obj, offset));
                sep = ", ";
            }
        }
        if (total > 1) {
            result.append(endMultiple);
        }
        result.append(end);
    }

    protected String getFieldString(Object obj, long offset) {
        Class<?> type = fieldTypes.get(offset);
        if (type == int.class) {
            return String.valueOf(unsafe.getInt(obj, offset));
        } else if (type == long.class) {
            return String.valueOf(unsafe.getLong(obj, offset));
        } else if (type == boolean.class) {
            return String.valueOf(unsafe.getBoolean(obj, offset));
        } else if (type == float.class) {
            return String.valueOf(unsafe.getFloat(obj, offset));
        } else if (type == double.class) {
            return String.valueOf(unsafe.getDouble(obj, offset));
        } else if (type == byte.class) {
            return String.valueOf(unsafe.getByte(obj, offset));
        } else if (!type.isPrimitive()) {
            Object value = unsafe.getObject(obj, offset);
            if (!type.isArray()) {
                return String.valueOf(value);
            } else if (type == int[].class) {
                return Arrays.toString((int[]) value);
            } else if (type == double[].class) {
                return Arrays.toString((double[]) value);
            } else if (type == byte[].class) {
                return Arrays.toString((byte[]) value);
            } else if (!type.getComponentType().isPrimitive()) {
                return Arrays.toString((Object[]) value);
            }
        }
        assert false : "unhandled field type: " + type;
        return "";
    }
}
