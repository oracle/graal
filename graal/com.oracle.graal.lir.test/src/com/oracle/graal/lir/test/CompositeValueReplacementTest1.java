/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.jvmci.meta.LIRKind;
import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.AbstractValue;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.asm.*;

/**
 * This test verifies that {@link CompositeValue}s are immutable, i.e. that a write to a component
 * of a {@link CompositeValue} results in a new {@link CompositeValue}.
 */
public class CompositeValueReplacementTest1 {

    private static class TestCompositeValue extends CompositeValue {

        @Component({REG, OperandFlag.ILLEGAL}) protected Value value;

        public TestCompositeValue(Value value) {
            super(LIRKind.Illegal);
            this.value = value;
        }

        private static final EnumSet<OperandFlag> flags = EnumSet.of(OperandFlag.REG, OperandFlag.ILLEGAL);

        @Override
        public CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc) {
            Value newValue = proc.doValue(inst, value, mode, flags);
            if (!value.identityEquals(newValue)) {
                return new TestCompositeValue(newValue);
            }
            return this;
        }

        @Override
        protected void forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc) {
            proc.visitValue(inst, value, mode, flags);
        }
    }

    private static class DummyValue extends AbstractValue {

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

    private static final class TestOp extends LIRInstruction {
        public static final LIRInstructionClass<TestOp> TYPE = LIRInstructionClass.create(TestOp.class);

        @Use({COMPOSITE}) protected TestCompositeValue compValue;

        public TestOp(TestCompositeValue compValue) {
            super(TYPE);
            this.compValue = compValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            fail("should not reach!");
        }

    }

    @Test
    public void replaceCompValueTest0() {
        DummyValue dummyValue1 = new DummyValue();
        DummyValue dummyValue2 = new DummyValue();
        DummyValue dummyValue3 = new DummyValue();
        TestCompositeValue compValue1 = new TestCompositeValue(dummyValue1);
        LIRInstruction op1 = new TestOp(compValue1);
        LIRInstruction op2 = new TestOp(compValue1);

        op1.forEachInput((instruction, value, mode, flags) -> {
            assertEquals(dummyValue1, value);
            return dummyValue2;
        });

        op2.forEachInput((instruction, value, mode, flags) -> {
            assertEquals(dummyValue1, value);
            return dummyValue3;
        });

        op1.visitEachInput((instruction, value, mode, flags) -> assertEquals(dummyValue2, value));
        op2.visitEachInput((instruction, value, mode, flags) -> assertEquals(dummyValue3, value));
    }
}
