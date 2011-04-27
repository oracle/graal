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
 * The {@code LoadIndexed} instruction represents a read from an element of an array.
 *
 * @author Ben L. Titzer
 */
public final class LoadIndexed extends AccessIndexed {

    /**
     * Creates a new LoadIndexed instruction.
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length
     * @param elementType the element type
     * @param stateBefore the state before executing this instruction
     */
    public LoadIndexed(Value array, Value index, Value length, CiKind elementType, FrameState stateBefore) {
        super(elementType.stackKind(), array, index, length, elementType, stateBefore);
    }

    /**
     * Gets the declared type of this instruction's result.
     * @return the declared type
     */
    @Override
    public RiType declaredType() {
        RiType arrayType = array().declaredType();
        if (arrayType == null) {
            return null;
        }
        return arrayType.componentType();
    }

    /**
     * Gets the exact type of this instruction's result.
     * @return the exact type
     */
    @Override
    public RiType exactType() {
        RiType declared = declaredType();
        return declared != null && declared.isResolved() ? declared.exactType() : null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLoadIndexed(this);
    }

    @Override
    public void print(LogStream out) {
        out.print(array()).print('[').print(index()).print("] (").print(kind.typeChar).print(')');
    }
}
