/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.test;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.asm.*;

/**
 * Same as {@link CompositeValueReplacementTest1} but with for {@link ValuePosition}s.
 *
 * @see CompositeValueReplacementTest1
 */
public class CompositeValueReplacementTest3 {

    private static class NestedCompositeValue extends CompositeValue {

        private static final long serialVersionUID = -8804214200173503527L;
        @Component({REG, OperandFlag.ILLEGAL}) protected Value value;

        public NestedCompositeValue(Value value) {
            super(LIRKind.Illegal);
            this.value = value;
        }

    }

    private static class DummyValue extends Value {

        private static final long serialVersionUID = -645435039553382737L;
        private final int id;
        private static int counter = 1;

        protected DummyValue() {
            super(LIRKind.Illegal);
            this.id = counter++;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + id;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!super.equals(obj)) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            DummyValue other = (DummyValue) obj;
            if (id != other.id) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "DummyValue [id=" + id + "]";
        }

    }

    private static class TestOp extends LIRInstruction {

        @Use({COMPOSITE}) protected NestedCompositeValue compValue;

        public TestOp(NestedCompositeValue compValue) {
            this.compValue = compValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            fail("should not reach!");
        }

    }

    private static NestedCompositeValue createNestedCompValue(Value value, int nestingLevel) {
        NestedCompositeValue compValue = new NestedCompositeValue(value);
        for (int i = 0; i < nestingLevel; i++) {
            compValue = new NestedCompositeValue(compValue);
        }
        return compValue;
    }

    @Test
    public void replaceCompValueTest0() {
        DummyValue dummyValue1 = new DummyValue();
        DummyValue dummyValue2 = new DummyValue();
        DummyValue dummyValue3 = new DummyValue();
        NestedCompositeValue compValue1 = createNestedCompValue(dummyValue1, 0);
        LIRInstruction op1 = new TestOp(compValue1);
        LIRInstruction op2 = new TestOp(compValue1);

        op1.forEachInputPos(new ValuePositionProcedure() {

            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue1, value);
                position.set(instruction, dummyValue2);
            }
        });

        op2.forEachInputPos(new ValuePositionProcedure() {
            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue1, value);
                position.set(instruction, dummyValue3);
            }
        });

        op1.forEachInputPos(new ValuePositionProcedure() {
            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue2, value);
            }
        });

        op2.forEachInputPos(new ValuePositionProcedure() {
            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue3, value);
            }
        });
    }

    @Test
    public void replaceCompValueTest1() {
        DummyValue dummyValue1 = new DummyValue();
        DummyValue dummyValue2 = new DummyValue();
        DummyValue dummyValue3 = new DummyValue();
        NestedCompositeValue compValue1 = createNestedCompValue(dummyValue1, 1);
        LIRInstruction op1 = new TestOp(compValue1);
        LIRInstruction op2 = new TestOp(compValue1);

        op1.forEachInputPos(new ValuePositionProcedure() {

            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue1, value);
                position.set(instruction, dummyValue2);
            }
        });

        op2.forEachInputPos(new ValuePositionProcedure() {
            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue1, value);
                position.set(instruction, dummyValue3);
            }
        });

        op1.forEachInputPos(new ValuePositionProcedure() {
            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue2, value);
            }
        });

        op2.forEachInputPos(new ValuePositionProcedure() {
            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                Value value = position.get(instruction);
                assertEquals(dummyValue3, value);
            }
        });
    }
}
