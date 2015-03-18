/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.debug.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;

public abstract class HotSpotCounterOp extends LIRInstruction {
    public static final LIRInstructionClass<HotSpotCounterOp> TYPE = LIRInstructionClass.create(HotSpotCounterOp.class);

    private final String[] names;
    private final String[] groups;
    protected final Register thread;
    protected final HotSpotVMConfig config;
    @Alive({OperandFlag.CONST, OperandFlag.REG}) protected Value[] increments;

    public HotSpotCounterOp(LIRInstructionClass<? extends HotSpotCounterOp> c, String name, String group, Value increment, HotSpotRegistersProvider registers, HotSpotVMConfig config) {
        this(c, new String[]{name}, new String[]{group}, new Value[]{increment}, registers, config);
    }

    public HotSpotCounterOp(LIRInstructionClass<? extends HotSpotCounterOp> c, String[] names, String[] groups, Value[] increments, HotSpotRegistersProvider registers, HotSpotVMConfig config) {
        super(c);

        assert names.length == groups.length;
        assert groups.length == increments.length;

        this.names = names;
        this.groups = groups;
        this.increments = increments;
        this.thread = registers.getThreadRegister();
        this.config = config;
    }

    protected static int getDisplacementForLongIndex(TargetDescription target, long index) {
        long finalDisp = index * target.getSizeInBytes(Kind.Long);
        if (!NumUtil.isInt(finalDisp)) {
            throw GraalInternalError.unimplemented("cannot deal with indices that big: " + index);
        }
        return (int) finalDisp;
    }

    protected interface CounterProcedure {
        void apply(String name, String group, Value increment);
    }

    protected void forEachCounter(CounterProcedure proc) {
        for (int i = 0; i < names.length; i++) {
            proc.apply(names[i], groups[i], increments[i]);
        }
    }

    protected int getIndex(String name, String group, Value increment) {
        if (isConstant(increment)) {
            // get index for the counter
            return BenchmarkCounters.getIndexConstantIncrement(name, group, config, asLong(asConstant(increment)));
        }
        assert isRegister(increment) : "Unexpected Value: " + increment;
        // get index for the counter
        return BenchmarkCounters.getIndex(name, group, config);
    }

    private static long asLong(JavaConstant value) {
        Kind kind = value.getKind();
        switch (kind) {
            case Byte:
            case Short:
            case Char:
            case Int:
                return value.asInt();
            case Long:
                return value.asLong();
            default:
                throw new IllegalArgumentException("not an integer kind: " + kind);
        }
    }

    protected static int asInt(JavaConstant value) {
        long l = asLong(value);
        if (!NumUtil.isInt(l)) {
            throw GraalInternalError.shouldNotReachHere("value does not fit into int: " + l);
        }
        return (int) l;
    }

}
