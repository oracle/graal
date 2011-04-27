/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code StoreIndexed} instruction represents a write to an array element.
 *
 * @author Ben L. Titzer
 */
public final class StoreIndexed extends AccessIndexed {

    /**
     * The value to store.
     */
    Value value;

    /**
     * Creates a new StoreIndexed instruction.
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length
     * @param elementType the element type
     * @param value the value to store into the array
     * @param stateBefore the state before executing this instruction
     */
    public StoreIndexed(Value array, Value index, Value length, CiKind elementType, Value value, FrameState stateBefore) {
        super(CiKind.Void, array, index, length, elementType, stateBefore);
        this.value = value;
        setFlag(Flag.LiveSideEffect);
    }

    /**
     * Gets the instruction that produces the value that is to be stored into the array.
     * @return the value to write into the array
     */
    public Value value() {
        return value;
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        value = closure.apply(value);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitStoreIndexed(this);
    }

    @Override
    public void print(LogStream out) {
        out.print(array()).print('[').print(index()).print("] := ").print(value()).print(" (").print(kind.typeChar).print(')');
    }
}
