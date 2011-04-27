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
import com.sun.cri.ri.*;

/**
 * The {@code StoreField} instruction represents a write to a static or instance field.
 *
 * @author Ben L. Titzer
 */
public final class StoreField extends AccessField {

    /**
     * The value to store.
     */
    Value value;

    /**
     * Creates a new LoadField instance.
     * @param object the receiver object
     * @param field the compiler interface field
     * @param value the instruction representing the value to store to the field
     * @param isStatic indicates if the field is static
     * @param stateBefore the state before the field access
     * @param isLoaded indicates if the class is loaded
     */
    public StoreField(Value object, RiField field, Value value, boolean isStatic, FrameState stateBefore, boolean isLoaded) {
        super(CiKind.Void, object, field, isStatic, stateBefore, isLoaded);
        this.value = value;
        setFlag(Flag.LiveStore);
        if (value.kind != CiKind.Object) {
            setFlag(Flag.NoWriteBarrier);
        }
    }

    /**
     * Gets the value that is written to the field.
     * @return the value
     */
    public Value value() {
        return value;
    }

    /**
     * Checks whether this instruction needs a write barrier.
     * @return {@code true} if this instruction needs a write barrier
     */
    public boolean needsWriteBarrier() {
        return !checkFlag(Flag.NoWriteBarrier);
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        super.inputValuesDo(closure);
        value = closure.apply(value);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitStoreField(this);
    }

    @Override
    public void print(LogStream out) {
        out.print(object()).
        print(".").
        print(field().name()).
        print(" := ").
        print(value()).
        print(" [type: ").print(CiUtil.format("%h.%n:%t", field(), false)).
        print(']');
    }
}
