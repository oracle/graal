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
 * The {@code NewObjectArray} instruction represents an allocation of an object array.
 *
 * @author Ben L. Titzer
 */
public final class NewObjectArray extends NewArray {

    final RiType elementClass;

    /**
     * Constructs a new NewObjectArray instruction.
     * @param elementClass the class of elements in this array
     * @param length the instruction producing the length of the array
     * @param stateBefore the state before the allocation
     */
    public NewObjectArray(RiType elementClass, Value length, FrameState stateBefore) {
        super(length, stateBefore);
        this.elementClass = elementClass;
    }

    /**
     * Gets the type of the elements of the array.
     * @return the element type of the array
     */
    public RiType elementClass() {
        return elementClass;
    }

    @Override
    public RiType exactType() {
        return elementClass.arrayOf();
    }

    @Override
    public RiType declaredType() {
        return exactType();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitNewObjectArray(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("new object array [").print(length()).print("] ").print(CiUtil.toJavaName(elementClass()));
    }
}
