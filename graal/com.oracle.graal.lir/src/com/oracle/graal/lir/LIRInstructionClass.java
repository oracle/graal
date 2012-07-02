/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.api.code.ValueUtil.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.LIRInstruction.StateProcedure;
import com.oracle.graal.lir.LIRInstruction.ValueProcedure;

public class LIRInstructionClass {
    // TODO(cwimmer) factor out the common methods of this class and NodeClass into a base class.

    /**
     * Interface used by {@link LIRInstructionClass#rescanAllFieldOffsets(CalcOffset)} to determine the offset (in bytes) of a field.
     */
    public interface CalcOffset {
        long getOffset(Field field);
    }

    private static final Unsafe unsafe = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            // this will only fail if graal is not part of the boot class path
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
            // nothing to do
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            // currently we rely on being able to use Unsafe...
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    static class DefaultCalcOffset implements CalcOffset {
        @Override
        public long getOffset(Field field) {
            return unsafe.objectFieldOffset(field);
        }
    }

    private static final Class<?> INSTRUCTION_CLASS = LIRInstruction.class;
    private static final Class<?> VALUE_CLASS = Value.class;
    private static final Class<?> VALUE_ARRAY_CLASS = Value[].class;
    private static final Class<?> STATE_CLASS = LIRFrameState.class;


    private static final ConcurrentHashMap<Class< ? >, LIRInstructionClass> classes = new ConcurrentHashMap<>();

    private final Class< ? > clazz;
    private final int directUseCount;
    private final long[] useOffsets;
    private final EnumSet<OperandFlag>[] useFlags;
    private final int directAliveCount;
    private final long[] aliveOffsets;
    private final EnumSet<OperandFlag>[] aliveFlags;
    private final int directTempCount;
    private final long[] tempOffsets;
    private final EnumSet<OperandFlag>[] tempFlags;
    private final int directDefCount;
    private final long[] defOffsets;
    private final EnumSet<OperandFlag>[] defFlags;

    private final long[] stateOffsets;
    private final long[] dataOffsets;

    private final Map<Long, String> fieldNames;
    private final Map<Long, Class<?>> fieldTypes;

    private String opcodeConstant;
    private long opcodeOffset;

    @SuppressWarnings("unchecked")
    public LIRInstructionClass(Class<?> clazz) {
        assert INSTRUCTION_CLASS.isAssignableFrom(clazz);
        this.clazz = clazz;

        FieldScanner scanner = new FieldScanner(new DefaultCalcOffset());
        scanner.scan(clazz);

        OperandModeAnnotation mode = scanner.valueAnnotations.get(LIRInstruction.Use.class);
        directUseCount = mode.scalarOffsets.size();
        useOffsets = sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets);
        useFlags = arrayUsingSortedOffsets(mode.flags, useOffsets, new EnumSet[useOffsets.length]);

        mode = scanner.valueAnnotations.get(LIRInstruction.Alive.class);
        directAliveCount = mode.scalarOffsets.size();
        aliveOffsets = sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets);
        aliveFlags = arrayUsingSortedOffsets(mode.flags, aliveOffsets, new EnumSet[aliveOffsets.length]);

        mode = scanner.valueAnnotations.get(LIRInstruction.Temp.class);
        directTempCount = mode.scalarOffsets.size();
        tempOffsets = sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets);
        tempFlags = arrayUsingSortedOffsets(mode.flags, tempOffsets, new EnumSet[tempOffsets.length]);

        mode = scanner.valueAnnotations.get(LIRInstruction.Def.class);
        directDefCount = mode.scalarOffsets.size();
        defOffsets = sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets);
        defFlags = arrayUsingSortedOffsets(mode.flags, defOffsets, new EnumSet[defOffsets.length]);

        stateOffsets = sortedLongCopy(scanner.stateOffsets);
        dataOffsets = sortedLongCopy(scanner.dataOffsets);

        fieldNames = scanner.fieldNames;
        fieldTypes = scanner.fieldTypes;

        opcodeConstant = scanner.opcodeConstant;
        opcodeOffset = scanner.opcodeOffset;
    }

    public static void rescanAllFieldOffsets(CalcOffset calc) {
        for (LIRInstructionClass nodeClass : classes.values()) {
            nodeClass.rescanFieldOffsets(calc);
        }
    }

    private void rescanFieldOffsets(CalcOffset calc) {
        FieldScanner scanner = new FieldScanner(calc);
        scanner.scan(clazz);

        OperandModeAnnotation mode = scanner.valueAnnotations.get(LIRInstruction.Use.class);
        copyInto(useOffsets, sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets));
        mode = scanner.valueAnnotations.get(LIRInstruction.Alive.class);
        copyInto(aliveOffsets, sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets));
        mode = scanner.valueAnnotations.get(LIRInstruction.Temp.class);
        copyInto(tempOffsets, sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets));
        mode = scanner.valueAnnotations.get(LIRInstruction.Def.class);
        copyInto(defOffsets, sortedLongCopy(mode.scalarOffsets, mode.arrayOffsets));

        copyInto(stateOffsets, sortedLongCopy(scanner.stateOffsets));
        copyInto(dataOffsets, sortedLongCopy(scanner.dataOffsets));

        fieldNames.clear();
        fieldNames.putAll(scanner.fieldNames);
        fieldTypes.clear();
        fieldTypes.putAll(scanner.fieldTypes);

        opcodeConstant = scanner.opcodeConstant;
        opcodeOffset = scanner.opcodeOffset;
    }

    private static void copyInto(long[] dest, long[] src) {
        assert dest.length == src.length;
        for (int i = 0; i < dest.length; i++) {
            dest[i] = src[i];
        }
    }

    public static final LIRInstructionClass get(Class<?> c) {
        LIRInstructionClass clazz = classes.get(c);
        if (clazz != null) {
            return clazz;
        }

        // We can have a race of multiple threads creating the LIRInstructionClass at the same time.
        // However, only one will be put into the map, and this is the one returned by all threads.
        clazz = new LIRInstructionClass(c);
        LIRInstructionClass oldClazz = classes.putIfAbsent(c, clazz);
        if (oldClazz != null) {
            return oldClazz;
        } else {
            return clazz;
        }
    }


    private static class OperandModeAnnotation {
        public final ArrayList<Long> scalarOffsets = new ArrayList<>();
        public final ArrayList<Long> arrayOffsets = new ArrayList<>();
        public final Map<Long, EnumSet<OperandFlag>> flags = new HashMap<>();
    }

    private static class FieldScanner {
        public final CalcOffset calc;

        public final Map<Class<? extends Annotation>, OperandModeAnnotation> valueAnnotations;
        public final ArrayList<Long> stateOffsets = new ArrayList<>();
        public final ArrayList<Long> dataOffsets = new ArrayList<>();

        public final Map<Long, String> fieldNames = new HashMap<>();
        public final Map<Long, Class<?>> fieldTypes = new HashMap<>();

        private String opcodeConstant;
        private long opcodeOffset;

        public FieldScanner(CalcOffset calc) {
            this.calc = calc;

            valueAnnotations = new HashMap<>();
            valueAnnotations.put(LIRInstruction.Use.class, new OperandModeAnnotation()); //LIRInstruction.Use.class));
            valueAnnotations.put(LIRInstruction.Alive.class, new OperandModeAnnotation()); //LIRInstruction.Alive.class));
            valueAnnotations.put(LIRInstruction.Temp.class, new OperandModeAnnotation()); //LIRInstruction.Temp.class));
            valueAnnotations.put(LIRInstruction.Def.class, new OperandModeAnnotation()); //LIRInstruction.Def.class));
        }

        private OperandModeAnnotation getOperandModeAnnotation(Field field) {
            OperandModeAnnotation result = null;
            for (Entry<Class<? extends Annotation>, OperandModeAnnotation> entry  : valueAnnotations.entrySet()) {
                Annotation annotation = field.getAnnotation(entry.getKey());
                if (annotation != null) {
                    assert result == null : "Field has two operand mode annotations: " + field;
                    result = entry.getValue();
                }
            }
            return result;
        }

        private static EnumSet<OperandFlag> getFlags(Field field) {
            EnumSet<OperandFlag> result = EnumSet.noneOf(OperandFlag.class);
            // Unfortunately, annotations cannot have class hierarchies or implement interfaces, so we have to duplicate the code for every operand mode.
            // Unfortunately, annotations cannot have an EnumSet property, so we have to convert from arrays to EnumSet manually.
            if (field.isAnnotationPresent(LIRInstruction.Use.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Use.class).value()));
            } else if (field.isAnnotationPresent(LIRInstruction.Alive.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Alive.class).value()));
            } else if (field.isAnnotationPresent(LIRInstruction.Temp.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Temp.class).value()));
            } else if (field.isAnnotationPresent(LIRInstruction.Def.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Def.class).value()));
            } else {
                GraalInternalError.shouldNotReachHere();
            }
            return result;
        }

        public void scan(Class<?> clazz) {
            if (clazz.getAnnotation(LIRInstruction.Opcode.class) != null) {
                opcodeConstant = clazz.getAnnotation(LIRInstruction.Opcode.class).value();
            }
            opcodeOffset = -1;

            Class<?> currentClazz = clazz;
            do {
                for (Field field : currentClazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    Class< ? > type = field.getType();
                    long offset = calc.getOffset(field);

                    if (VALUE_CLASS.isAssignableFrom(type)) {
                        assert Modifier.isProtected(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()) : "Value field must not be declared final or private because it is modified by register allocator: " + field;
                        OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                        assert annotation != null : "Field must have operand mode annotation: " + field;
                        annotation.scalarOffsets.add(offset);
                        annotation.flags.put(offset, getFlags(field));
                    } else if (VALUE_ARRAY_CLASS.isAssignableFrom(type)) {
                        OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                        assert annotation != null : "Field must have operand mode annotation: " + field;
                        annotation.arrayOffsets.add(offset);
                        annotation.flags.put(offset, getFlags(field));
                    } else if (STATE_CLASS.isAssignableFrom(type)) {
                        assert getOperandModeAnnotation(field) == null : "Field must not have operand mode annotation: " + field;
                        assert field.getAnnotation(LIRInstruction.State.class) != null : "Field must have state annotation: " + field;
                        stateOffsets.add(offset);
                    } else {
                        assert getOperandModeAnnotation(field) == null : "Field must not have operand mode annotation: " + field;
                        assert field.getAnnotation(LIRInstruction.State.class) == null : "Field must not have state annotation: " + field;
                        dataOffsets.add(offset);
                    }
                    fieldNames.put(offset, field.getName());
                    fieldTypes.put(offset, type);

                    if (field.getAnnotation(LIRInstruction.Opcode.class) != null) {
                        assert opcodeConstant == null && opcodeOffset == -1 : "Can have only one Opcode definition: " + clazz;
                        opcodeOffset = offset;
                    }
                }
                currentClazz = currentClazz.getSuperclass();
            } while (currentClazz != LIRInstruction.class);

            if (opcodeConstant == null && opcodeOffset == -1) {
                opcodeConstant = clazz.getSimpleName();
                if (opcodeConstant.endsWith("Op")) {
                    opcodeConstant = opcodeConstant.substring(0, opcodeConstant.length() - 2);
                }
            }
        }
    }

    private static <T> T[] arrayUsingSortedOffsets(Map<Long, T> map, long[] sortedOffsets, T[] result) {
        for (int i = 0; i < sortedOffsets.length; i++) {
            result[i] = map.get(sortedOffsets[i]);
        }
        return result;
    }

    private static long[] sortedLongCopy(ArrayList<Long> list1) {
        Collections.sort(list1);
        long[] result = new long[list1.size()];
        for (int i = 0; i < list1.size(); i++) {
            result[i] = list1.get(i);
        }
        return result;
    }

    private static long[] sortedLongCopy(ArrayList<Long> list1, ArrayList<Long> list2) {
        Collections.sort(list1);
        Collections.sort(list2);
        long[] result = new long[list1.size() + list2.size()];
        for (int i = 0; i < list1.size(); i++) {
            result[i] = list1.get(i);
        }
        for (int i = 0; i < list2.size(); i++) {
            result[list1.size() + i] = list2.get(i);
        }
        return result;
    }


    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(getClass().getSimpleName()).append(" ").append(clazz.getSimpleName()).append(" use[");
        for (int i = 0; i < useOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(useOffsets[i]);
        }
        str.append("] alive[");
        for (int i = 0; i < aliveOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(aliveOffsets[i]);
        }
        str.append("] temp[");
        for (int i = 0; i < tempOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(tempOffsets[i]);
        }
        str.append("] def[");
        for (int i = 0; i < defOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(defOffsets[i]);
        }
        str.append("] state[");
        for (int i = 0; i < stateOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(stateOffsets[i]);
        }
        str.append("] data[");
        for (int i = 0; i < dataOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(dataOffsets[i]);
        }
        str.append("]");
        return str.toString();
    }


    public final String getOpcode(LIRInstruction obj) {
        if (opcodeConstant != null) {
            return opcodeConstant;
        }
        assert opcodeOffset != -1;
        return unsafe.getObject(obj, opcodeOffset).toString();
    }

    public final boolean hasOperands() {
        return useOffsets.length > 0 || aliveOffsets.length > 0 || tempOffsets.length > 0 || defOffsets.length > 0;
    }

    public final boolean hasState(LIRInstruction obj) {
        for (int i = 0; i < stateOffsets.length; i++) {
            if (getState(obj, stateOffsets[i]) != null) {
                return true;
            }
        }
        return false;
    }

    public final void forEachUse(LIRInstruction obj, ValueProcedure proc) {
        forEach(obj, directUseCount, useOffsets, OperandMode.USE, useFlags, proc);
    }

    public final void forEachAlive(LIRInstruction obj, ValueProcedure proc) {
        forEach(obj, directAliveCount, aliveOffsets, OperandMode.ALIVE, aliveFlags, proc);
    }

    public final void forEachTemp(LIRInstruction obj, ValueProcedure proc) {
        forEach(obj, directTempCount, tempOffsets, OperandMode.TEMP, tempFlags, proc);
    }

    public final void forEachDef(LIRInstruction obj, ValueProcedure proc) {
        forEach(obj, directDefCount, defOffsets, OperandMode.DEF, defFlags, proc);
    }

    public final void forEachState(LIRInstruction obj, ValueProcedure proc) {
        for (int i = 0; i < stateOffsets.length; i++) {
            LIRFrameState state = getState(obj, stateOffsets[i]);
            if (state != null) {
                state.forEachState(proc);
            }
        }
    }

    public final void forEachState(LIRInstruction obj, StateProcedure proc) {
        for (int i = 0; i < stateOffsets.length; i++) {
            LIRFrameState state = getState(obj, stateOffsets[i]);
            if (state != null) {
                proc.doState(state);
            }
        }
    }

    private static void forEach(LIRInstruction obj, int directCount, long[] offsets, OperandMode mode, EnumSet<OperandFlag>[] flags, ValueProcedure proc) {
        for (int i = 0; i < offsets.length; i++) {
            assert LIRInstruction.ALLOWED_FLAGS.get(mode).containsAll(flags[i]);

            if (i < directCount) {
                Value value = getValue(obj, offsets[i]);
                if (isAddress(value)) {
                    doAddress(asAddress(value), mode, flags[i], proc);
                } else {
                    setValue(obj, offsets[i], proc.doValue(value, mode, flags[i]));
                }
            } else {
                Value[] values = getValueArray(obj, offsets[i]);
                for (int j = 0; j < values.length; j++) {
                    Value value = values[j];
                    if (isAddress(value)) {
                        doAddress(asAddress(value), mode, flags[i], proc);
                    } else {
                        values[j] = proc.doValue(value, mode, flags[i]);
                    }
                }
            }
        }
    }

    private static void doAddress(Address address, OperandMode mode, EnumSet<OperandFlag> flags, ValueProcedure proc) {
        assert flags.contains(OperandFlag.ADDR);
        address.setBase(proc.doValue(address.getBase(), mode, LIRInstruction.ADDRESS_FLAGS));
        address.setIndex(proc.doValue(address.getIndex(), mode, LIRInstruction.ADDRESS_FLAGS));
    }

    public final Value forEachRegisterHint(LIRInstruction obj, OperandMode mode, ValueProcedure proc) {
        int hintDirectCount = 0;
        long[] hintOffsets = null;
        if (mode == OperandMode.USE) {
            hintDirectCount = directDefCount;
            hintOffsets = defOffsets;
        } else if (mode == OperandMode.DEF) {
            hintDirectCount = directUseCount;
            hintOffsets = useOffsets;
        } else {
            return null;
        }

        for (int i = 0; i < hintOffsets.length; i++) {
            if (i < hintDirectCount) {
                Value hintValue = getValue(obj, hintOffsets[i]);
                Value result = proc.doValue(hintValue, null, null);
                if (result != null) {
                    return result;
                }
            } else {
                Value[] hintValues = getValueArray(obj, hintOffsets[i]);
                for (int j = 0; j < hintValues.length; j++) {
                    Value hintValue = hintValues[j];
                    Value result = proc.doValue(hintValue, null, null);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private static Value getValue(LIRInstruction obj, long offset) {
        return (Value) unsafe.getObject(obj, offset);
    }

    private static void setValue(LIRInstruction obj, long offset, Value value) {
        unsafe.putObject(obj, offset, value);
    }

    private static Value[] getValueArray(LIRInstruction obj, long offset) {
        return (Value[]) unsafe.getObject(obj, offset);
    }

    private static LIRFrameState getState(LIRInstruction obj, long offset) {
        return (LIRFrameState) unsafe.getObject(obj, offset);
    }


    public String toString(LIRInstruction obj) {
        StringBuilder result = new StringBuilder();

        appendValues(result, obj, "", " = ", "(", ")", new String[] {""}, defOffsets);
        result.append(getOpcode(obj).toUpperCase());
        appendValues(result, obj, " ", "", "(", ")", new String[] {"", "~"}, useOffsets, aliveOffsets);
        appendValues(result, obj, " ", "", "{", "}", new String[] {""}, tempOffsets);

        for (int i = 0; i < dataOffsets.length; i++) {
            if (dataOffsets[i] == opcodeOffset) {
                continue;
            }
            result.append(" ").append(fieldNames.get(dataOffsets[i])).append(": ").append(getFieldString(obj, dataOffsets[i]));
        }

        for (int i = 0; i < stateOffsets.length; i++) {
            LIRFrameState state = getState(obj, stateOffsets[i]);
            if (state != null) {
                result.append(" ").append(fieldNames.get(stateOffsets[i])).append(" [bci:");
                String sep = "";
                for (BytecodeFrame cur = state.topFrame; cur != null; cur = cur.caller()) {
                    result.append(sep).append(cur.getBCI());
                    sep = ", ";
                }
                result.append("]");
            }
        }

        return result.toString();
    }

    private void appendValues(StringBuilder result, LIRInstruction obj, String start, String end, String startMultiple, String endMultiple, String[] prefix, long[]...moffsets) {
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

    private String getFieldString(Object obj, long offset) {
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
        } else if (!type.isPrimitive()) {
            Object value = unsafe.getObject(obj, offset);
            if (!type.isArray()) {
                return String.valueOf(value);
            } else if (type == int[].class) {
                return Arrays.toString((int[]) value);
            } else if (type == double[].class) {
                return Arrays.toString((double[]) value);
            } else if (!type.getComponentType().isPrimitive()) {
                return Arrays.toString((Object[]) value);
            }
        }
        assert false : "unhandled field type: " + type;
        return "";
    }
}
