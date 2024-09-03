/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;

import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.FieldsScanner;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.graal.compiler.lir.StandardOp.LoadConstantOp;
import jdk.graal.compiler.lir.StandardOp.MoveOp;
import jdk.graal.compiler.lir.StandardOp.ValueMoveOp;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Value;

public class LIRInstructionClass<T> extends LIRIntrospection<T> {

    public static <T extends LIRInstruction> LIRInstructionClass<T> create(Class<T> c) {
        return new LIRInstructionClass<>(c);
    }

    private static final Class<LIRInstruction> INSTRUCTION_CLASS = LIRInstruction.class;
    private static final Class<LIRFrameState> STATE_CLASS = LIRFrameState.class;

    private final Values uses;
    private final Values alives;
    private final Values temps;
    private final Values defs;
    private final Fields states;

    private final boolean isMoveOp;
    private final boolean isValueMoveOp;
    private final boolean isLoadConstantOp;

    private String opcodeConstant;
    private int opcodeIndex;

    private LIRInstructionClass(Class<T> clazz) {
        this(clazz, new FieldsScanner.DefaultCalcOffset());
    }

    public LIRInstructionClass(Class<T> clazz, FieldsScanner.CalcOffset calcOffset) {
        super(clazz);
        assert INSTRUCTION_CLASS.isAssignableFrom(clazz);

        LIRInstructionFieldsScanner ifs = new LIRInstructionFieldsScanner(calcOffset);
        ifs.scan(clazz);

        uses = Values.create(ifs.valueAnnotations.get(LIRInstruction.Use.class));
        alives = Values.create(ifs.valueAnnotations.get(LIRInstruction.Alive.class));
        temps = Values.create(ifs.valueAnnotations.get(LIRInstruction.Temp.class));
        defs = Values.create(ifs.valueAnnotations.get(LIRInstruction.Def.class));

        states = Fields.create(ifs.states);
        data = Fields.create(ifs.data);

        opcodeConstant = ifs.opcodeConstant;
        if (ifs.opcodeField == null) {
            opcodeIndex = -1;
        } else {
            opcodeIndex = ifs.data.indexOf(ifs.opcodeField);
        }

        isMoveOp = MoveOp.class.isAssignableFrom(clazz);
        isValueMoveOp = ValueMoveOp.class.isAssignableFrom(clazz);
        isLoadConstantOp = LoadConstantOp.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> LIRInstructionClass<T> get(Class<T> clazz) {
        try {
            Field field = clazz.getDeclaredField("TYPE");
            field.setAccessible(true);
            LIRInstructionClass<T> result = (LIRInstructionClass<T>) field.get(null);
            if (result == null) {
                throw GraalError.shouldNotReachHere("TYPE field not initialized for class " + clazz.getTypeName()); // ExcludeFromJacocoGeneratedReport
            }
            return result;
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static class LIRInstructionFieldsScanner extends LIRFieldsScanner {

        private String opcodeConstant;

        /**
         * Field (if any) annotated by {@link Opcode}.
         */
        private FieldsScanner.FieldInfo opcodeField;

        LIRInstructionFieldsScanner(FieldsScanner.CalcOffset calc) {
            super(calc);

            valueAnnotations.put(LIRInstruction.Use.class, new OperandModeAnnotation());
            valueAnnotations.put(LIRInstruction.Alive.class, new OperandModeAnnotation());
            valueAnnotations.put(LIRInstruction.Temp.class, new OperandModeAnnotation());
            valueAnnotations.put(LIRInstruction.Def.class, new OperandModeAnnotation());
        }

        @Override
        protected EnumSet<OperandFlag> getFlags(Field field) {
            EnumSet<OperandFlag> result = EnumSet.noneOf(OperandFlag.class);
            // Unfortunately, annotations cannot have class hierarchies or implement interfaces, so
            // we have to duplicate the code for every operand mode.
            // Unfortunately, annotations cannot have an EnumSet property, so we have to convert
            // from arrays to EnumSet manually.
            if (field.isAnnotationPresent(LIRInstruction.Use.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Use.class).value()));
            } else if (field.isAnnotationPresent(LIRInstruction.Alive.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Alive.class).value()));
            } else if (field.isAnnotationPresent(LIRInstruction.Temp.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Temp.class).value()));
            } else if (field.isAnnotationPresent(LIRInstruction.Def.class)) {
                result.addAll(Arrays.asList(field.getAnnotation(LIRInstruction.Def.class).value()));
            } else {
                GraalError.shouldNotReachHereUnexpectedValue(field); // ExcludeFromJacocoGeneratedReport
            }
            return result;
        }

        public void scan(Class<?> clazz) {
            if (clazz.getAnnotation(Opcode.class) != null) {
                opcodeConstant = clazz.getAnnotation(Opcode.class).value();
            }
            opcodeField = null;

            super.scan(clazz, LIRInstruction.class, false);

            if (opcodeConstant == null && opcodeField == null) {
                opcodeConstant = clazz.getSimpleName();
                if (opcodeConstant.endsWith("Op")) {
                    opcodeConstant = opcodeConstant.substring(0, opcodeConstant.length() - 2);
                }
            }
        }

        @Override
        protected void scanField(Field field, long offset) {
            Class<?> type = field.getType();
            if (STATE_CLASS.isAssignableFrom(type)) {
                assert getOperandModeAnnotation(field) == null : "Field must not have operand mode annotation: " + field;
                assert field.getAnnotation(LIRInstruction.State.class) != null : "Field must have state annotation: " + field;
                states.add(new FieldsScanner.FieldInfo(offset, field.getName(), type, field.getDeclaringClass()));
            } else {
                super.scanField(field, offset);
            }

            if (field.getAnnotation(Opcode.class) != null) {
                assert opcodeConstant == null && opcodeField == null : "Can have only one Opcode definition: " + type;
                assert data.get(data.size() - 1).offset == offset : Assertions.errorMessage(data.get(data.size() - 1).offset, offset);
                opcodeField = data.get(data.size() - 1);
            }
        }
    }

    @Override
    public Fields[] getAllFields() {
        assert values == null;
        return new Fields[]{data, uses, alives, temps, defs, states};
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(getClass().getSimpleName()).append(" ").append(getClazz().getSimpleName()).append(" use[");
        uses.appendFields(str);
        str.append("] alive[");
        alives.appendFields(str);
        str.append("] temp[");
        temps.appendFields(str);
        str.append("] def[");
        defs.appendFields(str);
        str.append("] state[");
        states.appendFields(str);
        str.append("] data[");
        data.appendFields(str);
        str.append("]");
        return str.toString();
    }

    final String getOpcode(LIRInstruction obj) {
        if (opcodeConstant != null) {
            return opcodeConstant;
        }
        assert opcodeIndex != -1;
        return String.valueOf(data.getObject(obj, opcodeIndex));
    }

    final boolean hasOperands() {
        return uses.getCount() > 0 || alives.getCount() > 0 || temps.getCount() > 0 || defs.getCount() > 0;
    }

    final boolean hasState(LIRInstruction obj) {
        for (int i = 0; i < states.getCount(); i++) {
            if (states.getObject(obj, i) != null) {
                return true;
            }
        }
        return false;
    }

    final void forEachUse(LIRInstruction obj, InstructionValueProcedure proc) {
        forEach(obj, uses, OperandMode.USE, proc);
    }

    final void forEachAlive(LIRInstruction obj, InstructionValueProcedure proc) {
        forEach(obj, alives, OperandMode.ALIVE, proc);
    }

    final void forEachTemp(LIRInstruction obj, InstructionValueProcedure proc) {
        forEach(obj, temps, OperandMode.TEMP, proc);
    }

    final void forEachDef(LIRInstruction obj, InstructionValueProcedure proc) {
        forEach(obj, defs, OperandMode.DEF, proc);
    }

    final void visitEachUse(LIRInstruction obj, InstructionValueConsumer proc) {
        visitEach(obj, uses, OperandMode.USE, proc);
    }

    final void visitEachAlive(LIRInstruction obj, InstructionValueConsumer proc) {
        visitEach(obj, alives, OperandMode.ALIVE, proc);
    }

    final void visitEachTemp(LIRInstruction obj, InstructionValueConsumer proc) {
        visitEach(obj, temps, OperandMode.TEMP, proc);
    }

    final void visitEachDef(LIRInstruction obj, InstructionValueConsumer proc) {
        visitEach(obj, defs, OperandMode.DEF, proc);
    }

    final void forEachState(LIRInstruction obj, InstructionValueProcedure proc) {
        for (int i = 0; i < states.getCount(); i++) {
            LIRFrameState state = (LIRFrameState) states.getObject(obj, i);
            if (state != null) {
                state.forEachState(obj, proc);
            }
        }
    }

    final void visitEachState(LIRInstruction obj, InstructionValueConsumer proc) {
        for (int i = 0; i < states.getCount(); i++) {
            LIRFrameState state = (LIRFrameState) states.getObject(obj, i);
            if (state != null) {
                state.visitEachState(obj, proc);
            }
        }
    }

    final void forEachState(LIRInstruction obj, InstructionStateProcedure proc) {
        for (int i = 0; i < states.getCount(); i++) {
            LIRFrameState state = (LIRFrameState) states.getObject(obj, i);
            if (state != null) {
                proc.doState(obj, state);
            }
        }
    }

    final Value forEachRegisterHint(LIRInstruction obj, OperandMode mode, InstructionValueProcedure proc) {
        Values hints;
        if (mode == OperandMode.USE) {
            hints = defs;
        } else if (mode == OperandMode.DEF) {
            hints = uses;
        } else {
            return null;
        }

        for (int i = 0; i < hints.getCount(); i++) {
            if (i < hints.getDirectCount()) {
                Value hintValue = hints.getValue(obj, i);
                Value result = proc.doValue(obj, hintValue, null, null);
                if (result != null) {
                    return result;
                }
            } else {
                Value[] hintValues = hints.getValueArray(obj, i);
                for (int j = 0; j < hintValues.length; j++) {
                    Value hintValue = hintValues[j];
                    Value result = proc.doValue(obj, hintValue, null, null);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    String toString(LIRInstruction obj) {
        StringBuilder result = new StringBuilder();

        appendValues(result, obj, "", " = ", "(", ")", true, new String[]{""}, defs);
        result.append(String.valueOf(getOpcode(obj)).toUpperCase(Locale.ROOT));
        appendValues(result, obj, " ", "", "(", ")", false, new String[]{"", "~"}, uses, alives);
        appendValues(result, obj, " ", "", "{", "}", false, new String[]{""}, temps);

        for (int i = 0; i < data.getCount(); i++) {
            if (i == opcodeIndex) {
                continue;
            }
            result.append(" ").append(data.getName(i)).append(": ").append(getFieldString(obj, i, data));
        }

        for (int i = 0; i < states.getCount(); i++) {
            LIRFrameState state = (LIRFrameState) states.getObject(obj, i);
            if (state != null) {
                result.append(" ").append(states.getName(i)).append(" [bci:");
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

    final boolean isMoveOp() {
        return isMoveOp;
    }

    final boolean isValueMoveOp() {
        return isValueMoveOp;
    }

    final boolean isLoadConstantOp() {
        return isLoadConstantOp;
    }
}
