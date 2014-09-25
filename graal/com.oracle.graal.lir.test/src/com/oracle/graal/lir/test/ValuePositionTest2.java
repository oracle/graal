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
import com.oracle.graal.lir.asm.*;

public class ValuePositionTest2 {

    private static class NestedCompositeValue extends CompositeValue {

        private static final long serialVersionUID = -2243948303328857965L;
        @Component({REG, OperandFlag.ILLEGAL}) protected Value value1;
        @Component({REG, OperandFlag.ILLEGAL}) protected Value value2;

        public NestedCompositeValue(Value value1, Value value2) {
            super(LIRKind.Illegal);
            this.value1 = value1;
            this.value2 = value2;
        }

    }

    private static class DummyValue extends Value {

        private static final long serialVersionUID = 3620305384660607012L;
        private final int id;
        private static int counter = 0;

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

    @Test
    public void testSetGet() {
        DummyValue dummyValue0 = new DummyValue();
        DummyValue dummyValue1 = new DummyValue();
        DummyValue dummyValue2 = new DummyValue();
        DummyValue dummyValue3 = new DummyValue();

        NestedCompositeValue compValue0 = new NestedCompositeValue(dummyValue0, dummyValue1);
        NestedCompositeValue compValue1 = new NestedCompositeValue(compValue0, dummyValue2);
        NestedCompositeValue compValue2 = new NestedCompositeValue(dummyValue3, compValue1);

        LIRInstruction op = new TestOp(compValue2);
        List<ValuePosition> positions = new ArrayList<>();

        op.forEachInputPos(new ValuePositionProcedure() {

            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                positions.add(position);
            }
        });

        assertEquals(4, positions.size());

        // replace values
        List<Value> replValues = new ArrayList<>();
        for (ValuePosition pos : positions) {
            Value v = new DummyValue();
            replValues.add(v);
            pos.set(op, v);

        }

        // check replaced values
        Iterator<Value> it = replValues.iterator();
        for (ValuePosition pos : positions) {
            Value v = pos.get(op);
            assertEquals(it.next(), v);
        }
        assertFalse(it.hasNext());
    }

}
