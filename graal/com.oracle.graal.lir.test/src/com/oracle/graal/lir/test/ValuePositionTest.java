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

import java.util.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.ValuePositionProcedure;
import com.oracle.graal.lir.asm.*;

public class ValuePositionTest {

    private static class NestedCompositeValue extends CompositeValue {

        private static final long serialVersionUID = -8804214200173503527L;
        @Component({REG, OperandFlag.ILLEGAL}) protected Value value;

        public NestedCompositeValue(LIRKind kind, Value value) {
            super(kind);
            this.value = value;
        }

    }

    private static class DummyValue extends Value {

        private static final long serialVersionUID = -645435039553382737L;
        private final int id;

        protected DummyValue(LIRKind lirKind, int id) {
            super(lirKind);
            this.id = id;
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

    private static LIRInstruction createNestedOp(LIRKind kind, Value value, int nestingLevel) {
        NestedCompositeValue compValue = new NestedCompositeValue(kind, value);
        for (int i = 0; i < nestingLevel; i++) {
            compValue = new NestedCompositeValue(kind, compValue);
        }
        TestOp op = new TestOp(compValue);
        return op;
    }

    @Test
    public void nestedTest0() {
        LIRKind kind = LIRKind.Illegal;
        DummyValue dummyValue = new DummyValue(kind, 0);
        LIRInstruction op = createNestedOp(kind, dummyValue, 0);

        List<ValuePosition> positions = new ArrayList<>();

        op.forEachInput(new ValuePositionProcedure() {

            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                positions.add(position);
            }
        });

        assertEquals(1, positions.size());
        assertEquals(dummyValue, positions.get(0).get(op));
    }

    @Test
    public void nestedTest1() {
        LIRKind kind = LIRKind.Illegal;
        DummyValue dummyValue = new DummyValue(kind, 0);
        LIRInstruction op = createNestedOp(kind, dummyValue, 1);

        List<ValuePosition> positions = new ArrayList<>();

        op.forEachInput(new ValuePositionProcedure() {

            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                positions.add(position);
            }
        });

        assertEquals(1, positions.size());
        assertEquals(dummyValue, positions.get(0).get(op));
    }

    @Test
    public void nestedTest2() {
        LIRKind kind = LIRKind.Illegal;
        DummyValue dummyValue = new DummyValue(kind, 0);
        LIRInstruction op = createNestedOp(kind, dummyValue, 2);

        List<ValuePosition> positions = new ArrayList<>();

        op.forEachInput(new ValuePositionProcedure() {

            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                positions.add(position);
            }
        });

        assertEquals(1, positions.size());
        assertEquals(dummyValue, positions.get(0).get(op));
    }

}
