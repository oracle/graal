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
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.asm.*;

public class ValuePositionTest3 {

    public static final class TestAddressValue extends CompositeValue {

        private static final long serialVersionUID = -2679790860680123026L;

        @Component({REG, OperandFlag.ILLEGAL}) protected AllocatableValue base;
        @Component({REG, OperandFlag.ILLEGAL}) protected AllocatableValue index;

        public TestAddressValue(LIRKind kind, AllocatableValue base) {
            this(kind, base, Value.ILLEGAL);
        }

        public TestAddressValue(LIRKind kind, AllocatableValue base, AllocatableValue index) {
            super(kind);
            this.base = base;
            this.index = index;
        }
    }

    private static class DummyValue extends AllocatableValue {

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

        @Override
        public String toString() {
            return "DummyValue" + id;
        }

    }

    private static class TestOp extends LIRInstruction {

        @Use({COMPOSITE}) protected Value value;

        public TestOp(Value value) {
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            fail("should not reach!");
        }

        @Override
        public String toString() {
            return "TestOp [" + value + "]";
        }

    }

    @Test
    public void test0() {
        DummyValue dummyValue0 = new DummyValue();
        DummyValue dummyValue1 = new DummyValue();

        TestAddressValue compValue0 = new TestAddressValue(LIRKind.Illegal, dummyValue0, dummyValue1);

        LIRInstruction op = new TestOp(compValue0);

        HashMap<Value, EnumSet<OperandFlag>> positionMap = new HashMap<>();
        HashMap<Value, EnumSet<OperandFlag>> normalMap = new HashMap<>();

        op.forEachInputPos(new ValuePositionProcedure() {

            @Override
            public void doValue(LIRInstruction instruction, ValuePosition position) {
                positionMap.put(position.get(instruction), position.getFlags());
            }
        });
        op.visitEachInput(new InstructionValueConsumer() {

            @Override
            public void visitValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                normalMap.put(value, flags);
            }
        });

        assertEquals(normalMap.size(), positionMap.size());
        assertTrue(normalMap.keySet().containsAll(positionMap.keySet()));
        normalMap.keySet().forEach(key -> {
            EnumSet<OperandFlag> normal = normalMap.get(key);
            EnumSet<OperandFlag> position = positionMap.get(key);
            assertTrue(normal.containsAll(position));
            assertTrue(position.containsAll(normal));
        });
    }
}
