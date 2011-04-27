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

import java.lang.reflect.*;

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The base class of all instructions that access fields.
 */
public abstract class AccessField extends StateSplit {

    private Value object;
    protected final RiField field;

    /**
     * Constructs a new access field object.
     * @param kind the result kind of the access
     * @param object the instruction producing the receiver object
     * @param field the compiler interface representation of the field
     * @param isStatic indicates if the field is static
     * @param stateBefore the state before the field access
     * @param isLoaded indicates if the class is loaded
     */
    public AccessField(CiKind kind, Value object, RiField field, FrameState stateBefore) {
        super(kind, stateBefore);
        this.object = object;
        this.field = field;
        if (field.isResolved() && object.isNonNull()) {
            eliminateNullCheck();
        }
        assert object != null : "every field access must reference some object";
    }

    /**
     * Gets the instruction that produces the receiver object of this field access
     * (for instance field accesses).
     * @return the instruction that produces the receiver object
     */
    public Value object() {
        return object;
    }

    /**
     * Gets the compiler interface field for this field access.
     * @return the compiler interface field for this field access
     */
    public RiField field() {
        return field;
    }

    /**
     * Checks whether this field access is an access to a static field.
     * @return {@code true} if this field access is to a static field
     */
    public boolean isStatic() {
        return Modifier.isStatic(field.accessFlags());
    }

    /**
     * Checks whether the class of the field of this access is loaded.
     * @return {@code true} if the class is loaded
     */
    public boolean isLoaded() {
        return field.isResolved();
    }

    /**
     * Checks whether this field is declared volatile.
     * @return {@code true} if the field is resolved and declared volatile
     */
    public boolean isVolatile() {
        return isLoaded() && Modifier.isVolatile(field.accessFlags());
    }

    @Override
    public void runtimeCheckCleared() {
        if (isLoaded()) {
            clearState();
        }
    }

    /**
     * Checks whether this field access may cause a trap or an exception, which
     * is if it either requires a null check or needs patching.
     * @return {@code true} if this field access can cause a trap
     */
    @Override
    public boolean canTrap() {
        return !isLoaded() || needsNullCheck();
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        object = closure.apply(object);
    }
}
