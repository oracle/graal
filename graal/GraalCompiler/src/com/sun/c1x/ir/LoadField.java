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
 * The {@code LoadField} instruction represents a read of a static or instance field.
 *
 * @author Ben L. Titzer
 */
public final class LoadField extends AccessField {

    /**
     * Creates a new LoadField instance.
     * @param object the receiver object
     * @param field the compiler interface field
     * @param isStatic indicates if the field is static
     * @param stateBefore the state before the field access
     * @param isLoaded indicates if the class is loaded
     */
    public LoadField(Value object, RiField field, boolean isStatic, FrameState stateBefore, boolean isLoaded) {
        super(field.kind().stackKind(), object, field, isStatic, stateBefore, isLoaded);
    }

    /**
     * Gets the declared type of the field being accessed.
     * @return the declared type of the field being accessed.
     */
    @Override
    public RiType declaredType() {
        return field().type();
    }

    /**
     * Gets the exact type of the field being accessed. If the field type is
     * a primitive array or an instance class and the class is loaded and final,
     * then the exact type is the same as the declared type. Otherwise it is {@code null}
     * @return the exact type of the field if known; {@code null} otherwise
     */
    @Override
    public RiType exactType() {
        RiType declaredType = declaredType();
        return declaredType.isResolved() ? declaredType.exactType() : null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLoadField(this);
    }

    /**
     * Gets a constant value to which this load can be reduced.
     *
     * @return {@code null} if this load cannot be reduced to a constant
     */
    public CiConstant constantValue() {
        if (isStatic()) {
            return field.constantValue(null);
        } else if (object().isConstant()) {
            CiConstant cons = field.constantValue(object().asConstant());
            if (cons != null) {
                return cons;
            }
            return cons;
        }
        return null;
    }

    @Override
    public void print(LogStream out) {
        out.print(object()).
        print(".").
        print(field.name()).
        print(" [field: ").
        print(CiUtil.format("%h.%n:%t", field, false)).
        print("]");
    }
}
