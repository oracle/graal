/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.debug.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code UnsafeCast} instruction represents a {@link Bytecodes#UNSAFE_CAST}.
 *
 * @author Doug Simon
 */
public final class UnsafeCast extends Instruction {

    public final RiType toType;

    /**
     * The instruction that produced the value being unsafe cast.
     */
    private Value value;

    /**
     * Denotes if this is a redundant cast at the machine level. That is the source
     * and the destination kind are implemented by the same machine kind.
     */
    public final boolean redundant;

    /**
     * Creates a new UnsafeCast instruction.
     *
     * @param toType the the being cast to
     * @param value the value being cast
     */
    public UnsafeCast(RiType toType, Value value, boolean redundant) {
        super(toType.kind().stackKind());
        this.toType = toType;
        this.value = value;
        this.redundant = redundant;
    }

    /**
     * Gets the first non-redundant value derived from this value. If this
     * value is not {@linkplain #redundant}, then it is returned. Otherwise,
     * the first value found by following {@link #value()} that is not an
     * unsafe cast or is not redundant is returned.
     */
    public Value nonRedundantReplacement() {
        if (!redundant) {
            return this;
        }
        if (!(value instanceof UnsafeCast)) {
            return value;
        }
        return ((UnsafeCast) value).nonRedundantReplacement();
    }

    /**
     * Gets the instruction that produced the value being unsafe cast.
     */
    public Value value() {
        return value;
    }

    @Override
    public RiType declaredType() {
        return toType;
    }

    @Override
    public RiType exactType() {
        return declaredType().exactType();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitUnsafeCast(this);
    }

    /**
     * Iterates over the input values to this instruction.
     * @param closure the closure to apply
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        value = closure.apply(value);
    }

    @Override
    public void print(LogStream out) {
        out.print("unsafe_cast(").
        print(value).
        print(") ").
        print(CiUtil.toJavaName(toType));
    }
}
