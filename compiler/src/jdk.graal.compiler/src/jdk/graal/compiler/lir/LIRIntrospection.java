/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.FieldIntrospection;
import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.FieldsScanner;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

abstract class LIRIntrospection<T> extends FieldIntrospection<T> {

    private static final Class<Value> VALUE_CLASS = Value.class;
    private static final Class<ConstantValue> CONSTANT_VALUE_CLASS = ConstantValue.class;
    private static final Class<Variable> VARIABLE_CLASS = Variable.class;
    private static final Class<RegisterValue> REGISTER_VALUE_CLASS = RegisterValue.class;
    private static final Class<StackSlot> STACK_SLOT_CLASS = StackSlot.class;
    private static final Class<Value[]> VALUE_ARRAY_CLASS = Value[].class;

    LIRIntrospection(Class<T> clazz) {
        super(clazz);
    }

    protected static final class Values extends Fields {
        private final int directCount;
        private final EnumSet<OperandFlag>[] flags;

        private static final Values EMPTY_VALUES = new Values(0, Collections.emptyList());

        @SuppressWarnings({"unchecked"})
        private Values(int directCount, List<ValueFieldInfo> fields) {
            super(fields);
            this.directCount = directCount;
            flags = (EnumSet<OperandFlag>[]) new EnumSet<?>[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                flags[i] = fields.get(i).flags;
            }
        }

        public static Values create(OperandModeAnnotation mode) {
            if (mode.directValues.isEmpty() && mode.indirectValues.isEmpty()) {
                return EMPTY_VALUES;
            }
            List<ValueFieldInfo> fields = new ArrayList<>(mode.directValues.size() + mode.indirectValues.size());
            fields.addAll(mode.directValues);
            fields.addAll(mode.indirectValues);
            return new Values(mode.directValues.size(), fields);
        }

        public int getDirectCount() {
            return directCount;
        }

        public EnumSet<OperandFlag> getFlags(int i) {
            return flags[i];
        }

        Value getValue(Object obj, int index) {
            return (Value) getObject(obj, index);
        }

        private void setValue(Object obj, int index, Value value) {
            putObjectChecked(obj, index, value);
        }

        Value[] getValueArray(Object obj, int index) {
            return (Value[]) getObject(obj, index);
        }
    }

    /**
     * The component values in an {@link LIRInstruction} or {@link CompositeValue}.
     */
    protected Values values;

    protected static class ValueFieldInfo extends FieldsScanner.FieldInfo {

        final EnumSet<OperandFlag> flags;

        public ValueFieldInfo(long offset, String name, Class<?> type, Class<?> declaringClass, EnumSet<OperandFlag> flags) {
            super(offset, name, type, declaringClass);
            GraalError.guarantee(VALUE_ARRAY_CLASS.isAssignableFrom(type) || VALUE_CLASS.isAssignableFrom(type), "%s", type);
            this.flags = flags;
        }

        @Override
        public String toString() {
            return super.toString() + flags;
        }
    }

    protected static class OperandModeAnnotation {

        /**
         * Fields of type {@link LIRIntrospection#VALUE_CLASS}.
         */
        public final List<ValueFieldInfo> directValues = new ArrayList<>();

        /**
         * Fields of type {@link LIRIntrospection#VALUE_ARRAY_CLASS}.
         */
        public final List<ValueFieldInfo> indirectValues = new ArrayList<>();
    }

    protected abstract static class LIRFieldsScanner extends FieldsScanner {

        public final EconomicMap<Class<? extends Annotation>, OperandModeAnnotation> valueAnnotations;
        public final ArrayList<FieldsScanner.FieldInfo> states = new ArrayList<>();

        public LIRFieldsScanner() {
            valueAnnotations = EconomicMap.create(Equivalence.DEFAULT);
        }

        protected OperandModeAnnotation getOperandModeAnnotation(Field field) {
            OperandModeAnnotation result = null;
            MapCursor<Class<? extends Annotation>, OperandModeAnnotation> cursor = valueAnnotations.getEntries();
            while (cursor.advance()) {
                Annotation annotation = field.getAnnotation(cursor.getKey());
                if (annotation != null) {
                    GraalError.guarantee(result == null, "Field has two operand mode annotations: %s", field);
                    result = cursor.getValue();
                }
            }
            return result;
        }

        protected abstract EnumSet<OperandFlag> getFlags(Field field);

        @Override
        protected void scanField(Field field, long offset) {
            Class<?> type = field.getType();
            if (VALUE_CLASS.isAssignableFrom(type) && !CONSTANT_VALUE_CLASS.isAssignableFrom(type)) {
                GraalError.guarantee(!Modifier.isFinal(field.getModifiers()), "Value field must not be declared final because it is modified by register allocator: %s", field);
                OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                GraalError.guarantee(annotation != null, "Field must have operand mode annotation: %s", field);
                EnumSet<OperandFlag> flags = getFlags(field);
                verifyFlags(field, type, flags);
                annotation.directValues.add(new ValueFieldInfo(offset, field.getName(), type, field.getDeclaringClass(), flags));
            } else if (VALUE_ARRAY_CLASS.isAssignableFrom(type)) {
                OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                GraalError.guarantee(annotation != null, "Field must have operand mode annotation: %s", field);
                EnumSet<OperandFlag> flags = getFlags(field);
                verifyFlags(field, type.getComponentType(), flags);
                annotation.indirectValues.add(new ValueFieldInfo(offset, field.getName(), type, field.getDeclaringClass(), flags));
            } else {
                GraalError.guarantee(getOperandModeAnnotation(field) == null, "Field must not have operand mode annotation: %s", field);
                GraalError.guarantee(field.getAnnotation(LIRInstruction.State.class) == null, "Field must not have state annotation: %s", field);
                super.scanField(field, offset);
            }
        }

        private static void verifyFlags(Field field, Class<?> type, EnumSet<OperandFlag> flags) {
            GraalError.guarantee(!flags.contains(REG) || type.isAssignableFrom(REGISTER_VALUE_CLASS) || type.isAssignableFrom(VARIABLE_CLASS),
                            "Cannot assign RegisterValue / Variable to field with REG flag: %s", field);
            GraalError.guarantee(!flags.contains(STACK) || type.isAssignableFrom(STACK_SLOT_CLASS), "Cannot assign StackSlot to field with STACK flag: %s", field);
            GraalError.guarantee(!flags.contains(CONST) || type.isAssignableFrom(CONSTANT_VALUE_CLASS), "Cannot assign Constant to field with CONST flag: %s", field);
        }
    }

    /**
     * This method is called very frequently during linear scan so calling it from non-assertion
     * code should only be done after careful performance evaluation.
     */
    private static boolean verifyAssignment(LIRInstruction inst, Value newValue, EnumSet<OperandFlag> flags) {
        assert flags.contains(REG) || !(newValue instanceof RegisterValue) && !LIRValueUtil.isVariable(newValue) : err(inst, newValue, REG);
        assert flags.contains(STACK) || !(newValue instanceof StackSlot) : err(inst, newValue, STACK);
        assert flags.contains(CONST) || !(newValue instanceof ConstantValue) : err(inst, newValue, CONST);
        return true;
    }

    private static String err(LIRInstruction inst, Value value, OperandFlag flag) {
        return "Cannot assign " + value.getClass().getSimpleName() + "(" + value + ") to field without " + flag + " flag: " + inst;
    }

    protected static void forEach(LIRInstruction inst, Values values, OperandMode mode, InstructionValueProcedure proc) {
        for (int i = 0; i < values.getCount(); i++) {
            assert LIRInstruction.ALLOWED_FLAGS.get(mode).containsAll(values.getFlags(i));

            if (i < values.getDirectCount()) {
                Value value = values.getValue(inst, i);
                Value newValue;
                if (value instanceof CompositeValue composite) {
                    newValue = composite.forEachComponent(inst, mode, proc);
                } else {
                    newValue = proc.doValue(inst, value, mode, values.getFlags(i));
                }
                if (!value.identityEquals(newValue)) {
                    assert value instanceof CompositeValue || verifyAssignment(inst, newValue, values.getFlags(i));
                    GraalError.guarantee(newValue.getPlatformKind().equals(value.getPlatformKind()), "New assignment changes PlatformKind");
                    values.setValue(inst, i, newValue);
                }
            } else {
                Value[] valueArray = values.getValueArray(inst, i);
                for (int j = 0; j < valueArray.length; j++) {
                    Value value = valueArray[j];
                    Value newValue;
                    if (value instanceof CompositeValue composite) {
                        newValue = composite.forEachComponent(inst, mode, proc);
                    } else {
                        newValue = proc.doValue(inst, value, mode, values.getFlags(i));
                    }
                    if (!value.identityEquals(newValue)) {
                        GraalError.guarantee(newValue.getPlatformKind().equals(value.getPlatformKind()), "New assignment changes PlatformKind");
                        valueArray[j] = newValue;
                    }
                }
            }
        }
    }

    protected static void visitEach(LIRInstruction inst, Values values, OperandMode mode, InstructionValueConsumer proc) {
        for (int i = 0; i < values.getCount(); i++) {
            assert LIRInstruction.ALLOWED_FLAGS.get(mode).containsAll(values.getFlags(i));

            if (i < values.getDirectCount()) {
                Value value = values.getValue(inst, i);
                if (value instanceof CompositeValue composite) {
                    composite.visitEachComponent(inst, mode, proc);
                } else {
                    proc.visitValue(inst, value, mode, values.getFlags(i));
                }
            } else {
                Value[] valueArray = values.getValueArray(inst, i);
                for (Value value : valueArray) {
                    if (value instanceof CompositeValue composite) {
                        composite.visitEachComponent(inst, mode, proc);
                    } else {
                        proc.visitValue(inst, value, mode, values.getFlags(i));
                    }
                }
            }
        }
    }

    protected static void appendValues(StringBuilder sb, Object obj, String start, String end, String startMultiple, String endMultiple, boolean elideSingleName, String[] prefix,
                    Fields... fieldsList) {
        int total = 0;
        for (Fields fields : fieldsList) {
            total += fields.getCount();
        }
        if (total == 0) {
            return;
        }

        sb.append(start);
        if (total > 1) {
            sb.append(startMultiple);
        }
        String sep = "";
        int i = 0;
        for (Fields fields : fieldsList) {
            for (int j = 0; j < fields.getCount(); j++) {
                sb.append(sep).append(prefix[i]);
                if (!elideSingleName || total > 1) {
                    sb.append(fields.getName(j)).append(": ");
                }
                sb.append(getFieldString(obj, j, fields));
                sep = ", ";
            }
            i++;
        }
        if (total > 1) {
            sb.append(endMultiple);
        }
        sb.append(end);
    }

    protected static String getFieldString(Object obj, int index, Fields fields) {
        Object value = fields.get(obj, index);
        Class<?> type = fields.getType(index);
        if (value == null || type.isPrimitive() || !type.isArray()) {
            return String.valueOf(value);
        }
        if (type == int[].class) {
            return Arrays.toString((int[]) value);
        } else if (type == double[].class) {
            return Arrays.toString((double[]) value);
        } else if (type == byte[].class) {
            byte[] byteValue = (byte[]) value;
            if (isPrintableAsciiString(byteValue)) {
                return toString(byteValue);
            } else {
                return Arrays.toString(byteValue);
            }
        } else if (!type.getComponentType().isPrimitive()) {
            return Arrays.toString((Object[]) value);
        }
        throw new GraalError("unhandled field type: %s", type);
    }

    /**
     * Tests if all values in {@code array} are printable ASCII characters or value \0 (b in
     * [0x20,0x7F]) or b == 0.
     *
     * @return true if there are only printable ASCII characters and \0, false otherwise
     */
    private static boolean isPrintableAsciiString(byte[] array) {
        for (byte b : array) {
            char c = (char) b;
            if (c != 0 && (c < 0x20 || c > 0x7F)) {
                return false;
            }
        }
        return true;
    }

    private static String toString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (byte b : bytes) {
            if (b == 0) {
                sb.append("\\0");
            } else if (b == '"') {
                sb.append("\\\"");
            } else if (b == '\n') {
                sb.append("\\n");
            } else {
                sb.append((char) b);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
