/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.snippets.*;
import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;

/**
 * A special purpose store node that differs from {@link CompareAndSwapNode} in that
 * it is not a {@link StateSplit} and it {@linkplain #compareAndSwap(Object, long, Word, Word) returns}
 * either the expected value or the compared against value instead of a boolean.
 */
public class DirectCompareAndSwapNode extends FixedWithNextNode implements LIRGenLowerable, MemoryCheckpoint {

    @Input private ValueNode object;
    @Input private ValueNode offset;
    @Input private ValueNode expectedValue;
    @Input private ValueNode newValue;

    public DirectCompareAndSwapNode(ValueNode object, ValueNode offset, ValueNode expected, ValueNode newValue) {
        super(expected.stamp());
        this.object = object;
        this.offset = offset;
        this.expectedValue = expected;
        this.newValue = newValue;
    }


    @Override
    public void generate(LIRGenerator gen) {
        Kind kind = newValue.kind();
        assert kind == expectedValue.kind();

        Value expected = gen.loadNonConst(gen.operand(expectedValue));
        Variable newVal = gen.load(gen.operand(newValue));

        int disp = 0;
        Address address;
        Value index = gen.operand(this.offset);
        if (ValueUtil.isConstant(index) && NumUtil.isInt(ValueUtil.asConstant(index).asLong() + disp)) {
            disp += (int) ValueUtil.asConstant(index).asLong();
            address = new Address(kind, gen.load(gen.operand(this.object)), disp);
        } else {
            address = new Address(kind, gen.load(gen.operand(this.object)), gen.load(index), Address.Scale.Times1, disp);
        }

        RegisterValue rax = AMD64.rax.asValue(kind);
        gen.emitMove(expected, rax);
        gen.append(new CompareAndSwapOp(rax, address, rax, newVal));

        Variable result = gen.newVariable(kind());
        gen.emitMove(rax, result);
        gen.setResult(this, result);
    }

    /**
     * Compares an expected value with the actual value in a location denoted by an object and a given offset.
     * Iff they are same, {@code newValue} is placed into the location and the {@code expectedValue} is returned.
     * Otherwise, the actual value is returned.
     * All of the above is performed in one atomic hardware transaction.
     *
     * @param object the object containing a field to be atomically tested and updated
     * @param offset offset from {@code object} of the field
     * @param expectedValue if this value is currently in the field, perform the swap
     * @param newValue the new value to put into the field
     * @return either {@code expectedValue} or the actual value
     */
    @NodeIntrinsic
    public static native Word compareAndSwap(Object object, long offset, Word expectedValue, Word newValue);
}
